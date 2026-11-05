package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockLoadBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A solar generator: charges while the sky above it is bright, offers its energy to cables, shows it on right click. */
public record SolarBehavior() implements BlockPlaceBehavior, BlockLoadBehavior, BlockTickBehavior,
        BlockRemoveBehavior, BlockInteractBehavior, BlockCapabilityProvider {

    static SolarBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of());
        return new SolarBehavior();
    }

    @Override
    public void onPlace(@NotNull BlockContext context) {
        Grid.get().solar(context.custom());
    }

    @Override
    public void onLoad(@NotNull BlockContext context) {
        Grid.get().solar(context.custom());
    }

    @Override
    public void onUnload(@NotNull BlockContext context) {
        Grid.get().unloadSolar(context.custom());
    }

    @Override
    public void onTick(@NotNull BlockContext context) {
        Grid.get().tickSolar(context);
    }

    @Override
    public void onRemove(@NotNull BlockContext context) {
        Grid.get().removeSolar(context.custom());
    }

    @Override
    public void onInteract(@NotNull BlockContext context) {
        if (context.player() == null) {
            return;
        }
        context.cancel();
        Grid grid = Grid.get();
        if (grid.pushTablet(context, grid.solar(context.custom()).energy())) {
            return;
        }
        context.player().sendActionBar(Component.text(grid.describeSolar(context.custom())));
    }

    @Override
    public <T> @Nullable T capability(
            @NotNull CustomBlock block, @Nullable BlockFace side, @NotNull BlockCapability<T> capability) {
        return capability.equals(MekanismCapabilities.ENERGY)
                ? capability.type().cast(Grid.get().solar(block).energy()) : null;
    }
}
