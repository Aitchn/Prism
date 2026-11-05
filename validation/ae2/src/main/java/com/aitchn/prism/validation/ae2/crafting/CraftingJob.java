package com.aitchn.prism.validation.ae2.crafting;

import com.aitchn.prism.validation.ae2.storage.ItemKey;
import com.aitchn.prism.validation.ae2.storage.StorageView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/**
 * Runs a {@link CraftingPlan} on a crafting CPU: it pulls the required stock into an internal buffer, starts up to
 * {@code parallelism} crafts at a time, and returns everything to the network at the end. Tick-driven and not
 * thread-safe; one owner drives it.
 */
public final class CraftingJob {

    public enum Status { PENDING, RUNNING, FINISHING, CANCELLING, DONE, CANCELLED }

    private record Running(Pattern pattern, int ticksLeft) {}

    private final CraftingPlan plan;
    private final StorageView network;
    private final int parallelism;
    private final int craftTicks;
    private final Map<ItemKey, Long> buffer = new TreeMap<>();
    private final Map<Pattern, Long> remaining = new java.util.LinkedHashMap<>();
    private final List<Running> inFlight = new ArrayList<>();
    private Status status = Status.PENDING;

    public CraftingJob(@NotNull CraftingPlan plan, @NotNull StorageView network, int parallelism, int craftTicks) {
        if (!plan.feasible()) {
            throw new IllegalArgumentException("plan is not feasible: missing " + plan.missing());
        }
        if (parallelism < 1 || craftTicks < 1) {
            throw new IllegalArgumentException("parallelism and craftTicks must be at least 1");
        }
        this.plan = plan;
        this.network = network;
        this.parallelism = parallelism;
        this.craftTicks = craftTicks;
        plan.steps().forEach(step -> remaining.put(step.pattern(), step.crafts()));
    }

    public @NotNull Status status() {
        return status;
    }

    public @NotNull CraftingPlan plan() {
        return plan;
    }

    public @NotNull Map<ItemKey, Long> buffer() {
        return Map.copyOf(buffer);
    }

    /** Crafts still to start plus those running. */
    public long craftsLeft() {
        return remaining.values().stream().mapToLong(Long::longValue).sum() + inFlight.size();
    }

    /**
     * Takes the planned stock out of the network, all or nothing. Returns false (and changes nothing) when the stock
     * is no longer there.
     */
    public boolean start() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("already started");
        }
        Map<ItemKey, Long> taken = new TreeMap<>();
        for (var entry : plan.extractFromStorage().entrySet()) {
            long got = network.extract(entry.getKey(), entry.getValue());
            taken.put(entry.getKey(), got);
            if (got < entry.getValue()) {
                taken.forEach(network::insert);
                return false;
            }
        }
        taken.forEach((key, count) -> buffer.merge(key, count, Long::sum));
        status = Status.RUNNING;
        return true;
    }

    public void tick() {
        switch (status) {
            case RUNNING -> {
                finishCrafts();
                startCrafts();
                if (remaining.values().stream().allMatch(n -> n == 0) && inFlight.isEmpty()) {
                    status = Status.FINISHING;
                }
            }
            case FINISHING, CANCELLING -> flush();
            default -> { }
        }
    }

    /**
     * Stops the job and returns whatever is in the buffer to the network. When the network is full the job stays in
     * {@link Status#CANCELLING} and keeps retrying on every tick until the buffer is empty.
     */
    public void cancel() {
        if (status == Status.DONE || status == Status.CANCELLED || status == Status.CANCELLING) {
            return;
        }
        inFlight.clear();
        status = Status.CANCELLING;
        flush();
    }

    /** True while the job still holds items that must go back to the network. */
    public boolean holdsItems() {
        return !buffer.isEmpty();
    }

    private void finishCrafts() {
        List<Running> next = new ArrayList<>();
        for (Running running : inFlight) {
            if (running.ticksLeft() <= 1) {
                buffer.merge(running.pattern().output(), running.pattern().outputCount(), Long::sum);
            } else {
                next.add(new Running(running.pattern(), running.ticksLeft() - 1));
            }
        }
        inFlight.clear();
        inFlight.addAll(next);
    }

    private void startCrafts() {
        boolean progressed = true;
        while (progressed && inFlight.size() < parallelism) {
            progressed = false;
            for (var entry : remaining.entrySet()) {
                if (inFlight.size() >= parallelism) {
                    break;
                }
                if (entry.getValue() > 0 && hasInputs(entry.getKey())) {
                    entry.getKey().inputs().forEach((key, count) -> take(key, count));
                    entry.setValue(entry.getValue() - 1);
                    inFlight.add(new Running(entry.getKey(), craftTicks));
                    progressed = true;
                }
            }
        }
    }

    private boolean hasInputs(Pattern pattern) {
        return pattern.inputs().entrySet().stream().allMatch(e -> buffer.getOrDefault(e.getKey(), 0L) >= e.getValue());
    }

    private void take(ItemKey key, long count) {
        long left = buffer.get(key) - count;
        if (left == 0) {
            buffer.remove(key);
        } else {
            buffer.put(key, left);
        }
    }

    /** Hands the buffer back; whatever the network cannot hold stays and is retried on the next tick. */
    private void flush() {
        for (var entry : new ArrayList<>(buffer.entrySet())) {
            long accepted = network.insert(entry.getKey(), entry.getValue());
            if (accepted >= entry.getValue()) {
                buffer.remove(entry.getKey());
            } else {
                buffer.put(entry.getKey(), entry.getValue() - accepted);
            }
        }
        if (buffer.isEmpty()) {
            if (status == Status.FINISHING) {
                status = Status.DONE;
            } else if (status == Status.CANCELLING) {
                status = Status.CANCELLED;
            }
        }
    }
}
