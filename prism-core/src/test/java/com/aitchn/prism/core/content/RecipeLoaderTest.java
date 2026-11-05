package com.aitchn.prism.core.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.Behavior;
import com.aitchn.prism.api.content.BehaviorType;
import com.aitchn.prism.api.content.ComponentType;
import com.aitchn.prism.api.recipe.Ingredient;
import com.aitchn.prism.api.recipe.PrismRecipes;
import com.aitchn.prism.api.recipe.RecipeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecipeLoaderTest {

    record Press(Ingredient input, int ticks) {}

    static final RecipeType<Press> PRESS = new RecipeType<>(PrismKey.parse("m:press"), Press.class,
            (section, namespace) -> new Press(Ingredient.read(section, "input", namespace), section.requireInt("ticks")));

    private static final TypeLookup TYPES = new TypeLookup() {
        @Override
        public Optional<ComponentType<?>> component(PrismKey key) {
            return Optional.empty();
        }

        @Override
        public Optional<BehaviorType<? extends Behavior>> behavior(PrismKey key) {
            return Optional.empty();
        }

        @Override
        public Optional<RecipeType<?>> recipe(PrismKey key) {
            if (key.equals(PRESS.key())) {
                return Optional.of(PRESS);
            }
            return PrismRecipes.ALL.stream().<RecipeType<?>>map(t -> t).filter(t -> t.key().equals(key)).findFirst();
        }
    };

    private static ContentLoader.Loaded load(MemoryContentSource source, List<Problem> problems) {
        return new ContentLoader(TYPES, k -> true, k -> true).load(source, "prism", "m", "mod", problems);
    }

    @Test
    void loadsBuiltInAndModRecipeTypes() {
        MemoryContentSource source = new MemoryContentSource()
                .put("prism/recipes/block.yml", """
                        type: prism:shaped
                        pattern: ["GG", "GG"]
                        key:
                          G: gem
                        result: {item: block, count: 2}
                        """)
                .put("prism/recipes/dust.yml", """
                        type: prism:shapeless
                        ingredients: [gem, "#minecraft:planks"]
                        result: minecraft:redstone
                        """)
                .put("prism/recipes/cook.yml", """
                        type: prism:blasting
                        input: minecraft:iron_ore
                        result: gem
                        """)
                .put("prism/recipes/machines/press.yml", """
                        type: press
                        input: {item: gem, count: 3}
                        ticks: 40
                        """);
        List<Problem> problems = new ArrayList<>();
        var loaded = load(source, problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(4, loaded.recipes().size());

        var shaped = (PrismRecipes.Shaped) loaded.recipes().get(PrismKey.parse("m:block")).value();
        assertEquals("m:gem", shaped.key().get('G').item().toString());
        assertEquals(2, shaped.result().count());

        var shapeless = (PrismRecipes.Shapeless) loaded.recipes().get(PrismKey.parse("m:dust")).value();
        assertEquals("minecraft:planks", shapeless.ingredients().get(1).tag().toString());

        var cook = (PrismRecipes.Cooking) loaded.recipes().get(PrismKey.parse("m:cook")).value();
        assertEquals(PrismRecipes.Cooker.BLAST_FURNACE, cook.cooker());
        assertEquals(100, cook.ticks());

        var press = (Press) loaded.recipes().get(PrismKey.parse("m:machines/press")).value();
        assertEquals(3, press.input().count());
        assertEquals(40, press.ticks());
    }

    @Test
    void reportsEveryBadRecipe() {
        MemoryContentSource source = new MemoryContentSource()
                .put("prism/recipes/a.yml", "type: prism:shaped\npattern: [\"XY\"]\nkey: {X: gem}\nresult: gem\n")
                .put("prism/recipes/b.yml", "type: nope:thing\n")
                .put("prism/recipes/c.yml", "type: prism:shapeless\ningredients: []\nresult: gem\n")
                .put("prism/recipes/d.yml", "type: prism:smelting\ninput: {item: gem, count: 2}\nresult: gem\n");
        List<Problem> problems = new ArrayList<>();
        var loaded = load(source, problems);
        assertEquals(0, loaded.recipes().size());
        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.get(0).message().contains("'Y'"), problems.toString());
        assertTrue(problems.get(1).message().contains("unknown recipe type nope:thing"), problems.toString());
    }
}
