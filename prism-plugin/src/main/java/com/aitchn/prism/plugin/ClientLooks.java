package com.aitchn.prism.plugin;

import com.aitchn.prism.client.ClientCanon;
import com.aitchn.prism.client.ClientStates;
import com.aitchn.prism.core.block.BlockLook;
import com.aitchn.prism.core.look.DisplayLook;
import com.aitchn.prism.core.look.DisplayTable;
import com.aitchn.prism.core.look.LookTable;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

/**
 * What clients are shown for each look in this boot: the {@link LookTable} the allocator produced, the display looks
 * the pack build planned, and the packet ids of both. Immutable; {@link PackManager} replaces it as a whole whenever
 * the content changes, and nothing here is ever stored (the world keeps look names, see ADR-0023).
 */
final class ClientLooks {

    static final ClientLooks EMPTY = new ClientLooks(LookTable.empty(), DisplayTable.empty(), Map.of(),
            ClientCanon.none(), Map.of(), Map.of());

    private final LookTable table;
    private final DisplayTable displays;
    /** Look id to the client state id and display id (carrier left open). */
    private final Map<String, int[]> ids;
    private final ClientCanon canon;
    /** Client state id to whether the borrowed look hides neighbouring faces (state tier only). */
    private final Map<Integer, Boolean> stateOccludes;
    /** Look id to the synthetic state id Geyser viewers are shown; only looks registered with Geyser (ADR-0024). */
    private final Map<String, Integer> bedrockIds;

    private ClientLooks(LookTable table, DisplayTable displays, Map<String, int[]> ids, ClientCanon canon,
            Map<Integer, Boolean> stateOccludes, Map<String, Integer> bedrockIds) {
        this.table = table;
        this.displays = displays;
        this.ids = ids;
        this.canon = canon;
        this.stateOccludes = stateOccludes;
        this.bedrockIds = bedrockIds;
    }

    /** The same looks with {@code registered} as the synthetic ids Geyser viewers see (empty: none). */
    ClientLooks withBedrock(Map<String, Integer> registered) {
        return new ClientLooks(table, displays, ids, canon, stateOccludes, Map.copyOf(registered));
    }

    /** The synthetic state id of {@code lookId} for Geyser viewers, or {@link BlockLook#NO_BEDROCK}. */
    int bedrockId(String lookId) {
        return bedrockIds.getOrDefault(lookId, BlockLook.NO_BEDROCK);
    }

    /** Resolves the packet ids of {@code table}. Needs the packet library's block mappings. */
    static ClientLooks of(LookTable table, DisplayTable displays) {
        Map<String, int[]> ids = new HashMap<>();
        Map<Integer, Boolean> occludes = new HashMap<>();
        for (LookTable.Placement placement : table.placements().values()) {
            DisplayLook display = placement.tier() == LookTable.Tier.DISPLAY ? displays.forLook(placement.lookId()) : null;
            ids.put(placement.lookId(), new int[] {ClientStates.id(placement.state()),
                    display == null ? BlockLook.NO_DISPLAY : display.id()});
            if (placement.tier() == LookTable.Tier.STATE) {
                occludes.put(ids.get(placement.lookId())[0], placement.wanted().occludes());
            }
        }
        return new ClientLooks(table, displays, Map.copyOf(ids), ClientCanon.of(table), Map.copyOf(occludes),
                Map.of());
    }

    LookTable table() {
        return table;
    }

    DisplayTable displays() {
        return displays;
    }

    ClientCanon canon() {
        return canon;
    }

    /**
     * What Java viewers see at a block of look {@code lookId} stored with {@code carrier}: the borrowed state, or the
     * shell plus the display look. Null if the look got nothing (absent mod, or the pool ran dry) or is for Bedrock only.
     */
    @Nullable BlockLook lookFor(Carrier carrier, String lookId) {
        int[] found = ids.get(lookId);
        LookTable.Placement placement = table.placement(lookId);
        if (found == null || placement == null || placement.bedrockOnly()) {
            return null;
        }
        return new BlockLook(carrier.clientId(), found[0], found[1], bedrockId(lookId));
    }

    /**
     * The carrier a block of look {@code lookId} is stored with in this boot: {@link Carrier#PASSABLE} for a passable
     * block, {@link Carrier#DISPLAY} when its Java shell is the clear shell (one display entity, ADR-0025), otherwise
     * {@link Carrier#SOLID}. Null if the look got nothing or is for Bedrock only (the caller keeps its default).
     */
    @Nullable Carrier carrierFor(String lookId, boolean passable) {
        if (passable) {
            return Carrier.PASSABLE;
        }
        LookTable.Placement placement = table.placement(lookId);
        if (placement == null || placement.bedrockOnly()) {
            return null;
        }
        return com.aitchn.prism.core.look.LookPool.isClearShell(placement.state()) ? Carrier.DISPLAY : Carrier.SOLID;
    }

    /** Whether the custom block shown as {@code look} hides the faces of its neighbours. */
    boolean occludes(BlockLook look) {
        if (look.displayed()) {
            DisplayLook display = displays.get(look.display());
            return display != null && display.occludes();
        }
        return stateOccludes.getOrDefault(look.look(), false);
    }

    /** The block data a Bedrock viewer is sent for a look drawn only for Bedrock, or null. */
    @Nullable BlockData bedrockData(String lookId) {
        LookTable.Placement placement = table.placement(lookId);
        if (placement == null || !placement.bedrockOnly()) {
            return null;
        }
        return Bukkit.createBlockData(placement.state().blockData());
    }
}
