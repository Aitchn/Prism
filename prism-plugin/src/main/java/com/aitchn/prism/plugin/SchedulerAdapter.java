package com.aitchn.prism.plugin;

import com.aitchn.prism.api.scheduler.PrismScheduler;
import com.aitchn.prism.api.scheduler.PrismTask;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The public scheduler: a thin view of Prism's internal platform scheduler. */
final class SchedulerAdapter implements PrismScheduler {

    private final PlatformScheduler platform;

    SchedulerAdapter(PlatformScheduler platform) {
        this.platform = platform;
    }

    @Override
    public void runGlobal(@NotNull Runnable task) {
        platform.runGlobal(task);
    }

    @Override
    public void runAt(@NotNull Location location, @NotNull Runnable task) {
        platform.runAt(location, task);
    }

    @Override
    public void runOn(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        platform.runOn(entity, task, retired);
    }

    @Override
    public void runAsync(@NotNull Runnable task) {
        platform.runAsync(task);
    }

    @Override
    public @NotNull PrismTask runGlobalTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        return wrap(platform.runGlobalTimer(task, delayTicks, periodTicks));
    }

    @Override
    public @NotNull PrismTask runAtLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return wrap(platform.runAtLater(location, task, delayTicks));
    }

    @Override
    public @NotNull PrismTask runAtTimer(
            @NotNull Location location, @NotNull Runnable task, long delayTicks, long periodTicks) {
        return wrap(platform.runAtTimer(location, task, delayTicks, periodTicks));
    }

    @Override
    public @NotNull PrismTask runOnTimer(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
            long delayTicks, long periodTicks) {
        return wrap(platform.runOnTimer(entity, task, retired, delayTicks, periodTicks));
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Location location) {
        return platform.isOwnedByCurrentThread(location);
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Entity entity) {
        return platform.isOwnedByCurrentThread(entity);
    }

    private static PrismTask wrap(ScheduledTask task) {
        return new PrismTask() {
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
