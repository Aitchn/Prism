package com.aitchn.prism.platform.folia;

import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PlatformScheduler} for Folia, built on the global, region, entity and async schedulers. This module is the
 * only place in Prism allowed to talk about regionized threading.
 */
public final class FoliaPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    public FoliaPlatformScheduler(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void runGlobal(@NotNull Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }

    @Override
    public void runAt(@NotNull Location location, @NotNull Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
    }

    @Override
    public void runOn(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        entity.getScheduler().run(plugin, t -> task.run(), retired);
    }

    @Override
    public void runAsync(@NotNull Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    @Override
    public @NotNull ScheduledTask runAtLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return wrap(Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), Math.max(1, delayTicks)));
    }

    @Override
    public @NotNull ScheduledTask runAtTimer(
            @NotNull Location location, @NotNull Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getRegionScheduler()
                .runAtFixedRate(plugin, location, t -> task.run(), Math.max(1, delayTicks), Math.max(1, periodTicks)));
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Location location) {
        return Bukkit.isOwnedByCurrentRegion(location);
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    private static ScheduledTask wrap(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
        return new ScheduledTask() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }
}
