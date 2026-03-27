package auction;

import buffers.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionServer {

    private static final int DEFAULT_PORT = 8889;
    private static final String SCORES_FILE = "scores.txt";
    private static final int INITIAL_GOLD = 150;

    private static LeaderboardManager leaderboard;

    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    private static final Set<String> activePlayerNames =
            Collections.synchronizedSet(new HashSet<>());

    private static boolean gradingMode = false;

    private static final String[] BOT_NAMES = {
            "Alaric", "Brynn", "Cedric", "Daphne",
            "Elara", "Finn", "Gwen", "Hugo",
            "Isolde", "Jasper"
    };

    private static final Random botNameRandom = new Random();

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        for (String arg : args) {
            if ("--grading".equals(arg)) {
                gradingMode = true;
                System.out.println("Running in grading mode");
            } else {
                try {
                    port = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    System.out.println("Bad port: " + arg);
                }
            }
        }

        leaderboard = new LeaderboardManager(SCORES_FILE);
        System.out.println("Leaderboard loaded with " + leaderboard.size() + " scores");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Auction Server started on port " + port);
            System.out.println("Waiting for connections...");

            int clientId = 0;

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientId++;
                    final int id = clientId;

                    System.out.println("Client " + id + " connected from "
                            + clientSocket.getInetAddress().getHostAddress());

                    pool.submit(() -> processConnection(clientSocket, id));

                } catch (IOException e) {
                    System.out.println("Error accepting client: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    private static void processConnection(Socket clientSocket, int clientId) {
        String playerName = null;
        PlayerGameState gameState = null;

        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {

            sendWelcome(out, "Welcome to the Auction Game! Please set your name.");

            Request request;
            while ((request = Request.parseDelimitedFrom(in)) != null) {
                Request.RequestType type = request.getType();
                System.out.println("[Client " + clientId + "] " + type);

                switch (type) {
                    case REGISTER: {
                        String[] result = handleRegister(request, playerName);
                        playerName = result[0];

                        Response response;
                        if (playerName != null) {
                            response = buildWelcome(
                                    "Welcome, " + playerName + "! You have " + INITIAL_GOLD
                                            + " gold. Type 'join' to start playing against bot opponents!"
                            );
                        } else {
                            response = buildError(result[1]);
                        }

                        response.writeDelimitedTo(out);
                        break;
                    }

                    case JOIN: {
                        Response response;

                        if (playerName == null) {
                            response = buildError("Please set your name first");
                        } else if (gameState != null && !gameState.isFinished()) {
                            response = buildError("You are already in a game");
                        } else {
                            gameState = new PlayerGameState(playerName, gradingMode);
                            response = handleJoin(gameState);
                        }

                        response.writeDelimitedTo(out);
                        break;
                    }

                    case BID: {
                        if (gameState == null) {
                            buildError("You must join a game first").writeDelimitedTo(out);
                            break;
                        }

                        BidOutcome outcome = handleBid(request, gameState);
                        outcome.bidResponse.writeDelimitedTo(out);

                        if (outcome.gameOverResponse != null) {
                            outcome.gameOverResponse.writeDelimitedTo(out);
                            gameState.markFinished();
                        }

                        break;
                    }

                    case LEADERBOARD: {
                        handleLeaderboard().writeDelimitedTo(out);
                        break;
                    }

                    case QUIT: {
                        handleQuit(gameState).writeDelimitedTo(out);
                        return;
                    }

                    default:
                        buildError("Unknown request type").writeDelimitedTo(out);
                }
            }

        } catch (IOException e) {
            System.out.println("[Client " + clientId + "] disconnected");
        } finally {
            if (playerName != null) {
                activePlayerNames.remove(playerName);
            }

            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String[] handleRegister(Request request, String currentName) {
        if (currentName != null) {
            return new String[]{currentName, null};
        }

        String name = request.getName().trim();

        if (name.isEmpty()) {
            return new String[]{null, "Name cannot be empty"};
        }

        synchronized (activePlayerNames) {
            if (activePlayerNames.contains(name)) {
                return new String[]{null, "Name already taken. Please choose another."};
            }

            activePlayerNames.add(name);
        }

        return new String[]{name, null};
    }

    private static Response handleJoin(PlayerGameState gameState) {
        Item firstItem = gameState.getCurrentItem();

        PlayerStatus status = PlayerStatus.newBuilder()
                .setGoldRemaining(gameState.getGold())
                .build();

        String message = "Game started! You're playing against "
                + gameState.getBot(0).name + ", "
                + gameState.getBot(1).name + ", and "
                + gameState.getBot(2).name + ". Current item:";

        return Response.newBuilder()
                .setType(Response.ResponseType.GAME_JOINED)
                .setOk(true)
                .setMessage(message)
                .setPlayerStatus(status)
                .setNextItem(itemToProto(firstItem))
                .build();
    }

    private static BidOutcome handleBid(Request request, PlayerGameState gameState) {
        Item currentItem = gameState.getCurrentItem();

        int itemId = request.getItemId();
        int bidAmount = request.getBidAmount();

        String validationError = gameState.validateBid(itemId, bidAmount);
        if (validationError != null) {
            return new BidOutcome(buildError(validationError), null);
        }

        int playerBid = (bidAmount == -1) ? 0 : bidAmount;

        LinkedHashMap<String, Integer> bids = new LinkedHashMap<>();
        bids.put(gameState.getPlayerName(), playerBid);

        for (BotState bot : gameState.getBots()) {
            int botBid = makeBotBid(bot.bot, currentItem);
            if (botBid < 0) {
                botBid = 0;
            }
            if (botBid > bot.gold) {
                botBid = bot.gold;
            }
            bids.put(bot.name, botBid);
        }

        int reservePrice = currentItem.getMinValue() / 2;
        String winnerName = "(unsold)";
        int winningBid = 0;

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(bids.entrySet());
        ranked.sort((a, b) -> {
            if (!a.getValue().equals(b.getValue())) {
                return Integer.compare(b.getValue(), a.getValue());
            }
            return a.getKey().compareTo(b.getKey());
        });

        Map.Entry<String, Integer> top = ranked.get(0);
        if (top.getValue() >= reservePrice) {
            winnerName = top.getKey();
            winningBid = top.getValue();

            if (winnerName.equals(gameState.getPlayerName())) {
                gameState.awardItemToPlayer(currentItem, winningBid);
            } else {
                BotState winnerBot = gameState.findBot(winnerName);
                if (winnerBot != null) {
                    winnerBot.awardItem(currentItem, winningBid);
                }
            }
        }

        AuctionResult.Builder resultBuilder = AuctionResult.newBuilder()
                .setItem(itemToProto(currentItem))
                .setActualValue(currentItem.getActualValue())
                .setWinnerName(winnerName)
                .setWinningBid(winningBid);

        for (Map.Entry<String, Integer> entry : bids.entrySet()) {
            resultBuilder.addAllBids(
                    PlayerBid.newBuilder()
                            .setPlayerName(entry.getKey())
                            .setBidAmount(entry.getValue())
                            .build()
            );
        }

        boolean moreItems = gameState.moveToNextItem();

        Response.Builder bidResponse = Response.newBuilder()
                .setType(Response.ResponseType.BID_RESULT)
                .setOk(true)
                .setResult(resultBuilder.build())
                .setPlayerStatus(
                        PlayerStatus.newBuilder()
                                .setGoldRemaining(gameState.getGold())
                                .build()
                );

        if (moreItems) {
            bidResponse.setMessage("Auction complete!");
            bidResponse.setNextItem(itemToProto(gameState.getCurrentItem()));
            return new BidOutcome(bidResponse.build(), null);
        }

        bidResponse.setMessage("Auction complete! Calculating final scores...");
        Response gameOver = buildGameOver(gameState);

        return new BidOutcome(bidResponse.build(), gameOver);
    }

    private static Response buildGameOver(PlayerGameState gameState) {
        List<PlayerStatus> finalStats = new ArrayList<>();

        PlayerStatus playerStatus = PlayerStatus.newBuilder()
                .setPlayerName(gameState.getPlayerName())
                .setGoldRemaining(gameState.getGold())
                .setItemsValue(gameState.getInventoryValue())
                .setTotalScore(gameState.getPlayerScore())
                .addAllItemsWon(gameState.getItemNames())
                .build();

        finalStats.add(playerStatus);

        for (BotState bot : gameState.getBots()) {
            finalStats.add(
                    PlayerStatus.newBuilder()
                            .setPlayerName(bot.name)
                            .setGoldRemaining(bot.gold)
                            .setItemsValue(bot.getInventoryValue())
                            .setTotalScore(bot.getScore())
                            .addAllItemsWon(bot.getItemNames())
                            .build()
            );
        }

        PlayerStatus winner = finalStats.stream()
                .max(Comparator
                        .comparingInt(PlayerStatus::getTotalScore)
                        .thenComparing(PlayerStatus::getPlayerName, Comparator.reverseOrder()))
                .orElse(playerStatus);

        synchronized (leaderboard) {
            addScoreToLeaderboard(gameState.getPlayerName(), gameState.getPlayerScore());
        }

        int rank = getLeaderboardRank(gameState.getPlayerName(), gameState.getPlayerScore());

        GameResult.Builder result = GameResult.newBuilder()
                .setWinnerName(winner.getPlayerName())
                .setLeaderboardPosition(rank);

        for (PlayerStatus status : finalStats) {
            result.addPlayerScores(status);
        }

        return Response.newBuilder()
                .setType(Response.ResponseType.GAME_OVER)
                .setOk(true)
                .setMessage("Game over! Final results:")
                .setGameResult(result.build())
                .build();
    }

    private static Response handleLeaderboard() {
        Leaderboard.Builder leaderboardBuilder = Leaderboard.newBuilder();

        synchronized (leaderboard) {
            List<?> topScores = getTopScoresFromLeaderboard(10);

            int rankCounter = 1;
            for (Object entry : topScores) {
                String name = readString(entry, "getPlayerName", "getName", "playerName", "name");
                int score = readInt(entry, "getScore", "score");
                String timestamp = readString(entry, "getTimestamp", "timestamp", "getTime", "time");

                int rank = readInt(entry, "getRank", "rank");
                if (rank == 0) {
                    rank = rankCounter;
                }

                leaderboardBuilder.addEntries(
                        LeaderboardEntry.newBuilder()
                                .setRank(rank)
                                .setPlayerName(name == null ? "" : name)
                                .setScore(score)
                                .setTimestamp(timestamp == null ? "" : timestamp)
                                .build()
                );

                rankCounter++;
            }
        }

        return Response.newBuilder()
                .setType(Response.ResponseType.LEADERBOARD_RESPONSE)
                .setOk(true)
                .setMessage("Top 10 Scores:")
                .setLeaderboard(leaderboardBuilder.build())
                .build();
    }

    private static Response handleQuit(PlayerGameState gameState) {
        String message = "Thanks for playing!";

        if (gameState != null && gameState.isFinished()) {
            message += " Final score: " + gameState.getPlayerScore() + ".";
        }

        message += " Goodbye!";

        return Response.newBuilder()
                .setType(Response.ResponseType.FAREWELL)
                .setOk(true)
                .setMessage(message)
                .build();
    }

    private static void sendWelcome(OutputStream out, String message) throws IOException {
        buildWelcome(message).writeDelimitedTo(out);
    }

    private static Response buildWelcome(String message) {
        return Response.newBuilder()
                .setType(Response.ResponseType.WELCOME)
                .setOk(true)
                .setMessage(message)
                .build();
    }

    private static Response buildError(String message) {
        return Response.newBuilder()
                .setType(Response.ResponseType.ERROR)
                .setOk(false)
                .setMessage(message)
                .build();
    }

    private static AuctionItem itemToProto(Item item) {
        return AuctionItem.newBuilder()
                .setId(item.getId())
                .setName(item.getName())
                .setCategory(item.getCategory())
                .setMinValue(item.getMinValue())
                .setMaxValue(item.getMaxValue())
                .setReservePrice(item.getMinValue() / 2)
                .build();
    }

    private static String getRandomBotName() {
        return BOT_NAMES[botNameRandom.nextInt(BOT_NAMES.length)];
    }

    private static int makeBotBid(BotOpponent bot, Item item) {
        try {
            Object result = tryMethod(bot, "generateBid", new Class[]{Item.class}, item);
            if (result == null) {
                result = tryMethod(bot, "makeBid", new Class[]{Item.class}, item);
            }
            if (result == null) {
                result = tryMethod(bot, "bidOnItem", new Class[]{Item.class}, item);
            }
            if (result == null) {
                result = tryMethod(bot, "bid", new Class[]{Item.class}, item);
            }

            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception ignored) {
        }

        return 0;
    }

    private static String getBotName(BotOpponent bot) {
        try {
            Object result = tryMethod(bot, "getName", new Class[]{});
            if (result instanceof String) {
                return (String) result;
            }
        } catch (Exception ignored) {
        }
        return "Bot";
    }

    private static Object tryMethod(Object target, String methodName, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getMethod(methodName, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void addScoreToLeaderboard(String playerName, int score) {
        try {
            tryMethod(leaderboard, "addScore", new Class[]{String.class, int.class}, playerName, score);
            return;
        } catch (Exception ignored) {
        }

        try {
            tryMethod(leaderboard, "recordScore", new Class[]{String.class, int.class}, playerName, score);
            return;
        } catch (Exception ignored) {
        }

        try {
            tryMethod(leaderboard, "addEntry", new Class[]{String.class, int.class}, playerName, score);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> getTopScoresFromLeaderboard(int limit) {
        try {
            Object result = tryMethod(leaderboard, "getTopScores", new Class[]{int.class}, limit);
            if (result instanceof List<?>) {
                return (List<?>) result;
            }
        } catch (Exception ignored) {
        }

        try {
            Object result = tryMethod(leaderboard, "topScores", new Class[]{int.class}, limit);
            if (result instanceof List<?>) {
                return (List<?>) result;
            }
        } catch (Exception ignored) {
        }

        return new ArrayList<>();
    }

    private static int getLeaderboardRank(String playerName, int score) {
        try {
            Object result = tryMethod(leaderboard, "getRank", new Class[]{String.class, int.class}, playerName, score);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception ignored) {
        }

        try {
            Object result = tryMethod(leaderboard, "getPlayerRank", new Class[]{String.class, int.class}, playerName, score);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception ignored) {
        }

        return 0;
    }

    private static String readString(Object target, String method1, String method2, String field1, String field2) {
        try {
            Object value = tryMethod(target, method1, new Class[]{});
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {
        }

        try {
            Object value = tryMethod(target, method2, new Class[]{});
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {
        }

        try {
            Field field = target.getClass().getDeclaredField(field1);
            field.setAccessible(true);
            Object value = field.get(target);
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {
        }

        try {
            Field field = target.getClass().getDeclaredField(field2);
            field.setAccessible(true);
            Object value = field.get(target);
            if (value != null) {
                return value.toString();
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static int readInt(Object target, String methodName, String fieldName) {
        try {
            Object value = tryMethod(target, methodName, new Class[]{});
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception ignored) {
        }

        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception ignored) {
        }

        return 0;
    }

    private static class BidOutcome {
        private final Response bidResponse;
        private final Response gameOverResponse;

        public BidOutcome(Response bidResponse, Response gameOverResponse) {
            this.bidResponse = bidResponse;
            this.gameOverResponse = gameOverResponse;
        }
    }

    private static class BotState {
        private final BotOpponent bot;
        private final String name;
        private int gold;
        private final List<Item> inventory;

        public BotState(BotOpponent bot, String name) {
            this.bot = bot;
            this.name = name;
            this.gold = INITIAL_GOLD;
            this.inventory = new ArrayList<>();
        }

        public void awardItem(Item item, int bidAmount) {
            inventory.add(item);
            gold -= bidAmount;
        }

        public int getInventoryValue() {
            int total = 0;
            for (Item item : inventory) {
                total += item.getActualValue();
            }
            return total;
        }

        public int getScore() {
            return gold + getInventoryValue();
        }

        public List<String> getItemNames() {
            List<String> names = new ArrayList<>();
            for (Item item : inventory) {
                names.add(item.getName());
            }
            return names;
        }
    }

    private static class PlayerGameState {
        private final String playerName;
        private int gold;
        private final List<Item> inventory;
        private final List<Item> items;
        private final List<BotState> bots;
        private int currentItemIndex;
        private boolean finished;

        public PlayerGameState(String playerName, boolean gradingMode) {
            this.playerName = playerName;
            this.gold = INITIAL_GOLD;
            this.inventory = new ArrayList<>();
            this.items = ItemLoader.loadItems(gradingMode);
            this.currentItemIndex = 0;
            this.finished = false;
            this.bots = new ArrayList<>();

            Set<String> usedNames = new HashSet<>();
            bots.add(createUniqueBot(usedNames, gradingMode));
            bots.add(createUniqueBot(usedNames, gradingMode));
            bots.add(createUniqueBot(usedNames, gradingMode));
        }

        private BotState createUniqueBot(Set<String> usedNames, boolean gradingMode) {
            String name;
            do {
                name = getRandomBotName();
            } while (usedNames.contains(name));

            usedNames.add(name);

            BotOpponent bot = new BotOpponent(name, gradingMode);
            String actualName = getBotName(bot);
            if (actualName != null && !actualName.isEmpty()) {
                name = actualName;
            }

            return new BotState(bot, name);
        }

        public String validateBid(int itemId, int bidAmount) {
            Item currentItem = getCurrentItem();

            if (currentItem.getId() != itemId) {
                return "Invalid item ID. Current item is #" + currentItem.getId();
            }

            if (bidAmount == -1) {
                return null;
            }

            if (bidAmount < 0) {
                return "Bid cannot be negative (use -1 to skip)";
            }

            if (bidAmount > gold) {
                return "Insufficient gold. You have " + gold + " gold.";
            }

            int reservePrice = currentItem.getMinValue() / 2;
            if (bidAmount > 0 && bidAmount < reservePrice) {
                return "Bid must meet reserve price of " + reservePrice + " gold.";
            }

            return null;
        }

        public void awardItemToPlayer(Item item, int bidAmount) {
            inventory.add(item);
            gold -= bidAmount;
        }

        public boolean moveToNextItem() {
            currentItemIndex++;
            return currentItemIndex < items.size();
        }

        public Item getCurrentItem() {
            return items.get(currentItemIndex);
        }

        public int getInventoryValue() {
            int total = 0;
            for (Item item : inventory) {
                total += item.getActualValue();
            }
            return total;
        }

        public int getPlayerScore() {
            return gold + getInventoryValue();
        }

        public List<String> getItemNames() {
            List<String> names = new ArrayList<>();
            for (Item item : inventory) {
                names.add(item.getName());
            }
            return names;
        }

        public BotState getBot(int index) {
            return bots.get(index);
        }

        public List<BotState> getBots() {
            return new ArrayList<>(bots);
        }

        public BotState findBot(String name) {
            for (BotState bot : bots) {
                if (bot.name.equals(name)) {
                    return bot;
                }
            }
            return null;
        }

        public String getPlayerName() {
            return playerName;
        }

        public int getGold() {
            return gold;
        }

        public boolean isFinished() {
            return finished;
        }

        public void markFinished() {
            finished = true;
        }
    }
}
