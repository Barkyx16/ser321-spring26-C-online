package auction;

import buffers.LeaderboardEntry;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LeaderboardManager {

    private final List<ScoreEntry> scores;
    private final String persistenceFile;

    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static class ScoreEntry implements Comparable<ScoreEntry> {

        String playerName;
        int score;
        LocalDateTime timestamp;

        ScoreEntry(String playerName,
                   int score,
                   LocalDateTime timestamp) {

            this.playerName = playerName;
            this.score = score;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(ScoreEntry other) {

            if (this.score != other.score) {
                return Integer.compare(other.score, this.score);
            }

            return this.timestamp.compareTo(other.timestamp);
        }
    }

    public LeaderboardManager(String persistenceFile) {

        this.scores =
                Collections.synchronizedList(new ArrayList<>());

        this.persistenceFile = persistenceFile;

        loadFromFile();
    }

    public synchronized int addScore(String playerName,
                                     int score) {

        ScoreEntry entry =
                new ScoreEntry(playerName,
                               score,
                               LocalDateTime.now());

        scores.add(entry);

        Collections.sort(scores);

        saveToFile();

        for (int i = 0; i < scores.size(); i++) {

            if (scores.get(i) == entry) {

                return i + 1;
            }
        }

        return scores.size();
    }

    public synchronized List<LeaderboardEntry> getTopScores(int n) {

        List<LeaderboardEntry> result = new ArrayList<>();

        int limit = Math.min(n, scores.size());

        for (int i = 0; i < limit; i++) {

            ScoreEntry entry = scores.get(i);

            result.add(
                    LeaderboardEntry.newBuilder()
                            .setRank(i + 1)
                            .setPlayerName(entry.playerName)
                            .setScore(entry.score)
                            .setTimestamp(entry.timestamp.format(formatter))
                            .build()
            );
        }

        return result;
    }

    public synchronized int size() {

        return scores.size();
    }

    private synchronized void loadFromFile() {

        File file = new File(persistenceFile);

        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader =
                     new BufferedReader(new FileReader(file))) {

            String line;

            while ((line = reader.readLine()) != null) {

                String[] parts = line.split("\\|");

                if (parts.length == 3) {

                    String name = parts[0];

                    int score = Integer.parseInt(parts[1]);

                    LocalDateTime time =
                            LocalDateTime.parse(parts[2], formatter);

                    scores.add(
                            new ScoreEntry(name,
                                           score,
                                           time)
                    );
                }
            }

            Collections.sort(scores);

        } catch (Exception e) {

            System.err.println("Could not load scores file");
        }
    }

    private synchronized void saveToFile() {

        try (BufferedWriter writer =
                     new BufferedWriter(
                             new FileWriter(persistenceFile))) {

            for (ScoreEntry entry : scores) {

                writer.write(
                        entry.playerName + "|" +
                        entry.score + "|" +
                        entry.timestamp.format(formatter)
                );

                writer.newLine();
            }

        } catch (IOException e) {

            System.err.println("Could not save scores file");
        }
    }

    public synchronized void clear() {

        scores.clear();

        saveToFile();
    }
}
