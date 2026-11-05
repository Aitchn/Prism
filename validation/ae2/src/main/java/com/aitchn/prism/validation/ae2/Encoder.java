package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.runtime.StackCodec;
import com.aitchn.prism.validation.ae2.storage.ItemKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

/** Reads a server crafting recipe (vanilla or Prism's) and turns it into a {@link Pattern}. */
final class Encoder {

    /** A pattern, or the reason there is none. */
    record Result(Optional<Pattern> pattern, String error) {}

    private Encoder() {}

    static Result encode(ItemStack target) {
        ItemStack one = target.asOne();
        Recipe recipe = Bukkit.getRecipesFor(one).stream()
                .filter(r -> r instanceof ShapedRecipe || r instanceof ShapelessRecipe).findFirst().orElse(null);
        if (recipe == null) {
            return new Result(Optional.empty(), "No crafting recipe makes that item.");
        }
        List<RecipeChoice> choices = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            Map<Character, RecipeChoice> map = shaped.getChoiceMap();
            for (String row : shaped.getShape()) {
                for (char c : row.toCharArray()) {
                    RecipeChoice choice = map.get(c);
                    if (choice != null) {
                        choices.add(choice);
                    }
                }
            }
        } else {
            choices.addAll(((ShapelessRecipe) recipe).getChoiceList());
        }
        Map<ItemKey, Long> inputs = new HashMap<>();
        for (RecipeChoice choice : choices) {
            ItemStack ingredient = firstOf(choice);
            if (ingredient == null) {
                return new Result(Optional.empty(), "That recipe uses an ingredient type patterns cannot encode.");
            }
            inputs.merge(StackCodec.key(ingredient), 1L, Long::sum);
        }
        return new Result(Optional.of(new Pattern("enc-" + UUID.randomUUID(), inputs, StackCodec.key(one),
                Math.max(1, recipe.getResult().getAmount()))), "");
    }

    private static ItemStack firstOf(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice material && !material.getChoices().isEmpty()) {
            return new ItemStack(material.getChoices().get(0));
        }
        if (choice instanceof RecipeChoice.ExactChoice exact && !exact.getChoices().isEmpty()) {
            return exact.getChoices().get(0).clone();
        }
        return null;
    }
}
