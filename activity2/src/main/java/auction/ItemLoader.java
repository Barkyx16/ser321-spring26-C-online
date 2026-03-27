package auction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Loads auction items from resources file.
 * Supports two modes:
 * - Normal mode: Random values within ranges (for gameplay)
 * - Grading mode: Fixed values (for deterministic testing)
 * No changes needed
 */
public class ItemLoader {
    private static final Random random = new Random();

    /**
     * Load items from resources.
     * @param gradingMode If true, use fixed values; if false, use random values
     * @return List of Item objects
     */
    public static List<Item> loadItems(boolean gradingMode) {
        if (gradingMode) {
            return loadGradingItems();
        } else {
            return loadRandomItems();
        }
    }

    /**
     * Load items with random values within specified ranges.
     * Each game will have different actual values (replayability).
     * Items are randomly selected from the full item pool.
     */
    private static List<Item> loadRandomItems() {
        List<ItemDefinition> definitions = loadItemDefinitions();
        List<Item> items = new ArrayList<>();

        // Shuffle to get random items from the pool
        List<ItemDefinition> shuffled = new ArrayList<>(definitions);
        java.util.Collections.shuffle(shuffled);

        // Cap at 5 items for reasonable game length
        int itemCount = Math.min(5, shuffled.size());

        for (int i = 0; i < itemCount; i++) {
            ItemDefinition def = shuffled.get(i);
            // Generate random actual value within range
            int actualValue = def.minValue + random.nextInt(def.maxValue - def.minValue + 1);

            Item item = new Item(
                    def.id,
                    def.name,
                    def.category,
                    def.minValue,
                    def.maxValue,
                    actualValue
            );
            items.add(item);
        }

        return items;
    }

    /**
     * Load items with fixed values for grading.
     * Always returns same values for deterministic testing.
     */
    private static List<Item> loadGradingItems() {
        List<ItemDefinition> definitions = loadItemDefinitions();
        List<Item> items = new ArrayList<>();

        // Cap at 5 items for reasonable game length
        int itemCount = Math.min(5, definitions.size());

        for (int i = 0; i < itemCount; i++) {
            ItemDefinition def = definitions.get(i);
            // Use fixed value (middle of range) for grading
            int actualValue = (def.minValue + def.maxValue) / 2;

            Item item = new Item(
                    def.id,
                    def.name,
                    def.category,
                    def.minValue,
                    def.maxValue,
                    actualValue
            );
            items.add(item);
        }

        return items;
    }

    /**
     * Load item definitions from resources file.
     */
    private static List<ItemDefinition> loadItemDefinitions() {
        List<ItemDefinition> definitions = new ArrayList<>();

        try {
            InputStream is = ItemLoader.class.getClassLoader().getResourceAsStream("items.txt");
            if (is == null) {
                System.err.println("Warning: items.txt not found, using default items");
                return getDefaultItems();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            int id = 1;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse line: name,category,minValue,maxValue
                String[] parts = line.split(",");
                if (parts.length != 4) {
                    System.err.println("Warning: Invalid line format: " + line);
                    continue;
                }

                try {
                    String name = parts[0].trim();
                    String category = parts[1].trim();
                    int minValue = Integer.parseInt(parts[2].trim());
                    int maxValue = Integer.parseInt(parts[3].trim());

                    definitions.add(new ItemDefinition(id++, name, category, minValue, maxValue));
                } catch (NumberFormatException e) {
                    System.err.println("Warning: Invalid number in line: " + line);
                }
            }

            reader.close();
        } catch (IOException e) {
            System.err.println("Error loading items: " + e.getMessage());
            return getDefaultItems();
        }

        return definitions;
    }

    /**
     * Get default items if file not found.
     */
    private static List<ItemDefinition> getDefaultItems() {
        List<ItemDefinition> items = new ArrayList<>();
        items.add(new ItemDefinition(1, "Magic Sword", "Weapon", 30, 50));
        items.add(new ItemDefinition(2, "Health Potion", "Consumable", 5, 15));
        items.add(new ItemDefinition(3, "Ancient Shield", "Armor", 25, 40));
        items.add(new ItemDefinition(4, "Gold Ring", "Jewelry", 15, 25));
        items.add(new ItemDefinition(5, "Spell Scroll", "Magic", 20, 35));
        return items;
    }

    /**
     * Helper class to hold item definition before creating Item object.
     */
    private static class ItemDefinition {
        int id;
        String name;
        String category;
        int minValue;
        int maxValue;

        ItemDefinition(int id, String name, String category, int minValue, int maxValue) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }
    }
}
