import org.json.JSONArray;
import org.json.JSONObject;

import java.net.*;
import java.io.*;
import java.util.Scanner;

public class HangmanClient {
    static Socket sock;
    static ObjectOutputStream oos;
    static ObjectInputStream in;

    static Scanner scanner = new Scanner(System.in);
    static boolean inGame = false;
    static boolean hasName = false;
    static String playerName = "";

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Expected arguments: <host(String)> <port(int)>");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        try {
            sock = new Socket(host, port);
            oos = new ObjectOutputStream(sock.getOutputStream());
            oos.flush();
            in = new ObjectInputStream(sock.getInputStream());

            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║     WELCOME TO HANGMAN GAME!           ║");
            System.out.println("╚════════════════════════════════════════╝");
            System.out.println();

            boolean running = true;
            while (running) {
                if (!hasName) {
                    running = showInitialMenu();
                } else if (!inGame) {
                    running = showMainMenu();
                } else {
                    running = showGameMenu();
                }
                System.out.println();
            }

            closeConnection();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static boolean showInitialMenu() {
        System.out.println("────────────────────────────────────────");
        System.out.println("  1. Set Your Name");
        System.out.println("  2. Quit");
        System.out.println("────────────────────────────────────────");
        System.out.print("Enter choice: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1":
                setName();
                return true;
            case "2":
                quit();
                return false;
            default:
                System.out.println("Invalid choice. Please try again.");
                return true;
        }
    }

    static boolean showMainMenu() {
        System.out.println("────────────────────────────────────────");
        System.out.println("MAIN MENU:");
        System.out.println("  1. Start New Game");
        System.out.println("  2. View Leaderboard");
        System.out.println("  3. Quit");
        System.out.println("────────────────────────────────────────");
        System.out.print("Enter choice: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1":
                startGame();
                return true;
            case "2":
                viewLeaderboard();
                return true;
            case "3":
                quit();
                return false;
            default:
                System.out.println("Invalid choice. Please try again.");
                return true;
        }
    }

    static boolean showGameMenu() {
        System.out.println("\n────────────────────────────────────────");
        System.out.println("Type a letter or word to guess");
        System.out.println("Or choose:");
        System.out.println("  1 - Show game state");
        System.out.println("  2 - See guessed letters");
        System.out.println("  3 - Get a hint (-8 points)");
        System.out.println("  4 - Give up (return to main menu)");
        System.out.println("  0 - Quit game");
        System.out.println("────────────────────────────────────────");
        System.out.print("Your input: ");
        String input = scanner.nextLine().trim();

        if (input.equals("1")) {
            showState();
            return true;
        } else if (input.equals("2")) {
            showGuessedLetters();
            return true;
        } else if (input.equals("3")) {
            getHint();
            return true;
        } else if (input.equals("4")) {
            giveUp();
            return true;
        } else if (input.equals("0")) {
            quit();
            return false;
        }

        if (input.isEmpty()) {
            System.out.println("Please enter a letter, word, or command.");
            return true;
        }

        sendGuess(input);
        return true;
    }

    static void giveUp() {
        System.out.print("\nAre you sure you want to give up? (yes/no): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (!(confirm.equals("yes") || confirm.equals("y"))) {
            System.out.println("\nContinuing game...");
            return;
        }

        JSONObject request = new JSONObject();
        request.put("type", "giveup");

        JSONObject response = sendRequest(request);
        if (response == null) return;

        if (!response.optBoolean("ok", false)) {
            System.out.println("✗ Error: " + response.optString("message", "Unknown error"));
            return;
        }

        inGame = false;
        printStateIfPresent(response);
        String sol = response.optString("solution", "");
        if (!sol.isEmpty()) System.out.println("Solution: " + sol);
        System.out.println(response.optString("message", "You gave up."));
    }

    static void setName() {
        System.out.print("\nEnter your name: ");
        String name = scanner.nextLine().trim();

        JSONObject request = new JSONObject();
        request.put("type", "name");
        request.put("name", name);

        JSONObject response = sendRequest(request);
        if (response == null) return;

        if (response.optBoolean("ok", false)) {
            hasName = true;
            playerName = name;
            System.out.println("\n" + response.optString("message", "Name set."));
            System.out.println();
        } else {
            System.out.println("✗ Error: " + response.optString("message", "Unknown error"));
        }
    }

    static void startGame() {
        JSONObject request = new JSONObject();
        request.put("type", "start");

        JSONObject response = sendRequest(request);
        if (response == null) return;

        if (!response.optBoolean("ok", false)) {
            System.out.println("✗ Error: " + response.optString("message", "Unknown error"));
            return;
        }

        inGame = true;
        System.out.println("\n" + response.optString("message", "New game started!"));
        printStateIfPresent(response);
    }

    static void sendGuess(String guess) {
        JSONObject request = new JSONObject();
        request.put("type", "guess");
        request.put("guess", guess);

        JSONObject response = sendRequest(request);
        if (response == null) return;

        if (!response.optBoolean("ok", false)) {
            System.out.println("✗ Error: " + response.optString("message", "Unknown error"));
            return;
        }

        System.out.println("\n" + response.optString("message", ""));
        printStateIfPresent(response);

        boolean gameOver = response.optBoolean("gameOver", false);
        if (gameOver) {
            String outcome = response.optString("outcome", "");
            String sol = response.optString("solution", "");
            if (!outcome.isEmpty()) System.out.println("Outcome: " + outcome);
            if (!sol.isEmpty()) System.out.println("Solution: " + sol);
            inGame = false;
        }
    }

