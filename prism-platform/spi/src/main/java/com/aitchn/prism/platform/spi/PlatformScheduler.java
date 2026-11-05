package com.aitchn.prism.platform.spi;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The only way Prism code may touch threads. Everything that reads or writes world, block or entity state must run
 * through one of the {@code run*} methods that names the state it owns.
 *
 * <p>Tasks are always enqueued, never run inline, on every platform, so call-site behaviour does not differ between
 * Paper and Folia. Delays and periods are in ticks; values below 1 are treated as 1.
 */
public interface PlatformScheduler {

    /** Runs on the global tick thread (Folia: global region; Paper: main thread). No world state of a region. */
    void runGlobal(@NotNull Runnable task);

    /** Runs on the thread that owns the region containing {@code location}. */
    void runAt(@NotNull Location location, @NotNull Runnable task);

    /**
     * Runs on the thread that owns {@code entity}.
     *
     * @param retired invoked instead of {@code task} if the entity was removed before the task could run; may be null
     */
    void runOn(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired);

    /** Runs {@code task} repeatedly on the global tick thread until cancelled. */
    @NotNull ScheduledTask runGlobalTimer(@NotNull Runnable task, long delayTicks, long periodTicks);

    /** Runs off the tick threads. Must not touch world state. */
    void runAsync(@NotNull Runnable task);

    @NotNull ScheduledTask runAtLater(@NotNull Location location, @NotNull Runnable task, long delayTicks);

    @NotNull ScheduledTask runAtTimer(
            @NotNull Location location, @NotNull Runnable task, long delayTicks, long periodTicks);

    /**
     * Runs {@code task} repeatedly on the thread that owns {@code entity}, following it across regions, until
     * cancelled or the entity is removed; {@code retired} then runs once (may be null).
     */
    @NotNull ScheduledTask runOnTimer(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
            long delayTicks, long periodTicks);

    /** Whether the current thread may access state at {@code location} directly. Always the main thread on Paper. */
    boolean isOwnedByCurrentThread(@NotNull Location location);

    /** Whether the current thread may access {@code entity} directly. Always the main thread on Paper. */
    boolean isOwnedByCurrentThread(@NotNull Entity entity);
}
