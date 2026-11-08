package com.aitchn.prism.plugin;

import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Gives blocks drawn by a BetterModel model a look Bedrock players can see. Display entities do not reach Bedrock, so
 * those players would see an invisible barrier. Instead, each Bedrock player is told (per player, nothing changes in
 * the world) that the block is the note block state of its look, which the Geyser block mappings draw with the block's
 * texture. Java players keep the barrier carrier and the animated model, so their view is unchanged.
 */
final class BedrockModelLooks implements Listener {

    private final PrismBlockService blocks;
    private final NoteBlockAppearance noteBlocks;
    private final Predicate<UUID> bedrock;
    private final PlatformScheduler scheduler;

    BedrockModelLooks(PrismBlockService blocks, NoteBlockAppearance noteBlocks, Predicate<UUID> bedrock,
            PlatformScheduler scheduler) {
        this.blocks = blocks;
        this.noteBlocks = noteBlocks;
        this.bedrock = bedrock;
        this.scheduler = scheduler;
    }

    /** The note block state a Bedrock player should see at {@code custom}, or null if it has none. */
    BlockData lookOf(CustomBlock custom) {
        if (ModelAnchors.modelOf(custom.definition()).isEmpty()
                || custom.block().getType() == Material.NOTE_BLOCK) {
            return null;
        }
        return noteBlocks.existingState(custom.definition(), custom.facing(), custom.variant());
    }

    /** A chunk was sent: once it is there, overwrite the model blocks in it for a Bedrock player. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        if (!bedrock.test(player.getUniqueId())) {
            return;
        }
        Chunk chunk = event.getChunk();
        Location center = new Location(chunk.getWorld(), (chunk.getX() << 4) + 8, 64, (chunk.getZ() << 4) + 8);
        scheduler.runAt(center, () -> {
            if (!chunk.isLoaded()) {
                return;
            }
            Map<Location, BlockData> looks = new LinkedHashMap<>();
            for (CustomBlock custom : blocks.blocksIn(chunk)) {
                BlockData look = lookOf(custom);
                if (look != null) {
                    looks.put(custom.block().getLocation(), look);
                }
            }
            if (!looks.isEmpty()) {
                scheduler.runOn(player, () -> looks.forEach(player::sendBlockChange), null);
            }
        });
    }

    /** A model block was placed or changed its variant; call on the block's thread. */
    void changed(Block block) {
        BlockData look = blocks.get(block).map(this::lookOf).orElse(null);
        if (look == null) {
            return;
        }
        Location at = block.getLocation();
        double range = (Bukkit.getViewDistance() + 1) * 16.0;
        for (Player player : block.getWorld().getPlayers()) {
            if (bedrock.test(player.getUniqueId())) {
                scheduler.runOn(player, () -> {
                    Location where = player.getLocation();
                    if (where.getWorld() == at.getWorld() && where.distanceSquared(at) <= range * range) {
                        player.sendBlockChange(at, look);
                    }
                }, null);
            }
        }
    }
}
