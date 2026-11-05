package com.aitchn.validation.botania;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.api.recipe.Ingredient;
import com.aitchn.prism.api.recipe.ItemResult;
import com.aitchn.prism.api.recipe.RecipeService;
import com.aitchn.validation.botania.logic.RecipeBook;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The three machine recipe types of this mod and the loader that turns them into a {@link RecipeBook}. */
final class BotaniaRecipes {

    /** {@code botania:mana_infusion}: a dropped item in a pool becomes another item for some mana. */
    record Infusion(Ingredient input, ItemResult result, int mana) {}

    /** {@code botania:petal}: the petals in a Petal Apothecary become a flower. */
    record Petal(List<Ingredient> petals, ItemResult result) {}

    /** {@code botania:pure_daisy}: a Pure Daisy turns a block into another block. */
    record Conversion(PrismKey input, PrismKey result) {}

    private BotaniaRecipes() {}

    static Infusion infusion(ConfigSection section, String namespace) throws ContentException {
        section.rejectUnknownKeys(Set.of("input", "result", "mana"));
        Ingredient input = Ingredient.read(section, "input", namespace);
        requirePlain(section, input);
        int mana = section.requireInt("mana");
        if (mana < 1) {
            throw section.error("mana must be positive");
        }
        return new Infusion(input, ItemResult.read(section, "result", namespace), mana);
    }

    static Petal petal(ConfigSection section, String namespace) throws ContentException {
        section.rejectUnknownKeys(Set.of("ingredients", "result"));
        List<Ingredient> petals = new ArrayList<>();
        for (String text : section.getStringList("ingredients")) {
            Ingredient ingredient;
            try {
                ingredient = Ingredient.parse(text, namespace, 1);
            } catch (IllegalArgumentException e) {
                throw section.error("ingredients: " + e.getMessage());
            }
            requirePlain(section, ingredient);
            petals.add(ingredient);
        }
        if (petals.isEmpty() || petals.size() > 16) {
            throw section.error("ingredients needs 1 to 16 entries");
        }
        return new Petal(List.copyOf(petals), ItemResult.read(section, "result", namespace));
    }

    static Conversion conversion(ConfigSection section, String namespace) throws ContentException {
        section.rejectUnknownKeys(Set.of("input", "result"));
        try {
            return new Conversion(PrismKey.parse(section.requireString("input"), namespace),
                    PrismKey.parse(section.requireString("result"), namespace));
        } catch (IllegalArgumentException e) {
            throw section.error(e.getMessage());
        }
    }

    private static void requirePlain(ConfigSection section, Ingredient ingredient) throws ContentException {
        if (ingredient.isTag() || ingredient.count() != 1) {
            throw section.error("machine inputs name one item, not a tag or a count: " + ingredient);
        }
    }

    /** Reads every loaded recipe of the three types. Call again after content reloads. */
    static RecipeBook load(RecipeService recipes) {
        RecipeBook book = new RecipeBook();
        for (var recipe : recipes.all(BotaniaContent.INFUSION)) {
            var value = recipe.value();
            book.addInfusion(value.input().item().toString(), value.result().item().toString(), value.mana());
        }
        for (var recipe : recipes.all(BotaniaContent.PETAL)) {
            var value = recipe.value();
            book.addPetals(value.result().item().toString(),
                    value.petals().stream().map(petal -> petal.item().toString()).toList());
        }
        for (var recipe : recipes.all(BotaniaContent.PURE_DAISY)) {
            book.addConversion(recipe.value().input().toString(), recipe.value().result().toString());
        }
        return book;
    }
}
