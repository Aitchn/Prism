package com.aitchn.validation.botania.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recipe tables. Prism has no recipe content type yet (see docs/gaps.md), so they live in code. Amounts follow the
 * original mod where known, otherwise they are approximations: this is a validation, not a clone.
 */
public final class Recipes {

    public static final String NS = "botania:";
    public static final List<String> DYES = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
            "purple", "blue", "brown", "green", "red", "black");

    public record Infusion(String result, int cost) {}

    private static final Map<String, Infusion> INFUSIONS = Map.of(
            "minecraft:iron_ingot", new Infusion(NS + "manasteel_ingot", 3000),
            "minecraft:ender_pearl", new Infusion(NS + "mana_pearl", 6000),
            "minecraft:diamond", new Infusion(NS + "mana_diamond", 10000));

    private static final Map<String, Integer> FUELS = Map.of(
            "minecraft:coal", 1600,
            "minecraft:charcoal", 1600,
            "minecraft:blaze_rod", 2400,
            "minecraft:stick", 100,
            "minecraft:oak_planks", 300,
            "minecraft:oak_log", 300);

    private static final Map<String, String> CONVERSIONS = Map.of(
            "minecraft:stone", NS + "livingrock",
            "minecraft:oak_log", NS + "livingwood");

    /** Approximate petal recipes: result flower item to its petals. */
    private static final Map<String, List<String>> PETAL_RECIPES = Map.of(
            NS + "daybloom", List.of("yellow", "yellow", "light_blue"),
            NS + "endoflame", List.of("brown", "brown", "red", "light_gray"),
            NS + "pure_daisy", List.of("white", "white", "white", "white"));

    private Recipes() {}

    public static String petalItem(String dye) {
        return NS + "petal_" + dye;
    }

    public static boolean isPetal(String itemId) {
        return itemId.startsWith(NS + "petal_") && DYES.contains(itemId.substring((NS + "petal_").length()));
    }

    public static Infusion infusion(String inputItem) {
        return INFUSIONS.get(inputItem);
    }

    /** Burn time in ticks, 0 if the item is not a fuel. */
    public static int burnTime(String itemId) {
        return FUELS.getOrDefault(itemId, 0);
    }

    /** Block a Pure Daisy turns this block into, or null. */
    public static String conversion(String blockId) {
        return CONVERSIONS.get(blockId);
    }

    /** Result item for exactly these petals (order does not matter), or null. */
    public static String petal(List<String> petals) {
        List<String> sorted = new ArrayList<>(petals);
        sorted.sort(null);
        for (var entry : PETAL_RECIPES.entrySet()) {
            List<String> wanted = new ArrayList<>(entry.getValue().stream().map(Recipes::petalItem).toList());
            wanted.sort(null);
            if (wanted.equals(sorted)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
