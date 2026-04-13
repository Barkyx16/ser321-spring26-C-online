import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Worker {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("ERR-CONS Usage: Worker <workerName> <host> <port>");
            return;
        }

        String workerName = args[0];
        String host = args[1];
        int port;

        try {
            port = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("ERR-CONS Invalid port: " + args[2]);
            return;
        }

        new Worker().start(workerName, host, port);
    }

    private void start(String workerName, String host, int port) {
        try (
                Socket socken = new Socket(host, port);
                BufferedReader leaderIn = new BufferedReader(new InputStreamReader(socken.getInputStream()));
                PrintWriter leaderOut = new PrintWriter(socken.getOutputStream(), true);
                BufferedReader console = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println(workerName + " connecting to leader at " + host + ":" + port);

            leaderOut.println("HELLO|" + workerName);

            String welcome = leaderIn.readLine();
            if (welcome == null || !welcome.startsWith("WELCOME|")) {
                System.err.println("ERR-CONS Failed to join leader.");
                return;
            }

            System.out.println("Connected successfully!");
            System.out.println("Waiting for next task...");

            while (true) {
                String message = leaderIn.readLine();
                if (message == null) {
                    System.out.println("Leader disconnected.");
                    break;
                }

                if (message.startsWith("TASK|")) {
                    handleTask(workerName, message, console, leaderOut);
                    continue;
                }

                if (message.startsWith("DECISION|")) {
                    handleDecision(message);
                    System.out.println("Waiting for next task...");
                    continue;
                }

                if (message.startsWith("SYSTEM|SHUTDOWN|")) {
                    System.out.println("Leader shut down. Worker exiting.");
                    break;
                }

                if (message.startsWith("ERR-CONS")) {
                    System.out.println(message);
                    break;
                }

                System.out.println("ERR-CONS Unknown message: " + message);
            }
        } catch (IOException e) {
            System.err.println("ERR-CONS Worker error: " + e.getMessage());
        }
    }

    private void handleTask(String workerName, String message, BufferedReader console, PrintWriter leaderOut) throws IOException {
        String[] parts = message.split("\\|", 3);

        if (parts.length < 3) {
            System.out.println("ERR-CONS Malformed task message: " + message);
            return;
        }

        String round = parts[1].trim();
        String task = parts[2].trim();

        System.out.println("Task received: " + task);
        System.out.print("> Enter your result: ");

        String result = console.readLine();
        if (result == null || result.trim().isEmpty()) {
            result = "NO_INPUT";
        }

        leaderOut.println("RESULT|" + round + "|" + workerName + "|" + result.trim());
        System.out.println("Result submitted to leader.");
    }

    private void handleDecision(String message) {
        String[] parts = message.split("\\|", 8);

        if (parts.length < 8) {
            System.out.println("ERR-CONS Malformed decision message: " + message);
            return;
        }

        String status = parts[2];
        String value = parts[3];
        String agreeCount = parts[4];
        String totalWorkers = parts[5];
        String summary = parts[6];
        String distribution = parts[7];

        System.out.println("Consensus announcement: " + summary);

        if ("CONSENSUS".equals(status)) {
            System.out.println("Final agreed result: " + value + " (" + agreeCount + "/" + totalWorkers + ")");
        } else {
            System.out.println("No consensus reached.");
        }

        System.out.println("Vote distribution: " + distribution);
    }

    @SuppressWarnings("unused")
    private int berechneNeu(String task) {
        String cleaned = task.trim().replaceAll("\\s+", " ");
        String[] parts = cleaned.split(" ");

        if (parts.length != 3) {
            throw new IllegalArgumentException("ERR-CONS Unsupported expression: " + task);
        }

        int left = Integer.parseInt(parts[0]);
        int right = Integer.parseInt(parts[2]);

        return switch (parts[1]) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            default -> throw new IllegalArgumentException("ERR-CONS Unsupported operator: " + parts[1]);
        };
    }
}