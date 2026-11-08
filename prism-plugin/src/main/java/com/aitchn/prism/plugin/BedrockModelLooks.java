package com.aitchn.prism.plugin;

import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.client.LookSender;
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
    private final java.util.function.Supplier<ClientLooks> clientLooks;
    private final Predicate<UUID> bedrock;
    private final PlatformScheduler scheduler;
    /** The look each known displayed block should show; a queued send is dropped once its entry changed or went. */
    private final Map<Location, Shown> current = new ConcurrentHashMap<>();

    BedrockModelLooks(PrismBlockService blocks, java.util.function.Supplier<ClientLooks> clientLooks, Predicate<UUID> bedrock,
            PlatformScheduler scheduler) {
        this.blocks = blocks;
        this.clientLooks = clientLooks;
        this.bedrock = bedrock;
        this.scheduler = scheduler;
    }

    /**
     * What a Bedrock player is sent at a displayed block: the synthetic block state registered for its look
     * ({@code synthetic}, ADR-0024), or else the legacy note block state ({@code legacy}).
     */
    private record Shown(BlockData legacy, int synthetic) {}

    /** What a Bedrock player should see at {@code custom}, or null if it has nothing but the barrier. */
    private Shown shownOf(CustomBlock custom) {
        if (blocks.drawnAsLook(custom.block())) {
            return null;
        }
        String id = new com.aitchn.prism.core.block.Appearances.Look(custom.key(), blocks.drawnVariant(custom),
                custom.facing().name()).id();
        int synthetic = clientLooks.get().bedrockId(id);
        if (synthetic != com.aitchn.prism.core.block.BlockLook.NO_BEDROCK) {
            return new Shown(null, synthetic);
        }
        BlockData legacy = lookOf(custom);
        return legacy == null ? null : new Shown(legacy, com.aitchn.prism.core.block.BlockLook.NO_BEDROCK);
    }

    /** The note block state a Bedrock player should see at {@code custom}, or null if it has none. */
    BlockData lookOf(CustomBlock custom) {
        boolean displayed = ModelAnchors.modelOf(custom.definition()).isPresent()
                || custom.definition().components().has(PrismComponents.CONNECTIONS);
        if (!displayed || blocks.drawnAsLook(custom.block())) {
            return null;
        }
        return clientLooks.get().bedrockData(new com.aitchn.prism.core.block.Appearances.Look(custom.key(), blocks.drawnVariant(custom),
                custom.facing().name()).id());
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
            Map<Location, Shown> looks = new LinkedHashMap<>();
            for (CustomBlock custom : blocks.blocksIn(chunk)) {
                Shown look = shownOf(custom);
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

    /** The looks of a loaded chunk were recomputed (content changed, or synthetic ids became active): resend them. */
    void relooked(BlockIndex.Loaded mirror) {
        for (CustomBlock custom : blocks.blocksIn(mirror.world.getChunkAt(mirror.chunkX, mirror.chunkZ))) {
            changed(custom.block());
        }
    }

    /** A block was placed, removed or changed its variant or connections; call on the block's thread. */
    void changed(Block block) {
        Location at = block.getLocation();
        Shown look = blocks.get(block).map(this::shownOf).orElse(null);
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
    private void send(Player player, Location at, Shown look) {
        if (look.equals(current.get(at))) {
            if (look.synthetic() != com.aitchn.prism.core.block.BlockLook.NO_BEDROCK) {
                LookSender.send(player, at.getBlockX(), at.getBlockY(), at.getBlockZ(), look.synthetic());
            } else {
                player.sendBlockChange(at, look.legacy());
            }
        }
    }
}
