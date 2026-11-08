package com.aitchn.prism.api.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-correct scheduling on Paper, Folia and Purpur. Use it instead of the Bukkit scheduler.
 *
 * <p><b>The rule.</b> World state (blocks, entities, per-block data) may only be touched on the thread that owns it:
 * {@link #runAt} for a location, {@link #runOn} for an entity. On Paper that is always the main thread; on Folia each
 * region has its own thread, and two locations far apart are owned by different threads at the same time. Work that
 * spans regions (a network across a base) must keep its shared state thread-safe and hop to the owning thread with
 * {@link #runAt} for every block it touches; never read or write a block of another region directly.
 *
 * <p>Tasks are always queued, never run inline. Delays and periods are in ticks; values below 1 are treated as 1.
 */
public interface PrismScheduler {

    /** Runs on the global tick thread (Folia: global region; Paper: main thread). No region's world state. */
    void runGlobal(@NotNull Runnable task);

    /**
     * Runs {@code task} repeatedly on the global tick thread until cancelled. Use it for server-wide clocks (a network
     * tick, a periodic save); it owns no region, so hop to {@link #runAt} for every block it touches.
     */
    @NotNull PrismTask runGlobalTimer(@NotNull Runnable task, long delayTicks, long periodTicks);

    /** Runs on the thread that owns the region containing {@code location}. */
    void runAt(@NotNull Location location, @NotNull Runnable task);

    /** Runs on the thread that owns {@code entity}; {@code retired} runs instead if the entity was removed first. */
    void runOn(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired);

    /** Runs off the tick threads. Must not touch world state. */
    void runAsync(@NotNull Runnable task);

    @NotNull PrismTask runAtLater(@NotNull Location location, @NotNull Runnable task, long delayTicks);

    @NotNull PrismTask runAtTimer(@NotNull Location location, @NotNull Runnable task, long delayTicks, long periodTicks);

    /**
     * Runs {@code task} repeatedly on the thread that owns {@code entity} (it follows the entity across regions) until
     * cancelled or the entity is removed; {@code retired} then runs once. Use it for per-player work such as a live
     * GUI or a HUD.
     */
    @NotNull PrismTask runOnTimer(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
            long delayTicks, long periodTicks);

    /** Whether the current thread may touch state at {@code location} directly. */
    boolean isOwnedByCurrentThread(@NotNull Location location);

    /** Whether the current thread may touch {@code entity} directly. */
    boolean isOwnedByCurrentThread(@NotNull Entity entity);
}
