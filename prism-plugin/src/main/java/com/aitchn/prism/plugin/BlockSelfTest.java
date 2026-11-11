package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * Optional startup check, enabled with {@code -Dprism.selftest=<block key>}: places the block on the thread that
 * owns it, reads it back (the carrier in the world, the look in the index), removes it and reads again. The smoke test uses it to prove blocks work on Paper, Folia and
 * Purpur. Off by default.
 */
final class BlockSelfTest implements Listener {

    private final String blockKey;
    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;
    private final Logger log;

    BlockSelfTest(String blockKey, PrismBlockService blocks, PlatformScheduler scheduler, Logger log) {
        this.blockKey = blockKey;
        this.blocks = blocks;
        this.scheduler = scheduler;
        this.log = log;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (Bukkit.getWorlds().isEmpty()) {
            log.severe("PRISM_BLOCK_SELFTEST_FAIL no world");
            return;
        }
        PrismKey key = PrismKey.parse(blockKey);
        Location at = Bukkit.getWorlds().get(0).getSpawnLocation();
        scheduler.runAt(at, () -> {
            try {
                boolean placed = blocks.place(key, at);
                boolean identified = placed && blocks.identify(at.getBlock()).filter(key::equals).isPresent();
                // A plain block is stored as the inert carrier its look's tier asks for (glass for one display entity,
                // the mushroom block for a borrowed state, ADR-0025) and shown as something else; the world must hold
                // that carrier and the index the look.
                org.bukkit.Material carrier = at.getBlock().getType();
                com.aitchn.prism.core.block.BlockLook look = blocks.lookOf(at.getBlock());
                Carrier wanted = identified ? blocks.wantedCarrier(at.getBlock()) : null;
                if (identified) {
                    boolean shown = wanted != null && carrier == wanted.material() && look != null
                            && look.look() != look.carrier();
                    log.info((shown ? "PRISM_NATIVE_SELFTEST_OK " : "PRISM_NATIVE_SELFTEST_FAIL ") + key
                            + " carrier=" + carrier + " look=" + look);
                }
                boolean removed = identified && blocks.remove(at.getBlock(), false);
                boolean gone = removed && blocks.identify(at.getBlock()).isEmpty();
                log.info((gone ? "PRISM_BLOCK_SELFTEST_OK " : "PRISM_BLOCK_SELFTEST_FAIL ") + key
                        + " placed=" + placed + " identified=" + identified + " removed=" + removed + " gone=" + gone);
            } catch (RuntimeException e) {
                log.severe("PRISM_BLOCK_SELFTEST_FAIL " + e);
            }
        });
    }
}
