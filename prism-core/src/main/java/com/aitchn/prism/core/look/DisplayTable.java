package com.aitchn.prism.core.look;

import com.aitchn.prism.core.block.Appearances;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The display looks of a {@link LookTable}: its display-tier placements joined with the pack's {@link DisplayPlan}s. */
public final class DisplayTable {

    private static final DisplayTable EMPTY = new DisplayTable(List.of(), Map.of());

    private final List<DisplayLook> looks;
    private final Map<String, DisplayLook> byLook;

    private DisplayTable(List<DisplayLook> looks, Map<String, DisplayLook> byLook) {
        this.looks = looks;
        this.byLook = byLook;
    }

    public static @NotNull DisplayTable empty() {
        return EMPTY;
    }

    /**
     * @param plans the plan of each look id the pack could draw; a display look without one is not drawn (the pack
     *              build reports why)
     */
    public static @NotNull DisplayTable of(@NotNull LookTable table, @NotNull Map<String, DisplayPlan> plans) {
        List<DisplayLook> looks = new ArrayList<>();
        Map<String, DisplayLook> byLook = new HashMap<>();
        for (LookTable.Placement placement : table.placements().values()) {
            DisplayPlan plan = plans.get(placement.lookId());
            if (placement.tier() != LookTable.Tier.DISPLAY || plan == null) {
                continue;
            }
            int[] rotation = Appearances.rotation(facingOf(placement.lookId()));
            DisplayLook look = new DisplayLook(looks.size(), placement.lookId(), plan, rotation[0], rotation[1],
                    placement.wanted().occludes());
            looks.add(look);
            byLook.put(look.lookId(), look);
        }
        return new DisplayTable(Collections.unmodifiableList(looks), Collections.unmodifiableMap(byLook));
    }

    /** The facing name ({@code NORTH}...) in a look id: the part after {@code @}, north if there is none. */
    static String facingOf(String lookId) {
        int at = lookId.lastIndexOf('@');
        return at < 0 ? "NORTH" : lookId.substring(at + 1).toUpperCase(java.util.Locale.ROOT);
    }

    public int size() {
        return looks.size();
    }

    public @Nullable DisplayLook get(int id) {
        return id >= 0 && id < looks.size() ? looks.get(id) : null;
    }

    public @Nullable DisplayLook forLook(@NotNull String lookId) {
        return byLook.get(lookId);
    }

    public @NotNull List<DisplayLook> looks() {
        return looks;
    }
}
