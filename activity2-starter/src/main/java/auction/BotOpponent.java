package auction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BotOpponent {

    private final String name;
    private int gold;
    private final List<Item> inventory;
    private final Random random;
    private final boolean gradingMode;

    public BotOpponent(String name,
                       boolean gradingMode) {

        this.name = name;

        this.gold = 150;

        this.inventory = new ArrayList<>();

        this.gradingMode = gradingMode;

        if (gradingMode) {

            this.random = new Random(name.hashCode());

        } else {

            this.random = new Random();
        }
    }

    public int decideBid(Item item,
                         int reservePrice) {

        if (gold == 0) {
            return 0;
        }

        int avgValue =
                (item.getMinValue() +
                 item.getMaxValue()) / 2;

        int minBid = (avgValue * 40) / 100;

        int maxBid = (avgValue * 70) / 100;

        maxBid = Math.min(maxBid, gold);

        minBid = Math.min(minBid, gold);

        if (minBid > maxBid) {

            minBid = maxBid;
        }

        if (!gradingMode &&
            random.nextInt(10) == 0) {

            return 0;
        }

        int bid;

        if (minBid == maxBid) {

            bid = minBid;

        } else {

            bid =
                    minBid +
                    random.nextInt(maxBid - minBid + 1);
        }

        if (bid > 0 &&
            bid < reservePrice) {

            if (reservePrice <= gold) {

                return reservePrice;
            }

            return 0;
        }

        return bid;
    }

    public void awardItem(Item item,
                          int bidAmount) {

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

    public int getTotalScore() {

        return gold + getInventoryValue();
    }

    public String getName() {

        return name;
    }

    public int getGold() {

        return gold;
    }

    public List<String> getItemNames() {

        List<String> names =
                new ArrayList<>();

        for (Item item : inventory) {

            names.add(item.getName());
        }

        return names;
    }
}
