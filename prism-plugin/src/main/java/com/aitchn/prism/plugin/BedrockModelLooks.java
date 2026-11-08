package com.aitchn.prism.plugin;

import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Gives blocks drawn by a display entity (a BetterModel model or a connected shape) a look Bedrock players can see.
 * Display entities do not reach Bedrock, so those players would see an invisible barrier. Instead, each Bedrock player
 * is told (per player, nothing changes in the world) that the block is the note block state of its look, which the
 * Geyser block mappings draw with the block's texture (and, for a connected block, the generated shape of its current
 * connections). Java players keep the barrier carrier and the display entity, so their view is unchanged.
 */
final class BedrockModelLooks implements Listener {

    private final PrismBlockService blocks;
    private final NoteBlockAppearance noteBlocks;
    private final Predicate<UUID> bedrock;
    private final PlatformScheduler scheduler;
    /** The look each known displayed block should show; a queued send is dropped once its entry changed or went. */
    private final Map<Location, BlockData> current = new ConcurrentHashMap<>();

    BedrockModelLooks(PrismBlockService blocks, NoteBlockAppearance noteBlocks, Predicate<UUID> bedrock,
            PlatformScheduler scheduler) {
        this.blocks = blocks;
        this.noteBlocks = noteBlocks;
        this.bedrock = bedrock;
        this.scheduler = scheduler;
    }

    /** The note block state a Bedrock player should see at {@code custom}, or null if it has none. */
    BlockData lookOf(CustomBlock custom) {
        boolean displayed = ModelAnchors.modelOf(custom.definition()).isPresent()
                || custom.definition().components().has(PrismComponents.CONNECTIONS);
        if (!displayed || blocks.drawnAsLook(custom.block())) {
            return null;
        }
        return noteBlocks.existingState(custom.definition(), custom.facing(), blocks.drawnVariant(custom));
    }

    /** A chunk was sent: once it is there, overwrite the displayed blocks in it for a Bedrock player. */
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
                    Location at = custom.block().getLocation();
                    current.put(at, look);
                    looks.put(at, look);
                }
            }
            if (!looks.isEmpty()) {
                scheduler.runOn(player, () -> looks.forEach((at, look) -> send(player, at, look)), null);
            }
        });
    }

    /** A block was placed, removed or changed its variant or connections; call on the block's thread. */
    void changed(Block block) {
        Location at = block.getLocation();
        BlockData look = blocks.get(block).map(this::lookOf).orElse(null);
        if (look == null) {
            current.remove(at); // gone or no longer displayed: queued sends for it are dropped
            return;
        }
        current.put(at, look);
        long chunkKey = Chunk.getChunkKey(at.getBlockX() >> 4, at.getBlockZ() >> 4);
        for (Player player : block.getWorld().getPlayers()) {
            if (bedrock.test(player.getUniqueId())) {
                scheduler.runOn(player, () -> {
                    // Every player who has the chunk, whatever the distance (chunk visibility is horizontal).
                    if (player.getWorld() == at.getWorld() && player.isChunkSent(chunkKey)) {
                        send(player, at, look);
                    }
                }, null);
            }
        }
    }

    /** Sends {@code look} unless the block changed since it was queued (a newer look, or removed). */
    private void send(Player player, Location at, BlockData look) {
        if (look.equals(current.get(at))) {
            player.sendBlockChange(at, look);
        }
    }
}
