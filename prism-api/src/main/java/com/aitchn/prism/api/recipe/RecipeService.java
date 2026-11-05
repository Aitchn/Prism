package com.aitchn.prism.api.recipe;

import com.aitchn.prism.api.PrismKey;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * The recipes of every active mod, by type. Recipes are data in each mod's jar ({@code prism/recipes/**.yml}); any
 * mod may add recipes of another mod's type. The lists follow mod reloads, so look them up when needed instead of
 * keeping a copy forever.
 */
public interface RecipeService {

    /** Every recipe of {@code type}, in load order. */
    <R> @NotNull List<PrismRecipe<R>> all(@NotNull RecipeType<R> type);

    /** The recipe {@code id} if it exists and has {@code type}. */
    <R> @NotNull Optional<PrismRecipe<R>> get(@NotNull RecipeType<R> type, @NotNull PrismKey id);

    /** The registered recipe type {@code key}, for mods that read another mod's recipes. */
    @NotNull Optional<RecipeType<?>> type(@NotNull PrismKey key);
}
