package com.aitchn.validation.botania;

import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.recipe.RecipeType;
import java.util.function.Supplier;

/** Registers what the YAML in {@code prism/} refers to. Shared by the plugin and by the content test. */
final class BotaniaContent {

    static RecipeType<BotaniaRecipes.Infusion> INFUSION;
    static RecipeType<BotaniaRecipes.Petal> PETAL;
    static RecipeType<BotaniaRecipes.Conversion> PURE_DAISY;

    private BotaniaContent() {}

    static void register(ModContext context, Supplier<BotaniaRuntime> runtime) {
        context.registerBehavior("block_entity", BlockEntityBehavior.class,
                section -> BlockEntityBehavior.fromYaml(runtime, section));
        context.registerBehavior("mana_provider", ManaProvider.class, section -> new ManaProvider(runtime));
        context.registerBehavior("tablet_lore", TabletLore.class, section -> new TabletLore());
        INFUSION = context.registerRecipeType("mana_infusion", BotaniaRecipes.Infusion.class,
                BotaniaRecipes::infusion);
        PETAL = context.registerRecipeType("petal", BotaniaRecipes.Petal.class, BotaniaRecipes::petal);
        PURE_DAISY = context.registerRecipeType("pure_daisy", BotaniaRecipes.Conversion.class,
                BotaniaRecipes::conversion);
    }
}
