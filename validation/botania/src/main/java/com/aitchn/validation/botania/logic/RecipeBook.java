package com.aitchn.validation.botania.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The machine recipes in force. The plugin fills it from Prism's recipe registry ({@code prism/recipes/*.yml}); tests
 * use {@link #defaults()}, which mirrors the shipped YAML (a content test keeps the two in step).
 */
public final class RecipeBook {

    public record Infusion(String result, int cost) {}

    public record PetalRecipe(String result, List<String> petals) {}

    private final Map<String, Infusion> infusions = new HashMap<>();
    private final Map<String, String> conversions = new HashMap<>();
    private final List<PetalRecipe> petalRecipes = new ArrayList<>();

    public RecipeBook addInfusion(String input, String result, int cost) {
        infusions.put(input, new Infusion(result, cost));
        return this;
    }

    public RecipeBook addConversion(String block, String result) {
        conversions.put(block, result);
        return this;
    }

    public RecipeBook addPetals(String result, List<String> petals) {
        petalRecipes.add(new PetalRecipe(result, List.copyOf(petals)));
        return this;
    }

    public static RecipeBook defaults() {
        return new RecipeBook()
                .addInfusion("minecraft:iron_ingot", "botania:manasteel_ingot", 3000)
                .addInfusion("minecraft:ender_pearl", "botania:mana_pearl", 6000)
                .addInfusion("minecraft:diamond", "botania:mana_diamond", 10000)
                .addConversion("minecraft:stone", "botania:livingrock")
                .addConversion("minecraft:oak_log", "botania:livingwood")
                .addPetals("botania:daybloom", List.of("botania:petal_yellow", "botania:petal_yellow",
                        "botania:petal_light_blue"))
                .addPetals("botania:endoflame", List.of("botania:petal_brown", "botania:petal_brown",
                        "botania:petal_red", "botania:petal_light_gray"))
                .addPetals("botania:pure_daisy", List.of("botania:petal_white", "botania:petal_white",
                        "botania:petal_white", "botania:petal_white"));
    }

    public Infusion infusion(String inputItem) {
        return infusions.get(inputItem);
    }

    /** Block a Pure Daisy turns this block into, or null. */
    public String conversion(String blockId) {
        return conversions.get(blockId);
    }

    /** Result item for exactly these petals (order does not matter), or null. */
    public String petal(List<String> petals) {
        List<String> sorted = new ArrayList<>(petals);
        sorted.sort(null);
        for (PetalRecipe recipe : petalRecipes) {
            List<String> wanted = new ArrayList<>(recipe.petals());
            wanted.sort(null);
            if (wanted.equals(sorted)) {
                return recipe.result();
            }
        }
        return null;
    }
}
