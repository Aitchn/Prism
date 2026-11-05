package com.aitchn.prism.validation.ae2.runtime;

import com.aitchn.prism.validation.ae2.crafting.CraftingJob;
import com.aitchn.prism.validation.ae2.crafting.CraftingPlan;
import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.grid.GridNetwork;
import com.aitchn.prism.validation.ae2.grid.GridNetwork.CraftingCluster;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.grid.PowerState;
import com.aitchn.prism.validation.ae2.storage.ItemKey;
import com.aitchn.prism.validation.ae2.storage.NetworkStorage;
import com.aitchn.prism.validation.ae2.storage.StorageView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/**
 * The live state of one network: topology, energy, the combined storage, known patterns and running jobs. The
 * topology is replaced whenever blocks change; everything else survives the replacement. Never touches the world,
 * so it can be driven from any thread; all mutation goes through the owning {@link GridManager}'s lock.
 */
public final class NetworkRuntime {

    /** Ticks one craft step takes before its output appears. */
    public static final int CRAFT_TICKS = 10;

    private final GridPos id;
    private final PowerState power = new PowerState();
    private final Delegating storage = new Delegating();
    private final Map<GridPos, CraftingJob> jobs = new TreeMap<>();
    private volatile GridNetwork topology;
    private volatile List<Pattern> patterns = List.of();
    private final List<CraftingJob> draining = new ArrayList<>();
    private volatile boolean powered;

    NetworkRuntime(@NotNull GridPos id, @NotNull GridNetwork topology) {
        this.id = id;
        this.topology = topology;
    }

    /** Stable name of the network: its smallest member position. */
    public @NotNull GridPos id() {
        return id;
    }

    public @NotNull GridNetwork topology() {
        return topology;
    }

    public boolean powered() {
        return powered;
    }

    public @NotNull PowerState power() {
        return power;
    }

    /** The combined inventory of all cells in online drives. */
    public @NotNull StorageView storage() {
        return storage;
    }

    public @NotNull List<Pattern> patterns() {
        return patterns;
    }

    public synchronized @NotNull Map<GridPos, CraftingJob> jobs() {
        return new TreeMap<>(jobs);
    }

    void update(GridNetwork newTopology, NetworkStorage newStorage, List<Pattern> newPatterns) {
        topology = newTopology;
        storage.target = newStorage;
        patterns = List.copyOf(newPatterns);
    }

    /** Cancels jobs whose crafting cluster no longer exists. */
    synchronized void dropOrphanedJobs() {
        List<GridPos> anchors = topology.clusters().stream().map(c -> anchor(c)).toList();
        jobs.entrySet().removeIf(entry -> {
            if (anchors.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().cancel();
            if (entry.getValue().holdsItems()) {
                draining.add(entry.getValue());
            }
            return true;
        });
    }

    synchronized void cancelAllJobs() {
        jobs.values().forEach(CraftingJob::cancel);
        jobs.clear();
    }

    public static @NotNull GridPos anchor(@NotNull CraftingCluster cluster) {
        return cluster.members().stream().sorted().findFirst().orElseThrow();
    }

    /** Plans a request against what is stored now and the patterns of every online provider. */
    public @NotNull CraftingPlan plan(@NotNull ItemKey target, long amount) {
        return com.aitchn.prism.validation.ae2.crafting.CraftingPlanner.plan(target, amount, storage.snapshot(),
                patterns);
    }

    /** First idle, valid cluster with enough bytes for the plan. */
    public synchronized @NotNull Optional<CraftingCluster> freeClusterFor(@NotNull CraftingPlan plan) {
        return topology.clusters().stream()
                .filter(CraftingCluster::valid)
                .filter(c -> c.bytes() >= plan.bytes())
                .filter(c -> !jobs.containsKey(anchor(c)))
                .findFirst();
    }

    /** Starts the plan on {@code cluster}; false when the stock vanished since planning. */
    public synchronized boolean startJob(@NotNull CraftingPlan plan, @NotNull CraftingCluster cluster) {
        CraftingJob job = new CraftingJob(plan, storage, cluster.parallelism(), CRAFT_TICKS);
        if (!job.start()) {
            return false;
        }
        jobs.put(anchor(cluster), job);
        return true;
    }

    /** The cells currently backing the network's storage. */
    List<com.aitchn.prism.validation.ae2.storage.StorageCell> cells() {
        return storage.target.cells();
    }

    /**
     * Called every server tick from the global scheduler. Returns true when jobs ran, which means cell contents may
     * have changed and must be saved.
     */
    synchronized boolean tick() {
        powered = power.tick(topology);
        if (!powered) {
            return false;
        }
        boolean active = !jobs.isEmpty() || !draining.isEmpty();
        draining.forEach(CraftingJob::tick);
        draining.removeIf(job -> !job.holdsItems());
        List<GridPos> finished = new ArrayList<>();
        jobs.forEach((anchor, job) -> {
            job.tick();
            if (job.status() == CraftingJob.Status.DONE || job.status() == CraftingJob.Status.CANCELLED) {
                finished.add(anchor);
            }
        });
        finished.forEach(jobs::remove);
        return active;
    }

    /** Forwards to whichever {@link NetworkStorage} is current, so running jobs survive a topology change. */
    private static final class Delegating implements StorageView {
        private volatile NetworkStorage target = new NetworkStorage(List.of());

        @Override
        public long insert(@NotNull ItemKey key, long amount) {
            return target.insert(key, amount);
        }

        @Override
        public long extract(@NotNull ItemKey key, long amount) {
            return target.extract(key, amount);
        }

        @Override
        public long count(@NotNull ItemKey key) {
            return target.count(key);
        }

        @Override
        public @NotNull Map<ItemKey, Long> snapshot() {
            return target.snapshot();
        }
    }
}
