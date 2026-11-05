package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.mod.ContentView;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.api.recipe.PrismRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Validation mod: Mekanism's core mechanics (energy, machines, the osmium ore chain, gas pipes, a portable battery) as
 * a plain plugin on Prism. Prism supplies blocks, their data, ticking, capability lookup, menus, item data and recipe
 * loading; everything Mekanism-shaped (joules, chemicals, machine recipes) is in this plugin.
 */
public final class MekanismMod extends JavaPlugin implements PrismMod {

    @Override
    public void onInitialize(@NotNull ModContext context) {
        Content.register(context);
    }

    @Override
    public void onContentLoaded(@NotNull ContentView view) {
        Grid.get().recipes().refresh();
        var recipes = com.aitchn.prism.api.Prism.recipes().all(Content.machineRecipes());
        long machines = recipes.stream().map(PrismRecipe::value).map(MachineRecipe::machine).distinct().count();
        getLogger().info((recipes.isEmpty() ? "MEKANISM_RECIPES_FAIL" : "MEKANISM_RECIPES_OK") + " machines=" + machines
                + " recipes=" + recipes.size());
    }

    @Override
    public void onEnable() {
        Grid.get().start(this);
        if (Boolean.getBoolean("mekanism.selftest")) {
            getServer().getPluginManager().registerEvents(new SelfTest(this), this);
        }
    }

    @Override
    public void onDisable() {
        Grid.get().stop();
    }
}
