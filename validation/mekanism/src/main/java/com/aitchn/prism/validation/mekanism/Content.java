package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.recipe.RecipeType;

/** Everything the mod registers before its YAML is read. Shared by the plugin and the tests. */
final class Content {

    private static volatile RecipeType<MachineRecipe> machineRecipes;

    private Content() {}

    static RecipeType<MachineRecipe> machineRecipes() {
        return machineRecipes;
    }

    static void register(ModContext context) {
        context.registerBehavior("machine", MachineBehavior.class, MachineBehavior::fromYaml);
        context.registerBehavior("solar", SolarBehavior.class, SolarBehavior::fromYaml);
        context.registerBehavior("cable", CableBehavior.class, CableBehavior::fromYaml);
        context.registerBehavior("pipe", PipeBehavior.class, PipeBehavior::fromYaml);
        context.registerBehavior("transporter", TransporterBehavior.class, TransporterBehavior::fromYaml);
        context.registerBehavior("tablet", TabletBehavior.class, TabletBehavior::fromYaml);
        machineRecipes = context.registerRecipeType("machine", MachineRecipe.class, MachineRecipe::decode);
    }
}
