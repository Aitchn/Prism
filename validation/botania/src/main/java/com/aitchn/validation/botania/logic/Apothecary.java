package com.aitchn.validation.botania.logic;

import java.util.ArrayList;
import java.util.List;

/** Petal apothecary: fill with water, drop in petals, trigger with seeds. */
public final class Apothecary implements BlockEntity {

    public static final int MAX_INGREDIENTS = 16;
    public static final String WATER_BUCKET = "minecraft:water_bucket";
    public static final String TRIGGER = "minecraft:wheat_seeds";

    public enum Kind {
        FILLED,
        ADDED,
        CRAFTED,
        REMOVED,
        NO_MATCH,
        REJECTED
    }

    /** What happened; {@code item} is the crafted result or the removed ingredient, if any. */
    public record Outcome(Kind kind, String item) {}

    private final Pos pos;
    private boolean water;
    private final List<String> ingredients;

    public Apothecary(Pos pos, boolean water, List<String> ingredients) {
        this.pos = pos;
        this.water = water;
        this.ingredients = new ArrayList<>(ingredients);
    }

    @Override
    public Pos pos() {
        return pos;
    }

    public synchronized boolean water() {
        return water;
    }

    public synchronized List<String> ingredients() {
        return List.copyOf(ingredients);
    }

    /**
     * Applies a right-click with {@code held} (null for an empty hand). The caller consumes the held item for
     * FILLED, ADDED and CRAFTED (one item) and gives {@code item} back for REMOVED.
     */
    public synchronized Outcome use(String held) {
        if (held == null) {
            if (ingredients.isEmpty()) {
                return new Outcome(Kind.REJECTED, null);
            }
            return new Outcome(Kind.REMOVED, ingredients.remove(ingredients.size() - 1));
        }
        if (held.equals(WATER_BUCKET)) {
            if (water) {
                return new Outcome(Kind.REJECTED, null);
            }
            water = true;
            return new Outcome(Kind.FILLED, null);
        }
        if (!water) {
            return new Outcome(Kind.REJECTED, null);
        }
        if (held.equals(TRIGGER)) {
            String result = Recipes.petal(ingredients);
            if (result == null) {
                return new Outcome(Kind.NO_MATCH, null);
            }
            ingredients.clear();
            water = false;
            return new Outcome(Kind.CRAFTED, result);
        }
        if (Recipes.isPetal(held) && ingredients.size() < MAX_INGREDIENTS) {
            ingredients.add(held);
            return new Outcome(Kind.ADDED, null);
        }
        return new Outcome(Kind.REJECTED, null);
    }

    @Override
    public void tick(WorldView world, ManaNetwork network) {
        // Passive block: reacts to clicks only.
    }

    @Override
    public synchronized String serialize() {
        return "apothecary;" + (water ? 1 : 0) + ";" + String.join("|", ingredients);
    }

    static Apothecary fromParts(Pos pos, String[] parts) {
        List<String> items = parts.length > 2 && !parts[2].isEmpty() ? List.of(parts[2].split("\\|")) : List.of();
        return new Apothecary(pos, parts[1].equals("1"), items);
    }
}
