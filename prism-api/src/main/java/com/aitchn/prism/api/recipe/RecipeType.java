package com.aitchn.prism.api.recipe;

import com.aitchn.prism.api.PrismKey;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * A kind of recipe that YAML files can declare with {@code type: ns:name}. Prism only stores and loads recipes; what a
 * recipe means (an enrichment chamber input, a petal apothecary recipe) is the owning mod's design. Prism's own types
 * in {@link PrismRecipes} become vanilla crafting table and furnace recipes.
 */
public record RecipeType<R>(@NotNull PrismKey key, @NotNull Class<R> valueType, @NotNull RecipeDecoder<R> decoder) {

    public RecipeType {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(valueType, "valueType");
        Objects.requireNonNull(decoder, "decoder");
    }
}
