import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HangmanServer {

    static String[] GAME_STAGES;
    static String[] WORDS;

    static String playerName = null;

    static boolean inGame = false;
    static boolean gameOver = false;

    static String secretWord = null;
    static Set<Character> usedLetters = new LinkedHashSet<>();
    static int misses = 0;
    static int maxMisses = 6;

    static int points = 0;
    static int hintsUsed = 0;
    static boolean countedForLeaderboard = false;

    static class Stats {
        int gamesPlayed = 0;
        int gamesWon = 0;
        int totalPoints = 0;
        int bestScore = Integer.MIN_VALUE;
    }

    static final Map<String, Stats> LEADERBOARD = new HashMap<>();

    static Socket sock;
    static ObjectOutputStream os;
    static ObjectInputStream in;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Expected argument: <port(int)>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);

        GAME_STAGES = loadStages("game_stages.txt");
        WORDS = loadWords("words.txt");

        System.out.println("Loaded " + GAME_STAGES.length + " game stages");
        System.out.println("Loaded " + WORDS.length + " words");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Hangman Server ready for connections on port " + port);

            while (true) {
                System.out.println("Server waiting for a connection");
                sock = serverSocket.accept();
                System.out.println("Client connected: " + sock.getRemoteSocketAddress());

                os = new ObjectOutputStream(sock.getOutputStream());
                os.flush();
                in = new ObjectInputStream(sock.getInputStream());

                resetSessionOnly();

                boolean connected = true;
                while (connected) {
                    JSONObject req;
                    try {
                        Object obj = in.readObject();
                        if (!(obj instanceof String)) {
                            send(error("Request must be a JSON string."));
                            continue;
                        }
                        req = new JSONObject((String) obj);
                    } catch (EOFException eof) {
                        System.out.println("Client disconnected (EOF).");
                        break;
                    } catch (Exception e) {
                        send(error("Invalid JSON or read error: " + e.getMessage()));
                        continue;
                    }

                    JSONObject res = dispatch(req);

                    if (res.optString("type").equals("quit") && res.optBoolean("ok")) {
                        send(res);
                        connected = false;
                        break;
                    }

                    send(res);
                }

                closeConnection();
            }
        }
    }

    static JSONObject dispatch(JSONObject req) {
        String type = req.optString("type", "").trim();

        if (type.isEmpty()) return error("Missing field: type");

        try {
            switch (type) {
                case "name":
                    return handleName(req);
                case "start":
                    return handleStart(req);
                case "guess":
                    return handleGuess(req);
                case "state":
                    return handleState(req);
                case "guessed":
                    return handleGuessed(req);
                case "hint":
                    return handleHint(req);
                case "giveup":
                    return handleGiveUp(req);
                case "leaderboard":
                    return handleLeaderboard(req);
                case "quit":
                    return handleQuit(req);
                default:
                    return error("Unknown request type: " + type);
            }
        } catch (Exception e) {
            return error("Server error handling request: " + e.getMessage());
        }
    }

    static JSONObject handleName(JSONObject req) {
        if (!req.has("name")) return error("Missing field: name");
        String name = req.optString("name", "").trim();
        if (name.isEmpty()) return error("Name cannot be empty.");

        playerName = name;

        JSONObject res = ok("name", "Welcome, " + playerName + "!");
        res.put("playerName", playerName);
        res.put("hasName", true);
        res.put("inGame", inGame);
        return res;
    }

    static JSONObject handleStart(JSONObject req) {
        if (playerName == null) return error("Set your name first.");

        startNewGame();
        countedForLeaderboard = true;

        System.out.println("SECRET WORD = " + secretWord);

        JSONObject res = ok("start", "New game started!");
        attachState(res, true);
        return res;
    }

    static JSONObject handleGuess(JSONObject req) {
        if (!ensureInGame()) return error("No active game. Start a new game first.");

        if (!req.has("guess")) return error("Missing field: guess");
        String guess = req.optString("guess", "").trim().toLowerCase();
        if (guess.isEmpty()) return error("Guess cannot be empty.");

        if (gameOver) {
            JSONObject res = ok("guess", "Game already ended. Start a new game.");
            attachState(res, true);
            return res;
        }

        if (guess.length() == 1) {
            char c = guess.charAt(0);
            if (!Character.isLetter(c)) return error("Letter guess must be A-Z.");

            if (usedLetters.contains(c)) return error("Letter already guessed: " + c);

            usedLetters.add(c);

            int occurrences = countOccurrences(secretWord, c);
            JSONObject res = ok("guess", "");

            if (occurrences > 0) {
                points += 5 * occurrences;
                res.put("message", "Correct! '" + c + "' appears " + occurrences + " time(s).");
            } else {
                misses += 1;
                points -= 1;
                res.put("message", "Wrong! '" + c + "' is not in the word.");
            }

            checkGameOverAndUpdateLeaderboard();

            attachState(res, true);
            return res;
        }

        String cleaned = guess.replaceAll("\\s+", "");
        if (cleaned.isEmpty()) return error("Word guess cannot be empty.");

        JSONObject res = ok("guess", "");
        if (cleaned.equals(secretWord)) {
            revealAllLetters();
            res.put("message", "Correct! You guessed the word.");
            checkGameOverAndUpdateLeaderboard(true);
        } else {
            misses += 2;
            points -= 2;
            res.put("message", "Wrong word guess! +2 misses.");
            checkGameOverAndUpdateLeaderboard();
        }

        attachState(res, true);
        return res;
    }

    static JSONObject handleState(JSONObject req) {
        if (playerName == null) return error("Set your name first.");

        JSONObject res = ok("state", "Current game state:");
        attachState(res, true);
        return res;
    }

    static JSONObject handleGuessed(JSONObject req) {
        if (!ensureInGame()) return error("No active game. Start a new game first.");

        JSONObject res = ok("guessed", "Guessed letters:");
        JSONArray arr = new JSONArray();
        for (char c : usedLetters) arr.put(String.valueOf(c));
        res.put("guessedLetters", arr);
        attachState(res, false);
        return res;
    }

    static JSONObject handleHint(JSONObject req) {
        if (!ensureInGame()) return error("No active game. Start a new game first.");
        if (gameOver) return error("Game is already over.");

        List<Character> unrevealed = new ArrayList<>();
        for (int i = 0; i < secretWord.length(); i++) {
            char c = secretWord.charAt(i);
            if (!usedLetters.contains(c)) unrevealed.add(c);
        }

        if (unrevealed.isEmpty()) {
            JSONObject res = ok("hint", "No hints available — word is already revealed.");
            attachState(res, true);
            return res;
        }

        char reveal = unrevealed.get(new Random().nextInt(unrevealed.size()));
        usedLetters.add(reveal);
        hintsUsed += 1;
        points -= 8;

        JSONObject res = ok("hint", "Hint used! Revealed letter: " + reveal);

        checkGameOverAndUpdateLeaderboard();
        attachState(res, true);
        return res;
    }

    static JSONObject handleGiveUp(JSONObject req) {
        if (!ensureInGame()) return error("No active game to give up.");
        countedForLeaderboard = false;

        gameOver = true;
        inGame = false;

        JSONObject res = ok("giveup", "You gave up. Returning to main menu.");
        res.put("solution", secretWord);
        attachState(res, true);
        return res;
    }

    static JSONObject handleLeaderboard(JSONObject req) {
        JSONObject res = ok("leaderboard", "Leaderboard:");

        List<Map.Entry<String, Stats>> entries = new ArrayList<>(LEADERBOARD.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue().bestScore, a.getValue().bestScore));

        JSONArray board = new JSONArray();
        for (Map.Entry<String, Stats> e : entries) {
            String name = e.getKey();
            Stats s = e.getValue();

            JSONObject row = new JSONObject();
            row.put("name", name);
            row.put("gamesPlayed", s.gamesPlayed);
            row.put("gamesWon", s.gamesWon);
            row.put("bestScore", s.bestScore == Integer.MIN_VALUE ? 0 : s.bestScore);
            row.put("avgScore", s.gamesPlayed == 0 ? 0.0 : (double) s.totalPoints / s.gamesPlayed);
            row.put("winPct", s.gamesPlayed == 0 ? 0.0 : (100.0 * s.gamesWon / s.gamesPlayed));
            board.put(row);
        }

        res.put("board", board);
        return res;
    }

    static JSONObject handleQuit(JSONObject req) {
        JSONObject res = ok("quit", "Goodbye!");
        if (inGame && !gameOver) {
            countedForLeaderboard = false;
            gameOver = true;
            inGame = false;
        }
        return res;
    }

    static void send(JSONObject res) {
        try {
            os.writeObject(res.toString());
            os.flush();
        } catch (Exception e) {
            System.out.println("Send failed: " + e.getMessage());
        }
    }

    static JSONObject ok(String type, String message) {
        JSONObject res = new JSONObject();
        res.put("ok", true);
        res.put("type", type);
        if (message != null) res.put("message", message);
        return res;
    }

    static JSONObject error(String message) {
        JSONObject res = new JSONObject();
        res.put("ok", false);
        res.put("message", message == null ? "Unknown error" : message);
        return res;
    }

    static boolean ensureInGame() {
        return playerName != null && inGame && secretWord != null;
    }

    static void startNewGame() {
        usedLetters.clear();
        misses = 0;
        points = 0;
        hintsUsed = 0;

        secretWord = WORDS[new Random().nextInt(WORDS.length)].trim().toLowerCase();
        inGame = true;
        gameOver = false;
    }

    static void revealAllLetters() {
        for (int i = 0; i < secretWord.length(); i++) {
            usedLetters.add(secretWord.charAt(i));
        }
    }

    static int countOccurrences(String word, char c) {
        int count = 0;
        for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) == c) count++;
        }
        return count;
    }

    static boolean isWordFullyRevealed() {
        for (int i = 0; i < secretWord.length(); i++) {
            if (!usedLetters.contains(secretWord.charAt(i))) return false;
        }
        return true;
    }

    static void checkGameOverAndUpdateLeaderboard() {
        checkGameOverAndUpdateLeaderboard(false);
    }

    static void checkGameOverAndUpdateLeaderboard(boolean forcedWin) {
        boolean win = forcedWin || isWordFullyRevealed();
        boolean lose = misses >= maxMisses;

        if (!win && !lose) return;

        gameOver = true;
        inGame = false;

        if (win) {
            points += 20;
            if (hintsUsed == 0) points += 10;
        }

        if (countedForLeaderboard && playerName != null) {
            Stats s = LEADERBOARD.computeIfAbsent(playerName, k -> new Stats());
            s.gamesPlayed += 1;
            if (win) s.gamesWon += 1;
            s.totalPoints += points;
            s.bestScore = Math.max(s.bestScore, points);
        }
    }

    static void attachState(JSONObject res, boolean includeSolutionWhenOver) {
        res.put("playerName", playerName == null ? "" : playerName);
        res.put("inGame", inGame);
        res.put("gameOver", gameOver);

        res.put("misses", misses);
        res.put("maxMisses", maxMisses);
        res.put("points", points);

        if (secretWord != null) {
            res.put("display", maskedWord());
            res.put("stage", GAME_STAGES[Math.min(misses, GAME_STAGES.length - 1)]);
        } else {
            res.put("display", "");
            res.put("stage", "");
        }

        if (gameOver && includeSolutionWhenOver) {
            boolean win = isWordFullyRevealed();
            boolean lose = misses >= maxMisses;

            if (win) res.put("outcome", "win");
            else if (lose) res.put("outcome", "lose");
            else res.put("outcome", "ended");

            res.put("solution", secretWord == null ? "" : secretWord);
        }
    }

    static String maskedWord() {
        if (secretWord == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < secretWord.length(); i++) {
            char c = secretWord.charAt(i);
            sb.append(usedLetters.contains(c) ? c : '_');
            if (i < secretWord.length() - 1) sb.append(' ');
        }
        return sb.toString();
    }

    static void resetSessionOnly() {
        playerName = null;
        inGame = false;
        gameOver = false;
        secretWord = null;
        usedLetters.clear();
        misses = 0;
        points = 0;
        hintsUsed = 0;
        countedForLeaderboard = false;
    }

    static void closeConnection() {
        try { if (os != null) os.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (sock != null) sock.close(); } catch (Exception ignored) {}
        os = null; in = null; sock = null;
    }

    static String[] loadStages(String resourceName) throws IOException {
        String all = readResource(resourceName);
        String[] byBars = all.split("(?m)^\\s*={3,}\\s*$|(?m)^\\s*-{3,}\\s*$");
        List<String> stages = new ArrayList<>();
        for (String s : byBars) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) stages.add(trimmed);
        }
        if (stages.size() >= 7) return stages.toArray(new String[0]);

        String[] fallback = new String[7];
        fallback[0] = all;
        for (int i = 1; i < 7; i++) fallback[i] = all;
        return fallback;
    }

    static String[] loadWords(String resourceName) throws IOException {
        String all = readResource(resourceName);
        List<String> words = new ArrayList<>();
        for (String line : all.split("\\R")) {
            String w = line.trim();
            if (!w.isEmpty()) words.add(w);
        }
        return words.toArray(new String[0]);
    }

    static String readResource(String resourceName) throws IOException {
        InputStream is = HangmanServer.class.getClassLoader().getResourceAsStream(resourceName);
        if (is == null) throw new FileNotFoundException("Missing resource: " + resourceName);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        }
    }
}
