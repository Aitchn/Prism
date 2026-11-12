package com.aitchn.prism.plugin;

import com.aitchn.prism.api.effect.EffectService;
import com.aitchn.prism.api.effect.ParticleSpec;
import com.aitchn.prism.api.effect.Travel;
import com.aitchn.prism.api.effect.TravelHandle;
import com.aitchn.prism.client.DisplayEntities;
import com.aitchn.prism.core.effect.TravelCap;
import com.aitchn.prism.core.effect.TravelPath;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EffectService}: travelling visuals, bursts and beams (ADR-0027). Nothing exists on the server.
 *
 * <p>A travelling visual is shown to the players within {@code effects.view-range} of its path who have the chunk it is
 * in when it starts. A Java player gets one packet-only item display, spawned where the travel is and moved by the
 * client: one move every {@link TravelPath#MAX_STEP} ticks at most, one removal at the end. Its trail is sent each tick
 * to the players then in range. A Bedrock player gets the trail only. One clock runs on the global thread every tick,
 * and only while a travel is active; the arrival callback is a separate task on the destination's region.
 */
final class WorldEffects implements EffectService, Listener {

    /** The most points one beam sends. */
    static final int MAX_BEAM_POINTS = 512;

    private final PlatformScheduler scheduler;
    private final EffectViewers viewers;
    private final EffectParticles particles;
    private final Logger log;
    private final double viewRange;
    private final int maxPerPlayer;
    private final Set<Active> active = ConcurrentHashMap.newKeySet();
    /** Per player, the travels it is shown now. */
    private final Map<UUID, Set<Active>> shown = new ConcurrentHashMap<>();
    private volatile int now;
    private ScheduledTask clock;

    /**
     * @param viewRange    how far from an effect a player still gets it, in blocks ({@code effects.view-range})
     * @param maxPerPlayer the most travelling visuals one player is shown at once ({@code effects.travel.max-per-player})
     */
    WorldEffects(PlatformScheduler scheduler, EffectViewers viewers, EffectParticles particles, Logger log,
            double viewRange, int maxPerPlayer) {
        this.scheduler = scheduler;
        this.viewers = viewers;
        this.particles = particles;
        this.log = log;
        this.viewRange = viewRange;
        this.maxPerPlayer = Math.max(0, maxPerPlayer);
    }

    /** One travel in flight. */
    private final class Active implements TravelHandle {
        final Travel spec;
        final TravelPath path;
        final UUID world;
        final int start;
        final @Nullable String model;
        final @Nullable EffectParticles.Resolved trail;
        final Map<UUID, Seen> seen = new ConcurrentHashMap<>();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean finished = new AtomicBoolean();
        volatile boolean ended;
        volatile @Nullable ScheduledTask arrival;

        Active(Travel spec, int start) {
            this.spec = spec;
            Location from = spec.from();
            Location to = spec.to();
            this.path = new TravelPath(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(),
                    spec.ticks());
            this.world = Objects.requireNonNull(from.getWorld()).getUID();
            this.start = start;
            this.model = spec.model().map(Object::toString).orElse(null);
            this.trail = spec.trail().flatMap(particles::resolve).orElse(null);
        }

        int elapsed() {
            return now - start;
        }

        double[] position() {
            return path.at(elapsed());
        }

        @Override
        public boolean cancel() {
            if (finished.get() || !cancelled.compareAndSet(false, true)) {
                return false;
            }
            ScheduledTask pending = arrival;
            if (pending != null) {
                pending.cancel();
            }
            finished.set(true);
            return true;
        }

        @Override
        public boolean done() {
            return finished.get();
        }
    }

    /** What one player was given of a travel. */
    private record Seen(Player player, int entity, int[] nextMove) {}

    /** The trail points of one travel for one player in one tick. */
    private record TrailSend(EffectParticles.Resolved trail, double[] points) {}

    // ---------------------------------------------------------------- launch

    @Override
    public @NotNull TravelHandle launch(@NotNull Travel travel) {
        Objects.requireNonNull(travel, "travel");
        Active travelling = new Active(travel, now);
        Runnable callback = travel.onArrive();
        if (callback != null) {
            travelling.arrival = scheduler.runAtLater(travel.to(), () -> {
                if (travelling.cancelled.get()) {
                    return;
                }
                travelling.finished.set(true);
                try {
                    callback.run();
                } catch (RuntimeException e) {
                    log.log(Level.SEVERE, "A travelling visual's arrival callback failed", e);
                }
            }, travel.ticks());
        }
        active.add(travelling);
        ensureClock();
        if (travelling.model == null && travelling.trail == null) {
            return travelling; // nothing to show: only the callback
        }
        double rangeSq = viewRange * viewRange;
        for (EffectViewers.Viewer viewer : viewers.in(travelling.world)) {
            if (travelling.path.distanceSq(viewer.x, viewer.y, viewer.z) > rangeSq
                    || viewer.bedrock && travelling.trail == null) {
                continue;
            }
            Player player = viewer.player;
            scheduler.runOn(player, () -> attach(travelling, player, viewer.bedrock), null);
        }
        return travelling;
    }

    /** On the player's thread: shows the travel to the player if it has the chunk, within its cap. */
    private void attach(Active travel, Player player, boolean bedrock) {
        if (travel.ended || !player.isOnline() || !player.getWorld().getUID().equals(travel.world)) {
            return;
        }
        int elapsed = travel.elapsed();
        double[] at = travel.path.at(elapsed);
        if (!player.isChunkSent(Chunk.getChunkKey((int) Math.floor(at[0]) >> 4, (int) Math.floor(at[2]) >> 4))) {
            return;
        }
        Set<Active> mine = shown.computeIfAbsent(player.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
        DisplayEntities.Batch batch = new DisplayEntities.Batch();
        synchronized (mine) {
            mine.removeIf(other -> other.ended);
            if (mine.size() >= maxPerPlayer) {
                List<Active> held = new ArrayList<>(mine);
                held.add(travel);
                Location eye = player.getEyeLocation();
                double[] distances = new double[held.size()];
                for (int i = 0; i < distances.length; i++) {
                    double[] p = held.get(i).position();
                    double dx = p[0] - eye.getX();
                    double dy = p[1] - eye.getY();
                    double dz = p[2] - eye.getZ();
                    distances[i] = dx * dx + dy * dy + dz * dz;
                }
                boolean keepNew = true;
                for (int drop : TravelCap.drop(distances, maxPerPlayer)) {
                    Active dropped = held.get(drop);
                    if (dropped == travel) {
                        keepNew = false;
                        continue;
                    }
                    mine.remove(dropped);
                    Seen gone = dropped.seen.remove(player.getUniqueId());
                    if (gone != null && gone.entity() != 0) {
                        batch.destroy(gone.entity());
                    }
                }
                if (!keepNew) {
                    batch.send(player);
                    return;
                }
            }
            int entity = 0;
            if (travel.model != null && !bedrock) {
                entity = DisplayEntities.nextId();
                batch.spawnMoving(entity, at[0], at[1], at[2], travel.model, travel.spec.scale(),
                        travel.spec.billboard().ordinal(), travel.path.moveDuration(elapsed + 1), 1f);
            }
            if (entity == 0 && travel.trail == null) {
                batch.send(player);
                return;
            }
            mine.add(travel);
            // The first move goes out on the next clock tick, after the client has the entity.
            travel.seen.put(player.getUniqueId(), new Seen(player, entity, new int[] {elapsed + 1}));
        }
        batch.send(player);
    }

    // ---------------------------------------------------------------- the clock

    private synchronized void ensureClock() {
        if (clock == null) {
            clock = scheduler.runGlobalTimer(this::tick, 1, 1);
        }
    }

    private synchronized boolean stopIfIdle() {
        if (active.isEmpty() && clock != null) {
            clock.cancel();
            clock = null;
            return true;
        }
        return active.isEmpty();
    }

    /** One tick of every travel, on the global thread: moves, trails and the end of the travels that arrived. */
    private void tick() {
        int tick = ++now;
        Map<UUID, DisplayEntities.Batch> batches = new HashMap<>();
        Map<UUID, List<TrailSend>> trails = new HashMap<>();
        Map<UUID, Player> players = new HashMap<>();
        double rangeSq = viewRange * viewRange;
        for (Active travel : active) {
            int elapsed = tick - travel.start;
            if (travel.cancelled.get() || travel.path.arrived(elapsed)) {
                end(travel, batches, players);
                continue;
            }
            double[] trailPoints = travel.trail == null ? null : travel.path.trail(elapsed, travel.spec.trailPoints());
            double[] here = travel.path.at(elapsed);
            for (Map.Entry<UUID, Seen> entry : travel.seen.entrySet()) {
                Seen seen = entry.getValue();
                UUID id = entry.getKey();
                if (seen.entity() != 0 && elapsed >= seen.nextMove()[0]) {
                    int target = travel.path.moveTarget(elapsed);
                    double[] to = travel.path.at(target);
                    batches.computeIfAbsent(id, k -> new DisplayEntities.Batch())
                            .move(seen.entity(), to[0], to[1], to[2], target - elapsed);
                    players.put(id, seen.player());
                    seen.nextMove()[0] = target;
                }
                if (trailPoints != null) {
                    EffectViewers.Viewer viewer = viewers.of(id);
                    if (viewer != null && travel.world.equals(viewer.world)
                            && viewer.distanceSq(here[0], here[1], here[2]) <= rangeSq) {
                        trails.computeIfAbsent(id, k -> new ArrayList<>()).add(new TrailSend(travel.trail, trailPoints));
                        players.put(id, seen.player());
                    }
                }
            }
        }
        batches.forEach((id, batch) -> batch.send(players.get(id)));
        trails.forEach((id, sends) -> {
            Player player = players.get(id);
            scheduler.runOn(player, () -> {
                for (TrailSend send : sends) {
                    double[] points = send.points();
                    for (int p = 0; p + 2 < points.length; p += 3) {
                        EffectParticles.send(player, send.trail(), points[p], points[p + 1], points[p + 2], 1);
                    }
                }
            }, null);
        });
        stopIfIdle();
    }

    /** Takes a travel down for everyone who sees it; its callback (if any) is the arrival task's. */
    private void end(Active travel, Map<UUID, DisplayEntities.Batch> batches, Map<UUID, Player> players) {
        travel.ended = true;
        active.remove(travel);
        if (travel.spec.onArrive() == null) {
            travel.finished.set(true);
        }
        travel.seen.forEach((id, seen) -> {
            Set<Active> mine = shown.get(id);
            if (mine != null) {
                mine.remove(travel);
            }
            if (seen.entity() != 0) {
                batches.computeIfAbsent(id, k -> new DisplayEntities.Batch()).destroy(seen.entity());
                players.put(id, seen.player());
            }
        });
        travel.seen.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Set<Active> mine = shown.remove(id);
        if (mine != null) {
            mine.forEach(travel -> travel.seen.remove(id));
        }
    }

    // ---------------------------------------------------------------- instant effects

    @Override
    public void burst(@NotNull Location at, @NotNull List<ParticleSpec> specs) {
        World world = Objects.requireNonNull(at.getWorld(), "world");
        List<EffectParticles.Resolved> resolved = resolveAll(specs);
        if (resolved.isEmpty()) {
            return;
        }
        double x = at.getX();
        double y = at.getY();
        double z = at.getZ();
        long chunk = Chunk.getChunkKey(at.getBlockX() >> 4, at.getBlockZ() >> 4);
        double rangeSq = viewRange * viewRange;
        for (EffectViewers.Viewer viewer : viewers.in(world.getUID())) {
            if (viewer.distanceSq(x, y, z) > rangeSq) {
                continue;
            }
            onPlayer(viewer.player, () -> {
                if (viewer.player.getWorld().equals(world) && viewer.player.isChunkSent(chunk)) {
                    for (EffectParticles.Resolved particle : resolved) {
                        EffectParticles.send(viewer.player, particle, x, y, z, 1);
                    }
                }
            });
        }
    }

    @Override
    public void line(@NotNull Location from, @NotNull Location to, @NotNull ParticleSpec particle, double density) {
        World world = Objects.requireNonNull(from.getWorld(), "world");
        if (!world.equals(to.getWorld())) {
            throw new IllegalArgumentException("from and to must be in the same world");
        }
        Optional<EffectParticles.Resolved> resolved = particles.resolve(particle);
        if (resolved.isEmpty()) {
            return;
        }
        TravelPath path = new TravelPath(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), 1);
        int points = TravelPath.beamPoints(from.distance(to), density, MAX_BEAM_POINTS);
        long fromChunk = Chunk.getChunkKey(from.getBlockX() >> 4, from.getBlockZ() >> 4);
        long toChunk = Chunk.getChunkKey(to.getBlockX() >> 4, to.getBlockZ() >> 4);
        double rangeSq = viewRange * viewRange;
        for (EffectViewers.Viewer viewer : viewers.in(world.getUID())) {
            if (path.distanceSq(viewer.x, viewer.y, viewer.z) > rangeSq) {
                continue;
            }
            onPlayer(viewer.player, () -> {
                Player player = viewer.player;
                if (!player.getWorld().equals(world)
                        || !player.isChunkSent(fromChunk) && !player.isChunkSent(toChunk)) {
                    return;
                }
                for (int i = 0; i < points; i++) {
                    double[] p = path.at(i / (double) (points - 1));
                    EffectParticles.send(player, resolved.get(), p[0], p[1], p[2], 1);
                }
            });
        }
    }

    private List<EffectParticles.Resolved> resolveAll(List<ParticleSpec> specs) {
        List<EffectParticles.Resolved> resolved = new ArrayList<>();
        for (ParticleSpec spec : specs) {
            particles.resolve(spec).ifPresent(resolved::add);
        }
        return resolved;
    }

    /** Runs {@code task} now if this thread owns {@code player}, else on the player's thread. */
    private void onPlayer(Player player, Runnable task) {
        if (scheduler.isOwnedByCurrentThread(player)) {
            task.run();
        } else {
            scheduler.runOn(player, task, null);
        }
    }

    // ---------------------------------------------------------------- self test

    /** The travel clock's tick count: advances once per tick while any travel is in flight. */
    int clockNow() {
        return now;
    }

    /** Travels in flight. */
    int activeCount() {
        return active.size();
    }

    /** Whether the travel clock runs. */
    synchronized boolean running() {
        return clock != null;
    }
}
