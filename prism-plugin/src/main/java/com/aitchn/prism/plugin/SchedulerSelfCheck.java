package com.aitchn.prism.plugin;

import com.aitchn.prism.core.PrismRuntime;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Exercises global, region and async scheduling once at startup and asserts thread ownership. Cheap, and it turns
 * "the plugin loaded" into "the scheduler abstraction works on this platform" for the smoke test.
 */
final class SchedulerSelfCheck {

    private static final int CHECKS = 3;

    private SchedulerSelfCheck() {}

    static void run(Plugin plugin, PrismRuntime runtime, Runnable onSuccess) {
        Logger log = plugin.getLogger();
        PlatformScheduler scheduler = runtime.scheduler();
        AtomicInteger passed = new AtomicInteger();
        Runnable pass = () -> {
            if (passed.incrementAndGet() == CHECKS) {
                log.info("PRISM_SCHEDULER_OK");
                onSuccess.run();
            }
        };

        scheduler.runGlobal(pass);
        scheduler.runAsync(pass);

        // Worlds are loaded by the time a plugin enables with load: POSTWORLD (the default).
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            log.severe("PRISM_SCHEDULER_FAIL no world loaded for the region check");
            return;
        }
        Location spawn = world.getSpawnLocation();
        scheduler.runAt(spawn, () -> {
            if (scheduler.isOwnedByCurrentThread(spawn)) {
                pass.run();
            } else {
                log.severe("PRISM_SCHEDULER_FAIL runAt executed on a thread that does not own the location");
            }
        });
    }
}
