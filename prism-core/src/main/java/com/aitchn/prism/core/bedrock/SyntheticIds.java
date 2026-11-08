package com.aitchn.prism.core.bedrock;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;

/**
 * Synthetic Java block state ids for Geyser viewers (ADR-0024). Every look gets one id above the vanilla range; the
 * ids are registered with Geyser as non-vanilla block states, each mapped to a real Bedrock custom block, and are only
 * ever sent to Geyser connections. The assignment is made in look id order, like the Java allocation of stage 2, so the
 * same content gives the same ids and the same Bedrock pack. Nothing here is stored.
 */
public final class SyntheticIds {

    /**
     * How far above the server's last vanilla state the ids start, unless configured. Geyser refuses ids that do not
     * start after its own last vanilla state, and the server it runs beside (or behind a translation layer) may have
     * fewer states than the Geyser build; the gap costs nothing (Geyser fills it with air).
     */
    public static final int DEFAULT_MARGIN = 4096;

    private SyntheticIds() {}

    /**
     * @param base first id of the range; no look has an id below it
     * @param ids  look id to synthetic id, in allocation order
     */
    public record Plan(int base, @NotNull Map<String, Integer> ids) {

        public Plan {
            ids = Collections.unmodifiableMap(new LinkedHashMap<>(ids));
        }

        /** A plan without any look. */
        public static @NotNull Plan empty(int base) {
            return new Plan(base, Map.of());
        }

        public @NotNull OptionalInt idOf(@NotNull String lookId) {
            Integer id = ids.get(lookId);
            return id == null ? OptionalInt.empty() : OptionalInt.of(id);
        }

        public int size() {
            return ids.size();
        }

        /** One more than the highest id this plan hands out (the size of Geyser's block state registry afterwards). */
        public int stateCount() {
            return base + ids.size();
        }

        /**
         * The bit width of Geyser's global block palette once these ids are registered: it derives it from the size
         * of its registry, so a section written with the global palette must use exactly this many bits per block.
         */
        public int globalBits() {
            return bitsFor(stateCount());
        }
    }

    /** The first id when none is configured: the server's state count plus {@link #DEFAULT_MARGIN}. */
    public static int defaultBase(int vanillaStateCount) {
        return Math.addExact(vanillaStateCount, DEFAULT_MARGIN);
    }

    /**
     * Allocates one id per distinct look, in natural (look id) order, starting at {@code base}.
     *
     * @throws ArithmeticException if the ids would not fit in a positive int
     */
    public static @NotNull Plan plan(@NotNull Collection<String> lookIds, int base) {
        if (base < 0) {
            throw new IllegalArgumentException("base must not be negative: " + base);
        }
        Map<String, Integer> ids = new LinkedHashMap<>();
        int next = base;
        for (String lookId : new TreeSet<>(lookIds)) {
            ids.put(lookId, next);
            next = Math.addExact(next, 1);
        }
        return new Plan(base, ids);
    }

    /** Bits needed to write ids below {@code count}: the ceiling of log2, at least 1 (vanilla's {@code ceillog2}). */
    public static int bitsFor(int count) {
        if (count <= 2) {
            return 1;
        }
        return 32 - Integer.numberOfLeadingZeros(count - 1);
    }
}
