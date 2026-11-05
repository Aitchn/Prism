package com.aitchn.prism.api.recipe;

import com.aitchn.prism.api.PrismKey;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * One loaded recipe. {@code id} comes from the file path below {@code prism/recipes/} in the mod that declared it,
 * which may be a different mod from the one that owns the type (mods add recipes to each other's machines).
 */
public record PrismRecipe<R>(@NotNull PrismKey id, @NotNull String owner, @NotNull RecipeType<R> type, @NotNull R value) {

    public PrismRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }
}
