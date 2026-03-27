package auction;

/**
 * Represents an item in the auction.
 * Each item has a name, category, value range (shown to players),
 * and an actual value (hidden from players until auction ends).
 * No changes needed.
 */
public class Item {
    private final int id;
    private final String name;
    private final String category;
    private final int minValue;
    private final int maxValue;
    private final int actualValue;

    public Item(int id, String name, String category, int minValue, int maxValue, int actualValue) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.actualValue = actualValue;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public int getActualValue() {
        return actualValue;
    }

    /**
     * Get the value hint shown to players.
     * Shows the range but not the actual value.
     */
    public String getValueHint() {
        return minValue + "-" + maxValue + " gold";
    }

    /**
     * Get display info for players (before auction).
     * Does NOT include actual value.
     */
    public String getDisplayInfo() {
        return String.format("#%d: %s [%s] - Worth %s",
                id, name, category, getValueHint());
    }

    /**
     * Get full info (after auction).
     * Includes actual value.
     */
    public String getFullInfo() {
        return String.format("#%d: %s [%s] - Worth %s (actual: %d gold)",
                id, name, category, getValueHint(), actualValue);
    }

    @Override
    public String toString() {
        return getDisplayInfo();
    }
}
