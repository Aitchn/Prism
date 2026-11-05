package com.aitchn.prism.api.recipe;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Prism's own recipe types. Prism turns every recipe of these types into a real vanilla recipe (crafting table,
 * furnace, blast furnace, smoker, campfire), so players get custom items in survival, on Java and on Bedrock
 * (Geyser sends server recipes to Bedrock clients). Custom item ingredients match by identity only, so stacks with
 * per-stack data or lore still count.
 */
public final class PrismRecipes {

    private PrismRecipes() {}

    /** {@code pattern: ["RR", "RR"]}, {@code key: {R: examplemod:ruby}}, {@code result}. */
    public record Shaped(@NotNull List<String> pattern, @NotNull Map<Character, Ingredient> key, @NotNull ItemResult result) {}

    /** {@code ingredients: [..]} (1 to 9 in total, counting each ingredient's count), {@code result}. */
    public record Shapeless(@NotNull List<Ingredient> ingredients, @NotNull ItemResult result) {}

    /** Which vanilla block cooks a {@link Cooking} recipe. */
    public enum Cooker {
        FURNACE(200), BLAST_FURNACE(100), SMOKER(100), CAMPFIRE(600);

        private final int defaultTicks;

        Cooker(int defaultTicks) {
            this.defaultTicks = defaultTicks;
        }

        public int defaultTicks() {
            return defaultTicks;
        }
    }

    /** {@code input}, {@code result}, optional {@code experience} (0) and {@code time} in ticks (the block's default). */
    public record Cooking(@NotNull Cooker cooker, @NotNull Ingredient input, @NotNull ItemResult result, float experience,
            int ticks) {}

    public static final RecipeType<Shaped> SHAPED = new RecipeType<>(key("shaped"), Shaped.class, PrismRecipes::shaped);

    public static final RecipeType<Shapeless> SHAPELESS =
            new RecipeType<>(key("shapeless"), Shapeless.class, PrismRecipes::shapeless);

    public static final RecipeType<Cooking> SMELTING = cooking("smelting", Cooker.FURNACE);
    public static final RecipeType<Cooking> BLASTING = cooking("blasting", Cooker.BLAST_FURNACE);
    public static final RecipeType<Cooking> SMOKING = cooking("smoking", Cooker.SMOKER);
    public static final RecipeType<Cooking> CAMPFIRE_COOKING = cooking("campfire_cooking", Cooker.CAMPFIRE);

    /** Every built-in type. */
    public static final List<RecipeType<?>> ALL = List.of(SHAPED, SHAPELESS, SMELTING, BLASTING, SMOKING, CAMPFIRE_COOKING);

    private static PrismKey key(String path) {
        return new PrismKey(PrismKey.PRISM_NAMESPACE, path);
    }

    private static RecipeType<Cooking> cooking(String path, Cooker cooker) {
        return new RecipeType<>(key(path), Cooking.class, (section, namespace) -> {
            section.rejectUnknownKeys(Set.of("input", "result", "experience", "time"));
            Ingredient input = single(Ingredient.read(section, "input", namespace), section, "input");
            double experience = section.getDouble("experience").orElse(0.0);
            int ticks = section.getInt("time").orElse(cooker.defaultTicks());
            if (experience < 0 || ticks < 1) {
                throw section.error("experience must not be negative and time must be at least 1 tick");
            }
            return new Cooking(cooker, input, ItemResult.read(section, "result", namespace), (float) experience, ticks);
        });
    }

    private static Shaped shaped(ConfigSection section, String namespace) throws ContentException {
        section.rejectUnknownKeys(Set.of("pattern", "key", "result"));
        List<String> pattern = section.getStringList("pattern");
        if (pattern.isEmpty() || pattern.size() > 3) {
            throw section.error("pattern needs 1 to 3 rows");
        }
        int width = pattern.getFirst().length();
        for (String row : pattern) {
            if (row.isEmpty() || row.length() > 3 || row.length() != width) {
                throw section.error("pattern rows must all have the same length, 1 to 3");
            }
        }
        ConfigSection keys = section.requireSection("key");
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        for (String symbol : keys.keys()) {
            if (symbol.length() != 1 || symbol.equals(" ")) {
                throw keys.error("key symbols are single characters other than space, got '" + symbol + "'");
            }
            key.put(symbol.charAt(0), single(Ingredient.read(keys, symbol, namespace), keys, symbol));
        }
        for (String row : pattern) {
            for (char symbol : row.toCharArray()) {
                if (symbol != ' ' && !key.containsKey(symbol)) {
                    throw section.error("pattern uses '" + symbol + "' but key does not define it");
                }
            }
        }
        return new Shaped(List.copyOf(pattern), Map.copyOf(key), ItemResult.read(section, "result", namespace));
    }

    private static Shapeless shapeless(ConfigSection section, String namespace) throws ContentException {
        section.rejectUnknownKeys(Set.of("ingredients", "result"));
        List<Ingredient> ingredients = new ArrayList<>();
        for (String text : section.getStringList("ingredients")) {
            try {
                ingredients.add(Ingredient.parse(text, namespace, 1));
            } catch (IllegalArgumentException e) {
                throw section.error("ingredients: " + e.getMessage());
            }
        }
        if (ingredients.isEmpty() || ingredients.size() > 9) {
            throw section.error("ingredients needs 1 to 9 entries");
        }
        return new Shapeless(List.copyOf(ingredients), ItemResult.read(section, "result", namespace));
    }

    private static Ingredient single(Ingredient ingredient, ConfigSection section, String field) throws ContentException {
        if (ingredient.count() != 1) {
            throw section.error(field + ": vanilla recipes take one item per slot (repeat the ingredient instead)");
        }
        return ingredient;
    }
}
