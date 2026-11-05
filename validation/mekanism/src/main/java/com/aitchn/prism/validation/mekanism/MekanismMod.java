package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Validation mod: Mekanism's core mechanics (energy, machines, ore chain, gas pipes, multiblock casing) as a plain
 * plugin on Prism. Everything Prism could not express is listed in docs/gaps.md under "Found in Mekanism".
 */
public final class MekanismMod extends JavaPlugin implements PrismMod {

    private static MekanismMod instance;
    private final MekanismWorld world = new MekanismWorld(this);

    public MekanismMod() {
        instance = this;
    }

    static MekanismMod instance() {
        return instance;
    }

    MekanismWorld world() {
        return world;
    }

    @Override
    public void onInitialize(@NotNull ModContext context) {
        context.registerBehavior("block", MekanismBlock.class, MekanismBlock::fromYaml);
    }

    @Override
    public void onEnable() {
        world.start();
    }

    @Override
    public void onDisable() {
        world.stop();
    }
}
