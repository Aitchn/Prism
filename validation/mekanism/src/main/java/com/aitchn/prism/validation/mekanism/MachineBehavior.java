package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockDropBehavior;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockLoadBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.validation.mekanism.logic.Machine;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A recipe machine. Its state lives in {@link Grid} while loaded and in the block's per-block data otherwise; its
 * recipes are {@code mekanism:machine} files naming {@link #machine()}.
 *
 * <pre>
 * behaviors:
 *   - type: mekanism:machine
 *     machine: enrichment_chamber
 *     energy_capacity: 20000     # joules
 *     max_receive: 2000          # joules per tick a cable may push in
 *     tank_capacity: 0           # millibuckets per tank; 0 for machines without chemicals
 *     buffer: ""                 # "any" or "water": a storage tank that takes and gives back that much
 *     max_extract: 0             # joules per tick it gives to cables; above 0 makes it a battery (energy cube)
 * </pre>
 */
public record MachineBehavior(String machine, int energyCapacity, int maxReceive, int tankCapacity,
        int maxExtract, String buffer)
        implements BlockPlaceBehavior, BlockLoadBehavior, BlockTickBehavior, BlockRemoveBehavior,
        BlockInteractBehavior, BlockCapabilityProvider, BlockDropBehavior {

    static MachineBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of("machine", "energy_capacity", "max_receive", "tank_capacity", "max_extract", "buffer"));
        MachineBehavior behavior = new MachineBehavior(section.requireString("machine"),
                section.getInt("energy_capacity").orElse(20_000), section.getInt("max_receive").orElse(2_000),
                section.getInt("tank_capacity").orElse(0), section.getInt("max_extract").orElse(0),
                section.getString("buffer").orElse(""));
        if (!Set.of("", "any", "water").contains(behavior.buffer)) {
            throw section.error("buffer must be any or water");
        }
        if (behavior.energyCapacity < 1 || behavior.maxReceive < 1 || behavior.tankCapacity < 0
                || behavior.maxExtract < 0) {
            throw section.error("energy_capacity and max_receive must be at least 1, tank_capacity and max_extract at least 0");
        }
        return behavior;
    }

    /** Storage tanks take what this allows; null for ordinary machines. */
    java.util.function.Predicate<com.aitchn.prism.validation.mekanism.logic.Chemical> bufferFilter() {
        return switch (buffer) {
            case "any" -> c -> true;
            case "water" -> com.aitchn.prism.validation.mekanism.logic.Chemical.WATER::equals;
            default -> null;
        };
    }

    @Override
    public void onPlace(@NotNull BlockContext context) {
        Grid.restoreFromItem(context);
        Grid.get().machine(context.custom(), this);
    }

    @Override
    public void onDrop(@NotNull BlockContext context, @NotNull org.bukkit.inventory.ItemStack drop) {
        Grid.get().storeSettings(Grid.get().machine(context.custom(), this), drop);
    }

    @Override
    public void onLoad(@NotNull BlockContext context) {
        Grid.get().machine(context.custom(), this);
    }

    @Override
    public void onUnload(@NotNull BlockContext context) {
        Grid.get().unloadMachine(context.custom(), this);
    }

    @Override
    public void onTick(@NotNull BlockContext context) {
        Grid.get().tickMachine(context, this);
    }

    @Override
    public void onRemove(@NotNull BlockContext context) {
        Grid.get().removeMachine(context.custom());
    }

    @Override
    public void onInteract(@NotNull BlockContext context) {
        if (context.player() == null) {
            return;
        }
        context.cancel();
        Grid grid = Grid.get();
        Machine machine = grid.machine(context.custom(), this);
        if (grid.configure(context, machine) || grid.pushTablet(context, machine.energy()) || grid.useBucket(context, machine.tankFor(com.aitchn.prism.validation.mekanism.logic.Chemical.WATER))
                || grid.installUpgrade(context, machine)) {
            return;
        }
        if (!grid.openMenu(context.player(), Grid.pos(context.block()), machine, this.machine)) {
            context.player().sendActionBar(Component.text("Someone else is using this machine"));
        }
    }

    @Override
    public <T> @Nullable T capability(
            @NotNull CustomBlock block, @Nullable BlockFace side, @NotNull BlockCapability<T> capability) {
        if (capability.equals(MekanismCapabilities.ENERGY)) {
            return capability.type().cast(Grid.get().machine(block, this).energy());
        }
        if (capability.equals(MekanismCapabilities.CHEMICAL) && tankCapacity > 0) {
            return capability.type().cast(Grid.get().portsOf(Grid.get().machine(block, this)));
        }
        return null;
    }
}
