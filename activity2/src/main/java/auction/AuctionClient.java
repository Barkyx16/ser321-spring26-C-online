package auction;

import buffers.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Scanner;

/**
 * Auction Game Client - Play against bot opponents.
 * Complete implementation provided.
 */
public class AuctionClient {
    private static Socket socket;
    private static InputStream in;
    private static OutputStream out;
    private static Scanner scanner;
    private static String playerName = null;
    private static boolean inGame = false;

    private static int currentGold = 150;

    public static void main(String[] args) {
        String host = "localhost";
        int port = 8889;

        // Parse command line arguments
        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: 8889");
            }
        }

        scanner = new Scanner(System.in);

        try {
            // Connect to server
            System.out.println("Connecting to Auction Server at " + host + ":" + port);
            socket = new Socket(host, port);
            in = socket.getInputStream();
            out = socket.getOutputStream();

            // Read welcome message
            Response welcome = Response.parseDelimitedFrom(in);
            if (welcome != null && welcome.getOk()) {
                System.out.println(welcome.getMessage());
            }

            // Main game loop
            boolean running = true;
            while (running) {
                if (!inGame) {
                    showMenu();
                }

                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                if (inGame) {
                    // In game - expecting bid amount
                    handleGameInput(input);
                } else {
                    // Not in game - handle menu commands
                    running = handleMenuInput(input);
                }
            }

        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Show main menu.
     */
    private static void showMenu() {
        System.out.println("\n=== Auction Game Menu ===");
        if (playerName == null) {
            System.out.println("1. Set Name");
        } else {
            System.out.println("Player: " + playerName);
            System.out.println("1. Join Game");
            System.out.println("2. View Leaderboard");
        }
        System.out.println("3. Quit");
        System.out.print("> ");
    }

    /**
     * Handle menu input.
     */
    private static boolean handleMenuInput(String input) throws IOException {
        switch (input) {
            case "1":
                if (playerName == null) {
                    handleSetName();
                } else {
                    handleJoin();
                }
                break;

            case "2":
                if (playerName != null) {
                    handleLeaderboard();
                } else {
                    System.out.println("Please set your name first.");
                }
                break;

            case "3":
                handleQuit();
                return false;

            default:
                System.out.println("Invalid choice. Try again.");
        }
        return true;
    }

    /**
     * Handle game input (bidding).
     */
    private static void handleGameInput(String input) throws IOException {
        try {
            int bidAmount = Integer.parseInt(input);
            // Bid amount will be sent when we know item ID
            System.out.println("Processing bid: " + bidAmount);
        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number for your bid.");
        }
    }

    /**
     * Handle SET NAME.
     */
    private static void handleSetName() throws IOException {
        System.out.print("Enter your name: ");
        String name = scanner.nextLine().trim();

        if (name.isEmpty()) {
            System.out.println("Name cannot be empty.");
            return;
        }

        // Send REGISTER request
        Request request = Request.newBuilder()
                .setType(Request.RequestType.REGISTER)
                .setName(name)
                .build();
        request.writeDelimitedTo(out);

        // Read response
        Response response = Response.parseDelimitedFrom(in);
        if (response != null) {
            System.out.println(response.getMessage());
            if (response.getOk()) {
                playerName = name;
            }
        }
    }

    /**
     * Handle JOIN GAME.
     */
    private static void handleJoin() throws IOException {
        // Send JOIN request
        Request request = Request.newBuilder()
                .setType(Request.RequestType.JOIN)
                .build();
        request.writeDelimitedTo(out);

        // Read response
        Response response = Response.parseDelimitedFrom(in);
        if (response != null && response.getType() == Response.ResponseType.GAME_JOINED) {
            System.out.println("\n" + response.getMessage());
            currentGold = response.getPlayerStatus().getGoldRemaining();
            if (response.hasNextItem()) {
                inGame = true;
                AuctionItem firstItem = response.getNextItem();
                displayItem(firstItem);
                playGame(firstItem);
            }
        } else if (response != null) {
            System.out.println(response.getMessage());
        }
    }

    /**
     * Play the game (bidding loop).
     */
    private static void playGame(AuctionItem firstItem) throws IOException {
        AuctionItem currentItem = firstItem;


        while (inGame) {
            if (currentItem != null) {
                // Prompt for bid (mention -1 to skip)
                System.out.print("\nEnter your bid (0-" + currentGold + ", or -1 to skip): ");
                String input = scanner.nextLine().trim();
                try {
                    int bidAmount = Integer.parseInt(input);

                    // Send BID request
                    Request bidRequest = Request.newBuilder()
                            .setType(Request.RequestType.BID)
                            .setItemId(currentItem.getId())
                            .setBidAmount(bidAmount)
                            .build();
                    bidRequest.writeDelimitedTo(out);

                    // Read response
                    Response response = Response.parseDelimitedFrom(in);
                    if (response == null) {
                        break;
                    }

                    if (response.getType() == Response.ResponseType.ERROR) {
                        System.out.println("Error: " + response.getMessage());
                        continue;
                    }

                    if (response.getType() == Response.ResponseType.BID_RESULT) {
                        // Display auction result
                        System.out.println("\n" + response.getMessage());
                        displayAuctionResult(response.getResult());

                        // Check for next item
                        if (response.hasNextItem()) {
                            currentItem = response.getNextItem();
                            currentGold = response.getPlayerStatus().getGoldRemaining();
                            displayItem(currentItem);
                        } else {
                            // No more items - should get GAME_OVER next
                            currentItem = null;
                        }
                    } else if (response.getType() == Response.ResponseType.GAME_OVER) {
                        // Game finished
                        System.out.println("\n" + response.getMessage());
                        displayGameOver(response.getGameResult());
                        inGame = false;
                        break;
                    }

                } catch (NumberFormatException e) {
                    System.out.println("Please enter a valid number.");
                }
            } else {
                // Waiting for GAME_OVER
                Response response = Response.parseDelimitedFrom(in);
                if (response != null && response.getType() == Response.ResponseType.GAME_OVER) {
                    System.out.println("\n" + response.getMessage());
                    displayGameOver(response.getGameResult());
                    inGame = false;
                    break;
                }
            }
        }
    }

    /**
     * Handle LEADERBOARD.
     */
    private static void handleLeaderboard() throws IOException {
        // Send LEADERBOARD request
        Request request = Request.newBuilder()
                .setType(Request.RequestType.LEADERBOARD)
                .build();
        request.writeDelimitedTo(out);

        // Read response
        Response response = Response.parseDelimitedFrom(in);
        if (response != null && response.getType() == Response.ResponseType.LEADERBOARD_RESPONSE) {
            System.out.println("\n" + response.getMessage());
            displayLeaderboard(response.getLeaderboard());
        }
    }

    /**
     * Handle QUIT.
     */
    private static void handleQuit() throws IOException {
        // Send QUIT request
        Request request = Request.newBuilder()
                .setType(Request.RequestType.QUIT)
                .build();
        request.writeDelimitedTo(out);

        // Read response
        Response response = Response.parseDelimitedFrom(in);
        if (response != null) {
            System.out.println(response.getMessage());
        }
    }

    /**
     * Display an item.
     */
    private static void displayItem(AuctionItem item) {
        System.out.println("\n=== Item #" + item.getId() + " ===");
        System.out.println("Name: " + item.getName());
        System.out.println("Category: " + item.getCategory());
        System.out.println("Value Range: " + item.getMinValue() + "-" + item.getMaxValue() + " gold");
        System.out.println("Reserve Price: " + item.getReservePrice() + " gold");
    }

    /**
     * Display auction result.
     */
    private static void displayAuctionResult(AuctionResult result) {
        System.out.println("Winner: " + result.getWinnerName() + " (bid: " + result.getWinningBid() + " gold)");
        System.out.println("Actual Value: " + result.getActualValue() + " gold");
        System.out.println("\nAll Bids:");
        for (PlayerBid bid : result.getAllBidsList()) {
            System.out.println("  " + bid.getPlayerName() + ": " + bid.getBidAmount() + " gold");
        }
    }

    /**
     * Display game over results.
     */
    private static void displayGameOver(GameResult result) {
        System.out.println("\n=== Final Scores ===");
        for (PlayerStatus score : result.getPlayerScoresList()) {
            System.out.println("\n" + score.getPlayerName() + ":");
            System.out.println("  Gold Remaining: " + score.getGoldRemaining());
            System.out.println("  Items Value: " + score.getItemsValue());
            System.out.println("  Total Score: " + score.getTotalScore());
            System.out.println("  Items Won: " + String.join(", ", score.getItemsWonList()));
        }
        System.out.println("\nWinner: " + result.getWinnerName());
        System.out.println("Your Leaderboard Position: #" + result.getLeaderboardPosition());
    }

    /**
     * Display leaderboard.
     */
    private static void displayLeaderboard(Leaderboard leaderboard) {
        System.out.println("\nRank | Player          | Score | Date");
        System.out.println("-----+-----------------+-------+---------------------");
        for (LeaderboardEntry entry : leaderboard.getEntriesList()) {
            System.out.printf("%4d | %-15s | %5d | %s\n",
                    entry.getRank(),
                    entry.getPlayerName(),
                    entry.getScore(),
                    entry.getTimestamp());
        }
    }

    /**
     * Cleanup resources.
     */
    private static void cleanup() {
        try {
            if (scanner != null) scanner.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}
