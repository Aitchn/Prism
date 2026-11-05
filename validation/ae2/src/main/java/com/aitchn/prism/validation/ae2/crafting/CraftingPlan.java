package com.aitchn.prism.validation.ae2.crafting;

import com.aitchn.prism.validation.ae2.storage.ItemKey;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * What a crafting request would do.
 *
 * @param steps               patterns to run and how often, in the order they were discovered (outputs first)
 * @param extractFromStorage  items the job must take out of the network before it starts
 * @param missing             items that can neither be found in storage nor crafted
 * @param cycle               true when a pattern needed its own output to make itself
 */
public record CraftingPlan(
        @NotNull ItemKey target,
        long amount,
        @NotNull List<Step> steps,
        @NotNull Map<ItemKey, Long> extractFromStorage,
        @NotNull Map<ItemKey, Long> missing,
        boolean cycle) {

    /** One pattern and how many times it runs. */
    public record Step(@NotNull Pattern pattern, long crafts) {}

    public CraftingPlan {
        steps = List.copyOf(steps);
        extractFromStorage = Map.copyOf(extractFromStorage);
        missing = Map.copyOf(missing);
    }

    public boolean feasible() {
        return missing.isEmpty() && !cycle;
    }

    public long totalCrafts() {
        return steps.stream().mapToLong(Step::crafts).sum();
    }

    /** Memory the job needs on a crafting CPU: a flat cost per pattern plus one byte per eight moved items. */
    public long bytes() {
        long moved = 0;
        for (Step step : steps) {
            moved += step.crafts() * (step.pattern().outputCount()
                    + step.pattern().inputs().values().stream().mapToLong(Long::longValue).sum());
        }
        return 8L * steps.size() + (moved + 7) / 8;
    }
}
