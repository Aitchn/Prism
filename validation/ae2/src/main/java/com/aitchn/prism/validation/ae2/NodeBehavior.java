package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockLoadBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import com.aitchn.prism.validation.ae2.runtime.DriveState;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * The single behavior every ME block uses. Prism calls it when the block is placed or its chunk loads (join the grid),
 * when its chunk unloads or it is removed (leave the grid and save), and on right click.
 */
record NodeBehavior(@NotNull NodeKind kind, @NotNull Ae2Services services)
        implements BlockPlaceBehavior, BlockRemoveBehavior, BlockLoadBehavior, BlockInteractBehavior {

    static NodeBehavior fromYaml(ConfigSection section, Ae2Services services) throws ContentException {
        section.rejectUnknownKeys(Set.of("kind"));
        String raw = section.requireString("kind");
        try {
            return new NodeBehavior(NodeKind.fromId(raw), services);
        } catch (IllegalArgumentException e) {
            throw section.error("unknown ME node kind '" + raw + "'");
        }
    }

    static GridPos pos(Location location) {
        return new GridPos(location.getWorld().getName(), location.getBlockX(), location.getBlockY(),
                location.getBlockZ());
    }

    @Override
    public void onPlace(@NotNull BlockContext context) {
        attach(context.custom());
    }

    @Override
    public void onLoad(@NotNull BlockContext context) {
        attach(context.custom());
    }

    @Override
    public void onUnload(@NotNull BlockContext context) {
        CustomBlock block = context.custom();
        GridPos pos = pos(block.block().getLocation());
        if (kind == NodeKind.DRIVE) {
            services.grid.drive(pos).ifPresent(drive -> drive.write(block.data()));
        } else if (kind == NodeKind.ENERGY_CELL) {
            block.data().set(Ae2Content.ENERGY, PersistentDataType.DOUBLE, services.grid.energyShare(pos, true));
        }
        services.grid.detach(pos);
    }

    @Override
    public void onRemove(@NotNull BlockContext context) {
        CustomBlock block = context.custom();
        Location at = block.block().getLocation();
        GridPos pos = pos(at);
        Location center = at.clone().add(0.5, 0.5, 0.5);
        if (kind == NodeKind.DRIVE) {
            services.grid.drive(pos).ifPresent(drive -> {
                for (int slot = 0; slot < DriveState.SLOTS; slot++) {
                    drive.eject(slot).ifPresent(stack -> at.getWorld().dropItemNaturally(center, stack));
                }
            });
        } else if (kind == NodeKind.PATTERN_PROVIDER) {
            // An open menu hands the stored patterns to its viewer when it closes; otherwise they drop here.
            if (!ProviderMenu.blockRemoved(at)) {
                ProviderMenu.storedStacks(block.data()).forEach(stack -> at.getWorld().dropItemNaturally(center, stack));
            }
        } else if (kind == NodeKind.ENERGY_CELL) {
            services.grid.energyShare(pos, true);
        }
        services.grid.detach(pos);
    }

    @Override
    public void onInteract(@NotNull BlockContext context) {
        Player player = context.player();
        if (player == null || player.isSneaking()) {
            return;
        }
        context.cancel();
        services.ui.interact(player, context.custom(), kind);
    }

    private void attach(CustomBlock block) {
        GridPos pos = pos(block.block().getLocation());
        if (services.grid.contains(pos)) {
            return;
        }
        DriveState drive = kind == NodeKind.DRIVE ? DriveState.read(block.data()) : null;
        List<Pattern> patterns = kind == NodeKind.PATTERN_PROVIDER ? ProviderMenu.readPatterns(block.data()) : List.of();
        double energy = kind == NodeKind.ENERGY_CELL
                ? block.data().getOrDefault(Ae2Content.ENERGY, PersistentDataType.DOUBLE, 0.0) : 0;
        services.grid.attach(pos, kind, drive, patterns, energy);
    }
}
