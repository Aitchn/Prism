package com.example.prism;

import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.api.recipe.Ingredient;
import com.aitchn.prism.api.recipe.ItemResult;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * The example mod's own recipe type, {@code examplemod:polishing}: what a machine would turn into what, and how long
 * it takes. Prism loads these from every mod's {@code prism/recipes/} and lists them; a machine block would match
 * its input with {@code Prism.items().matches(recipe.input(), stack)}.
 */
public record Polishing(@NotNull Ingredient input, @NotNull ItemResult result, int ticks) {

    static Polishing decode(ConfigSection section, String namespace) throws ContentException {
        section.rejectUnknownKeys(Set.of("input", "result", "ticks"));
        int ticks = section.getInt("ticks").orElse(200);
        if (ticks < 1) {
            throw section.error("ticks must be at least 1");
        }
        return new Polishing(Ingredient.read(section, "input", namespace), ItemResult.read(section, "result", namespace),
                ticks);
    }
}
