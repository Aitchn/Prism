package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.recipe.PrismRecipe;
import com.aitchn.prism.validation.mekanism.logic.Recipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The machine recipes by machine id, in the Bukkit-free form. Rebuilt whenever content (re)loads. */
final class RecipeBook {

    private volatile Map<String, List<Recipe>> byMachine;

    void refresh() {
        Map<String, List<Recipe>> next = new HashMap<>();
        for (PrismRecipe<MachineRecipe> recipe : Prism.recipes().all(Content.machineRecipes())) {
            next.computeIfAbsent(recipe.value().machine(), id -> new ArrayList<>()).add(recipe.value().toLogic());
        }
        next.replaceAll((id, list) -> List.copyOf(list));
        byMachine = next;
    }

    /** The recipes of {@code machine}; reads the recipe service on first use. */
    List<Recipe> forMachine(String machine) {
        if (byMachine == null) {
            refresh();
        }
        return byMachine.getOrDefault(machine, List.of());
    }
}
