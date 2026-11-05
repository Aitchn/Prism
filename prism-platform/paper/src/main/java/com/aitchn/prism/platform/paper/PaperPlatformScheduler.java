package com.aitchn.prism.platform.paper;

import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@link PlatformScheduler} for Paper and Purpur: every world-touching task runs on the main thread. */
public final class PaperPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    public PaperPlatformScheduler(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void runGlobal(@NotNull Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runAt(@NotNull Location location, @NotNull Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runOn(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid()) {
                task.run();
            } else if (retired != null) {
                retired.run();
            }
        });
    }

    @Override
    public void runAsync(@NotNull Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public @NotNull ScheduledTask runAtLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, delayTicks)));
    }

    @Override
    public @NotNull ScheduledTask runAtTimer(
            @NotNull Location location, @NotNull Runnable task, long delayTicks, long periodTicks) {
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1, delayTicks), Math.max(1, periodTicks)));
    }

    @Override
    public @NotNull ScheduledTask runOnTimer(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
            long delayTicks, long periodTicks) {
        BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (entity.isValid()) {
                task.run();
            } else {
                self[0].cancel();
                if (retired != null) {
                    retired.run();
                }
            }
        }, Math.max(1, delayTicks), Math.max(1, periodTicks));
        return wrap(self[0]);
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Location location) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Entity entity) {
        return Bukkit.isPrimaryThread();
    }

    private static ScheduledTask wrap(BukkitTask task) {
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
