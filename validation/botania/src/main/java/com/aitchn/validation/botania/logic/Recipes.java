package com.aitchn.validation.botania.logic;

import java.util.List;
import java.util.Map;

/** Fixed vocabulary: dye names, petal ids and the vanilla fuel values Endoflame burns. Recipes live in {@link RecipeBook}. */
public final class Recipes {

    public static final String NS = "botania:";
    public static final List<String> DYES = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
            "purple", "blue", "brown", "green", "red", "black");

    private static final Map<String, Integer> FUELS = Map.of(
            "minecraft:coal", 1600,
            "minecraft:charcoal", 1600,
            "minecraft:blaze_rod", 2400,
            "minecraft:stick", 100,
            "minecraft:oak_planks", 300,
            "minecraft:oak_log", 300);

    private Recipes() {}

    public static String petalItem(String dye) {
        return NS + "petal_" + dye;
    }

    public static boolean isPetal(String itemId) {
        return itemId.startsWith(NS + "petal_") && DYES.contains(itemId.substring((NS + "petal_").length()));
    }

    /** Burn time in ticks, 0 if the item is not a fuel. */
    public static int burnTime(String itemId) {
        return FUELS.getOrDefault(itemId, 0);
    }
}
