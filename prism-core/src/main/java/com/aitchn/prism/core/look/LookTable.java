package com.aitchn.prism.core.look;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The result of allocating client representations to looks for one boot: which borrowed state (S tier) or which shell
 * (D tier) a client is shown for each look. Nothing here is stored; the world keeps identity only, so the table is
 * rebuilt from the registered content, in a deterministic order, whenever it changes.
 */
public final class LookTable {

    public enum Tier { STATE, DISPLAY }

    /**
     * @param lookId  the look
     * @param tier    {@code STATE}: {@code state} is borrowed for it alone. {@code DISPLAY}: {@code state} is the
     *                invisible shell several looks share, and display entities draw the model
     * @param wanted  the class the look asked for
     * @param drawnAs the class of {@code state}; differs from {@code wanted} only for a shell chosen by relaxation
     */
    public record Placement(@NotNull String lookId, @NotNull Tier tier, @NotNull StateRef state,
            @NotNull PhysicsClass wanted, @NotNull PhysicsClass drawnAs, boolean bedrockOnly) {
        public boolean exact() {
            return wanted.equals(drawnAs);
        }
    }

    private final ResolvedPool pool;
    private final Map<String, Placement> placements;
    private final List<String> unplaced;
    private final List<String> warnings;

    LookTable(ResolvedPool pool, Map<String, Placement> placements, List<String> unplaced, List<String> warnings) {
        this.pool = pool;
        this.placements = Collections.unmodifiableMap(new LinkedHashMap<>(placements));
        this.unplaced = List.copyOf(unplaced);
        this.warnings = List.copyOf(warnings);
    }

    /** A table without any look, for a server that has not allocated yet. */
    public static @NotNull LookTable empty() {
        return new LookTable(new ResolvedPool(List.of(), List.of()), Map.of(), List.of(), List.of());
    }

    public @NotNull ResolvedPool pool() {
        return pool;
    }

    /** Every placement, ordered by look id. */
    public @NotNull Map<String, Placement> placements() {
        return placements;
    }

    public @Nullable Placement placement(@NotNull String lookId) {
        return placements.get(lookId);
    }

    /** Looks that got nothing (a Bedrock-only look when the note block states ran out). */
    public @NotNull List<String> unplaced() {
        return unplaced;
    }

    public @NotNull List<String> warnings() {
        return warnings;
    }

    /** How many looks use the given tier. */
    public int count(@NotNull Tier tier) {
        return (int) placements.values().stream().filter(p -> p.tier() == tier).count();
    }

    /** How a client predicts breaking the state a look is drawn with, or null if the look has no placement. */
    public @Nullable ClientBreak breaking(@NotNull String lookId) {
        Placement placement = placements.get(lookId);
        if (placement == null) {
            return null;
        }
        return pool.family(placement.state().block()).map(f -> f.family().breaking()).orElse(null);
    }

    /** The blocks of the families that have at least one borrowed state or shell in use, in family order. */
    public @NotNull Set<String> activeBlocks() {
        Set<String> used = new LinkedHashSet<>();
        for (Placement placement : placements.values()) {
            used.add(placement.state().block());
        }
        Set<String> active = new LinkedHashSet<>();
        for (ResolvedPool.Family family : pool.families()) {
            if (used.contains(family.family().block())) {
                active.add(family.family().block());
            }
        }
        return active;
    }

    /**
     * What a client is shown for a real vanilla block in {@code state}: the canonical sibling if its family is in use,
     * else the state itself.
     */
    public @NotNull StateRef canonical(@NotNull StateRef state) {
        for (ResolvedPool.Family family : pool.families()) {
            if (family.family().block().equals(state.block())) {
                return activeBlocks().contains(state.block()) ? family.family().canonical(state) : state;
            }
        }
        return state;
    }

    /**
     * The state index Geyser's block mappings know for this look, for the families Bedrock can draw: a note block state
     * (0 to 799) or a tripwire state (from {@link com.aitchn.prism.core.block.TripwireStates#OFFSET}). Empty for looks in
     * any other family and for the display layer.
     */
    public @NotNull OptionalInt bedrockIndex(@NotNull String lookId) {
        Placement placement = placements.get(lookId);
        if (placement == null || placement.tier() != Tier.STATE) {
            return OptionalInt.empty();
        }
        return LegacyIndex.of(placement.state());
    }
}
