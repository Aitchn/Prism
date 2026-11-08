package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * Optional startup check of connected blocks, enabled with {@code -Dprism.selftest.connections=<cable>,<other>}; the
 * smoke test passes {@code examplemod:ruby_cable,examplemod:ruby_counter}. {@code cable} must connect to itself and to
 * {@code other}. Places two cables side by side and {@code other} north of the first, then removes the second, and
 * checks each step's connections, the display entity's model and the Bedrock look.
 */
final class ConnectionSelfTest implements Listener {

    private final PrismKey cable;
    private final PrismKey other;
    private final PrismBlockService blocks;
    private final Function<CustomBlock, BlockData> bedrockLook;
    private final PlatformScheduler scheduler;
    private final Logger log;

    ConnectionSelfTest(String spec, PrismBlockService blocks, Function<CustomBlock, BlockData> bedrockLook,
            PlatformScheduler scheduler, Logger log) {
        String[] parts = spec.split(",");
        this.cable = PrismKey.parse(parts[0]);
        this.other = PrismKey.parse(parts.length > 1 ? parts[1] : parts[0]);
        this.blocks = blocks;
        this.bedrockLook = bedrockLook;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            log.severe("PRISM_CONNECTIONS_SELFTEST_FAIL no world");
            return;
        }
        Location spawn = world.getSpawnLocation();
        Location at = new Location(world, spawn.getBlockX() + 6, world.getMaxHeight() - 20, spawn.getBlockZ() + 6);
        scheduler.runAtLater(at, () -> run(at.getBlock()), 40);
    }

    private void run(Block a) {
        Block b = a.getRelative(BlockFace.EAST);
        Block c = a.getRelative(BlockFace.NORTH);
        try {
            boolean placed = blocks.place(cable, a.getLocation()) && blocks.place(cable, b.getLocation())
                    && blocks.place(other, c.getLocation());
            String joined = drawn(a) + "/" + drawn(b);
            // a: north (other) and east (b); b: west (a).
            boolean masks = joined.equals("c3/c8");
            boolean model = modelShown(a, "c3");
            boolean bedrock = blocks.get(a).map(bedrockLook).isPresent() && blocks.get(b).map(bedrockLook).isPresent();
            boolean carrier = !blocks.drawnAsLook(a);
            blocks.remove(b, false);
            String after = drawn(a);
            boolean unlinked = after.equals("c1");
            blocks.remove(a, false);
            blocks.remove(c, false);
            boolean ok = placed && masks && model && bedrock && carrier && unlinked;
            log.info((ok ? "PRISM_CONNECTIONS_SELFTEST_OK " : "PRISM_CONNECTIONS_SELFTEST_FAIL ") + cable
                    + " placed=" + placed + " looks=" + joined + " model=" + model + " bedrock=" + bedrock
                    + " display=" + carrier + " afterRemove=" + after);
        } catch (RuntimeException e) {
            log.severe("PRISM_CONNECTIONS_SELFTEST_FAIL " + e);
        }
    }

    private String drawn(Block block) {
        return blocks.get(block).map(blocks::drawnVariant).orElse("none");
    }

    /** Whether the display entity over {@code block} shows the model of the {@code drawn} look. */
    private boolean modelShown(Block block, String drawn) {
        NamespacedKey expected = new NamespacedKey(cable.namespace(), "block/" + cable.value() + "_" + drawn);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemDisplay display : block.getWorld().getNearbyEntitiesByType(ItemDisplay.class, center, 0.3)) {
            var meta = display.getItemStack().getItemMeta();
            if (meta != null && expected.equals(meta.getItemModel())) {
                return true;
            }
        }
        return false;
    }
}
