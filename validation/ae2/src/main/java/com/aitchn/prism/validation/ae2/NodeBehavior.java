package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** The single behavior every ME block uses: joins the grid on place, leaves it on removal, reacts to clicks. */
record NodeBehavior(@NotNull NodeKind kind, @NotNull Ae2Services services)
        implements BlockPlaceBehavior, BlockRemoveBehavior, BlockInteractBehavior {

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
        services.grid.add(pos(context.block().getLocation()), kind);
    }

    @Override
    public void onRemove(@NotNull BlockContext context) {
        Location at = context.block().getLocation();
        GridPos pos = pos(at);
        switch (kind) {
            case DRIVE -> services.ui.dropDriveContents(pos, at);
            case PATTERN_PROVIDER -> services.ui.dropProviderContents(pos, at);
            default -> { }
        }
        services.grid.remove(pos);
    }

    @Override
    public void onInteract(@NotNull BlockContext context) {
        Player player = context.player();
        if (player == null || player.isSneaking()) {
            return;
        }
        GridPos pos = pos(context.block().getLocation());
        context.cancel();
        services.ui.interact(player, pos, kind);
    }
}
