package com.aitchn.prism.validation.ae2.crafting;

import com.aitchn.prism.validation.ae2.storage.ItemKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/** Works out which patterns to run, and what to take from storage, for a crafting request. */
public final class CraftingPlanner {

    private CraftingPlanner() {}

    /**
     * Stock is used before crafting. When several patterns make an item, the one with the smallest id wins. Surplus
     * from a craft is reused by later needs in the same plan.
     */
    public static @NotNull CraftingPlan plan(@NotNull ItemKey target, long amount, @NotNull Map<ItemKey, Long> stock,
            @NotNull Collection<Pattern> patterns) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Map<ItemKey, Pattern> byOutput = new HashMap<>();
        patterns.stream().sorted(Comparator.comparing(Pattern::id)).forEach(p -> byOutput.putIfAbsent(p.output(), p));
        Run run = new Run(new HashMap<>(stock), byOutput);
        run.need(target, amount, new ArrayDeque<>());
        List<CraftingPlan.Step> steps = new ArrayList<>();
        run.crafts.forEach((pattern, crafts) -> steps.add(new CraftingPlan.Step(pattern, crafts)));
        return new CraftingPlan(target, amount, steps, run.extract, run.missing, run.cycle);
    }

    private static final class Run {
        final Map<ItemKey, Long> available;
        final Map<ItemKey, Pattern> byOutput;
        final Map<ItemKey, Long> surplus = new HashMap<>();
        final Map<ItemKey, Long> extract = new TreeMap<>();
        final Map<ItemKey, Long> missing = new TreeMap<>();
        final Map<Pattern, Long> crafts = new LinkedHashMap<>();
        boolean cycle;

        Run(Map<ItemKey, Long> available, Map<ItemKey, Pattern> byOutput) {
            this.available = available;
            this.byOutput = byOutput;
        }

        void need(ItemKey key, long count, Deque<ItemKey> stack) {
            long left = count;
            long fromSurplus = Math.min(left, surplus.getOrDefault(key, 0L));
            if (fromSurplus > 0) {
                surplus.merge(key, -fromSurplus, Long::sum);
                left -= fromSurplus;
            }
            long fromStock = Math.min(left, available.getOrDefault(key, 0L));
            if (fromStock > 0) {
                available.merge(key, -fromStock, Long::sum);
                extract.merge(key, fromStock, Long::sum);
                left -= fromStock;
            }
            if (left == 0) {
                return;
            }
            Pattern pattern = byOutput.get(key);
            if (pattern == null) {
                missing.merge(key, left, Long::sum);
                return;
            }
            if (stack.contains(key)) {
                cycle = true;
                missing.merge(key, left, Long::sum);
                return;
            }
            long runs = (left + pattern.outputCount() - 1) / pattern.outputCount();
            crafts.merge(pattern, runs, Long::sum);
            stack.push(key);
            for (var input : pattern.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                need(input.getKey(), input.getValue() * runs, stack);
            }
            stack.pop();
            long extra = runs * pattern.outputCount() - left;
            if (extra > 0) {
                surplus.merge(key, extra, Long::sum);
            }
        }
    }
}
