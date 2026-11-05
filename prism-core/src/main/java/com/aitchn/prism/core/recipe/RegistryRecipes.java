package com.aitchn.prism.core.recipe;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.recipe.PrismRecipe;
import com.aitchn.prism.api.recipe.RecipeService;
import com.aitchn.prism.api.recipe.RecipeType;
import com.aitchn.prism.api.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/** {@link RecipeService} over the mod manager's live registries. */
public final class RegistryRecipes implements RecipeService {

    private final Registry<RecipeType<?>> types;
    private final Registry<PrismRecipe<?>> recipes;

    public RegistryRecipes(@NotNull Registry<RecipeType<?>> types, @NotNull Registry<PrismRecipe<?>> recipes) {
        this.types = types;
        this.recipes = recipes;
    }

    @Override
    public <R> @NotNull List<PrismRecipe<R>> all(@NotNull RecipeType<R> type) {
        List<PrismRecipe<R>> matching = new ArrayList<>();
        for (PrismKey id : recipes.keys()) {
            recipes.get(id).flatMap(recipe -> cast(recipe, type)).ifPresent(matching::add);
        }
        return matching;
    }

    @Override
    public <R> @NotNull Optional<PrismRecipe<R>> get(@NotNull RecipeType<R> type, @NotNull PrismKey id) {
        return recipes.get(id).flatMap(recipe -> cast(recipe, type));
    }

    @Override
    public @NotNull Optional<RecipeType<?>> type(@NotNull PrismKey key) {
        return types.get(key);
    }

    @SuppressWarnings("unchecked")
    private static <R> Optional<PrismRecipe<R>> cast(PrismRecipe<?> recipe, RecipeType<R> type) {
        // Types are compared by key: a reloaded mod registers a new RecipeType object with the same key.
        return recipe.type().key().equals(type.key()) && type.valueType().isInstance(recipe.value())
                ? Optional.of((PrismRecipe<R>) recipe) : Optional.empty();
    }
}