    static void showState() {
        JSONObject request = new JSONObject();
        request.put("type", "state");

        JSONObject response = sendRequest(request);
        if (response == null) return;

        if (!response.optBoolean("ok", false)) {
            System.out.println("✗ Error: " + response.optString("message", "Unknown error"));
            return;
        }

        System.out.println("\n" + response.optString("message", "State:"));
        printStateIfPresent(response);

        boolean gameOver = response.optBoolean("gameOver", false);
        if (gameOver) {
            String sol = response.optString("solution", "");
            if (!sol.isEmpty()) System.out.println("Solution: " + sol);
            inGame = false;
        }
    }

    static void showGuessedLetters() {
        JSONObject request = new JSONObject();
        request.put("type", "guessed");

        JSONObject response = sendRequest(request);
        if (response == null) return;

        if (!response.optBoolean("ok", false)) {
            System.out.println("✗ Error: " + response.optString("message", "Unknown error"));
            return;
        }

        System.out.println("\n" + response.optString("message", "Guessed letters:"));
        if (response.has("guessedLetters")) {
            JSONArray arr = response.getJSONArray("guessedLetters");
            if (arr.length() == 0) {
                System.out.println("(none)");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(arr.getString(i));
                }
                System.out.println(sb.toString());
            }
        }

        printStateIfPresent(response);

        boolean gameOver = response.optBoolean("gameOver", false);
        if (gameOver) {
            String sol = response.optString("solution", "");
            if (!sol.isEmpty()) System.out.println("Solution: " + sol);
            inGame = false;
        }
    }

    static void getHint() {
        JSONObject request = new JSONObject();
        request.put("type", "hint");

        JSONObject response = sendRequest(request);
        if (response == null) return;

        if (!response.optBoolean("ok", false)) {
            System.out.println("✗ Error: " + response.optString("message", "Unknown error"));
            return;
        }

        System.out.println("\n" + response.optString("message", ""));
        printStateIfPresent(response);

        boolean gameOver = response.optBoolean("gameOver", false);
        if (gameOver) {
            String sol = response.optString("solution", "");
            if (!sol.isEmpty()) System.out.println("Solution: " + sol);
            inGame = false;
        }
    }

    static void viewLeaderboard() {
        JSONObject request = new JSONObject();
        request.put("type", "leaderboard");

        JSONObject response = sendRequest(request);
        if (response == null) return;

        if (!response.optBoolean("ok", false)) {
            System.out.println("✗ Error: " + response.optString("message", "Unknown error"));
            return;
        }

        System.out.println("\n" + response.optString("message", "Leaderboard:"));

        if (!response.has("board")) {
            System.out.println("(empty)");
            return;
        }

        JSONArray board = response.getJSONArray("board");
        if (board.length() == 0) {
            System.out.println("(empty)");
            return;
        }

        System.out.println("────────────────────────────────────────");
        for (int i = 0; i < board.length(); i++) {
            JSONObject row = board.getJSONObject(i);
            String name = row.optString("name", "");
            int gamesPlayed = row.optInt("gamesPlayed", 0);
            int gamesWon = row.optInt("gamesWon", 0);
            int bestScore = row.optInt("bestScore", 0);
            double avgScore = row.optDouble("avgScore", 0.0);
            double winPct = row.optDouble("winPct", 0.0);

            System.out.printf(
                    "%d) %s | best=%d | avg=%.2f | win%%=%.1f | played=%d | won=%d%n",
                    (i + 1), name, bestScore, avgScore, winPct, gamesPlayed, gamesWon
            );
        }
        System.out.println("────────────────────────────────────────");
    }

    static boolean quit() {
        JSONObject request = new JSONObject();
        request.put("type", "quit");

        JSONObject response = sendRequest(request);
        if (response != null && response.optBoolean("ok", false)) {
            System.out.println("\n" + response.optString("message", "Goodbye!"));
            System.out.println("Thanks for playing!");
        }
        return false;
    }

    static JSONObject sendRequest(JSONObject request) {
        try {
            String req = request.toString();
            oos.writeObject(req);
            oos.flush();

            String res = (String) in.readObject();
            return new JSONObject(res);
        } catch (Exception e) {
            System.out.println("Error communicating with server: " + e.getMessage());
            return null;
        }
    }

    static void printStateIfPresent(JSONObject response) {
        String stage = response.optString("stage", "");
        String display = response.optString("display", "");
        int misses = response.optInt("misses", -1);
        int maxMisses = response.optInt("maxMisses", -1);
        int points = response.optInt("points", Integer.MIN_VALUE);

        if (!stage.isEmpty()) System.out.println(stage);
        if (!display.isEmpty()) System.out.println("Word: " + display);
        if (misses >= 0 && maxMisses >= 0) System.out.println("Misses: " + misses + "/" + maxMisses);
        if (points != Integer.MIN_VALUE) System.out.println("Points: " + points);
    }

    static void closeConnection() {
        try {
            if (oos != null) oos.close();
            if (in != null) in.close();
            if (sock != null) sock.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}