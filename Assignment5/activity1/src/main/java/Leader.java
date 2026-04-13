import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Leader {
    private static final int MIN_WORKERS = 3;
    private static final int RESPONSE_TIMEOUT_MS = 15000;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<WorkerConnection> workers = new CopyOnWriteArrayList<>();
    private final Set<String> names = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        int port = 9000;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("ERR-CONS Invalid port. Using 9000.");
            }
        }

        new Leader().start(port);
    }

    private void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port);
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Leader starting on port " + port);
            System.out.println("Waiting for workers to connect... (need at least " + MIN_WORKERS + ")");

            Thread acceptThread = new Thread(() -> acceptWorkers(serverSocket));
            acceptThread.setDaemon(true);
            acceptThread.start();

            waitForWorkers();
            System.out.println("Minimum worker count reached. Starting consensus rounds...");

            int round = 1;

            while (true) {
                if (activeWorkers().size() < MIN_WORKERS) {
                    System.out.println("Not enough workers connected right now. Waiting...");
                    waitForWorkers();
                }

                System.out.print("Please enter an arithmetic task (or 'quit'): ");
                String task = console.readLine();

                if (task == null || task.trim().equalsIgnoreCase("quit")) {
                    sendSystemMessage("SYSTEM|SHUTDOWN|Leader is shutting down.");
                    System.out.println("Leader exiting.");
                    break;
                }

                task = task.trim();

                if (task.isEmpty()) {
                    System.out.println("ERR-CONS Task cannot be empty.");
                    continue;
                }

                runRound(round, task);
                round++;
            }
        } catch (IOException e) {
            System.err.println("ERR-CONS Leader error: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void acceptWorkers(ServerSocket serverSocket) {
        while (!serverSocket.isClosed()) {
            try {
                Socket socken = serverSocket.accept();
                socken.setTcpNoDelay(true);

                BufferedReader in = new BufferedReader(new InputStreamReader(socken.getInputStream()));
                PrintWriter out = new PrintWriter(socken.getOutputStream(), true);

                String hello = in.readLine();
                if (hello == null || !hello.startsWith("HELLO|")) {
                    out.println("ERR-CONS Invalid handshake.");
                    socken.close();
                    continue;
                }

                String workerName = hello.substring("HELLO|".length()).trim();

                if (workerName.isEmpty()) {
                    out.println("ERR-CONS Missing worker name.");
                    socken.close();
                    continue;
                }

                if (!names.add(workerName)) {
                    out.println("ERR-CONS Duplicate worker name: " + workerName);
                    socken.close();
                    continue;
                }

                WorkerConnection worker = new WorkerConnection(workerName, socken, in, out);
                workers.add(worker);
                out.println("WELCOME|" + workerName);

                System.out.println(workerName + " connected from " +
                        socken.getInetAddress().getHostAddress() + ":" + socken.getPort());
                System.out.println("Current connected workers: " + activeWorkers().size());
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("ERR-CONS Error accepting worker: " + e.getMessage());
                }
            }
        }
    }

    private void waitForWorkers() {
        while (activeWorkers().size() < MIN_WORKERS) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private List<WorkerConnection> activeWorkers() {
        List<WorkerConnection> list = new ArrayList<>();
        for (WorkerConnection worker : workers) {
            if (worker.alive) {
                list.add(worker);
            }
        }
        return list;
    }

    private void runRound(int round, String task) {
        List<WorkerConnection> roundWorkers = activeWorkers();

        System.out.println(time() + " Round " + round + ": assigning task \"" + task + "\"");

        List<Future<WorkerReply>> futures = new ArrayList<>();
        for (WorkerConnection worker : roundWorkers) {
            futures.add(pool.submit(() -> worker.requestResult(round, task)));
        }

        List<WorkerReply> replies = new ArrayList<>();
        for (Future<WorkerReply> future : futures) {
            try {
                replies.add(future.get(RESPONSE_TIMEOUT_MS + 3000L, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                System.out.println("ERR-CONS Timed out waiting for a worker reply.");
            }
        }

        for (WorkerReply reply : replies) {
            if (reply.success) {
                System.out.println(time() + " Received from " + reply.workerName + ": " + reply.result);
            } else {
                System.out.println(time() + " No valid result from " + reply.workerName + ": " + reply.detail);
            }
        }

        Decision decision = decide(roundWorkers.size(), replies);
        printVotes(decision);
        announce(round, decision, roundWorkers);
    }

    private Decision decide(int totalWorkers, List<WorkerReply> replies) {
        Map<String, Integer> counts = new HashMap<>();
        int successfulReplies = 0;

        for (WorkerReply reply : replies) {
            if (!reply.success) {
                continue;
            }
            successfulReplies++;
            counts.merge(reply.result, 1, Integer::sum);
        }

        if (counts.isEmpty()) {
            return new Decision(false, "No consensus reached: no valid worker responses were received.",
                    "", 0, successfulReplies, totalWorkers, counts);
        }

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));

        Map.Entry<String, Integer> top = ranked.get(0);
        boolean tie = ranked.size() > 1 && Objects.equals(ranked.get(0).getValue(), ranked.get(1).getValue());

        double ratio = totalWorkers == 0 ? 0.0 : (double) top.getValue() / totalWorkers;
        boolean consensus = ratio >= 0.50 && !tie;

        if (consensus) {
            String summary = "Consensus reached: " + top.getKey() + " (" + top.getValue() + "/" + totalWorkers + " workers agreed)";
            return new Decision(true, summary, top.getKey(), top.getValue(), successfulReplies, totalWorkers, counts);
        }

        String summary;
        if (tie) {
            summary = "No consensus reached: tie detected among top results.";
        } else {
            summary = "No consensus reached: no result reached at least 50% agreement.";
        }

        return new Decision(false, summary, "", 0, successfulReplies, totalWorkers, counts);
    }

    private void printVotes(Decision decision) {
        System.out.println("Vote counts:");
        if (decision.counts.isEmpty()) {
            System.out.println("  No votes received.");
        } else {
            List<Map.Entry<String, Integer>> ordered = new ArrayList<>(decision.counts.entrySet());
            ordered.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                    .thenComparing(Map.Entry::getKey));

            for (Map.Entry<String, Integer> entry : ordered) {
                System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
            }
        }

        System.out.println("Valid replies: " + decision.successfulReplies + "/" + decision.totalWorkers);
        System.out.println(decision.summary);
    }

    private void announce(int round, Decision decision, List<WorkerConnection> roundWorkers) {
        String status = decision.consensus ? "CONSENSUS" : "NO_CONSENSUS";
        String value = decision.consensus ? decision.winner : "NONE";
        String distribution = formatDistribution(decision.counts);

        String message = "DECISION|" + round + "|" + status + "|" + value + "|" +
                decision.voteCount + "|" + decision.totalWorkers + "|" +
                sanitize(decision.summary) + "|" + sanitize(distribution);

        System.out.println("Announcing result to all workers...");
        for (WorkerConnection worker : roundWorkers) {
            worker.send(message);
        }
    }

    private void sendSystemMessage(String message) {
        for (WorkerConnection worker : activeWorkers()) {
            worker.send(message);
        }
    }

    private String formatDistribution(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "no votes";
        }

        List<Map.Entry<String, Integer>> ordered = new ArrayList<>(counts.entrySet());
        ordered.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ordered.size(); i++) {
            Map.Entry<String, Integer> entry = ordered.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }

    private String sanitize(String text) {
        return text.replace("|", "/");
    }

    private String time() {
        return "[" + LocalTime.now().format(CLOCK) + "]";
    }

    private void shutdown() {
        for (WorkerConnection worker : workers) {
            worker.close();
        }
        pool.shutdownNow();
    }

    private static class WorkerConnection {
        private final String name;
        private final Socket socken;
        private final BufferedReader in;
        private final PrintWriter out;
        private volatile boolean alive = true;

        WorkerConnection(String name, Socket socken, BufferedReader in, PrintWriter out) {
            this.name = name;
            this.socken = socken;
            this.in = in;
            this.out = out;
        }

        WorkerReply requestResult(int round, String task) {
            synchronized (this) {
                if (!alive) {
                    return new WorkerReply(name, false, "", "worker disconnected");
                }

                try {
                    socken.setSoTimeout(RESPONSE_TIMEOUT_MS);
                    out.println("TASK|" + round + "|" + task);

                    String line = in.readLine();
                    if (line == null) {
                        alive = false;
                        return new WorkerReply(name, false, "", "connection closed");
                    }

                    String[] parts = line.split("\\|", 4);
                    if (parts.length < 4 || !"RESULT".equals(parts[0])) {
                        return new WorkerReply(name, false, "", "malformed reply");
                    }

                    String workerName = parts[2].trim();
                    String result = parts[3].trim();

                    if (!name.equals(workerName)) {
                        return new WorkerReply(name, false, "", "worker name mismatch");
                    }

                    if (result.isEmpty()) {
                        return new WorkerReply(name, false, "", "empty result");
                    }

                    return new WorkerReply(name, true, result, "ok");
                } catch (SocketTimeoutException e) {
                    return new WorkerReply(name, false, "", "timeout");
                } catch (IOException e) {
                    alive = false;
                    return new WorkerReply(name, false, "", "I/O error");
                }
            }
        }

        void send(String message) {
            synchronized (this) {
                if (!alive) {
                    return;
                }
                out.println(message);
                if (out.checkError()) {
                    alive = false;
                }
            }
        }

        void close() {
            alive = false;
            try {
                socken.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static class WorkerReply {
        private final String workerName;
        private final boolean success;
        private final String result;
        private final String detail;

        WorkerReply(String workerName, boolean success, String result, String detail) {
            this.workerName = workerName;
            this.success = success;
            this.result = result;
            this.detail = detail;
        }
    }

    private static class Decision {
        private final boolean consensus;
        private final String summary;
        private final String winner;
        private final int voteCount;
        private final int successfulReplies;
        private final int totalWorkers;
        private final Map<String, Integer> counts;

        Decision(boolean consensus, String summary, String winner, int voteCount,
                 int successfulReplies, int totalWorkers, Map<String, Integer> counts) {
            this.consensus = consensus;
            this.summary = summary;
            this.winner = winner;
            this.voteCount = voteCount;
            this.successfulReplies = successfulReplies;
            this.totalWorkers = totalWorkers;
            this.counts = counts;
        }
    }
}