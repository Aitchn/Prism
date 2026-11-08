package com.aitchn.prism.plugin;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/**
 * The inert vanilla block the server stores for a custom block whose look is a client-side state. A carrier has no
 * block states, no scheduled or random ticks and no reaction to its neighbours, so nothing the server or a client
 * predicts can change it; viewers are shown the block's look instead ({@code BlockPacketRewriter}). Which block it is
 * does not matter to players, only its physics do. See ADR-0022.
 */
enum Carrier {

    /**
     * Full, opaque, redstone-conducting block. Brown mushroom block: its six face flags are fixed (no neighbour update
     * changes them), no ticks, no vanilla tool changes it, fire does not burn it, and it breaks like the note block it
     * stands in for: axe is the preferred tool, no tool is needed for drops, and its hardness (0.2) is below the note
     * block's (0.8), so the server never finishes a survival break later than the client predicts it.
     */
    SOLID(Material.BROWN_MUSHROOM_BLOCK),

    /**
     * No collision, no states, no ticks, does not hold fluid, cannot be pressed or connected. Structure void: the
     * client is shown a tripwire state (disarmed) with no collision either.
     */
    PASSABLE(Material.STRUCTURE_VOID);

    private final Material material;
    private volatile int clientId = -1;

    Carrier(Material material) {
        this.material = material;
    }

    /** The block state id of this carrier in packets (the id the packet rewriter looks for). */
    int clientId() {
        int id = clientId;
        if (id < 0) {
            clientId = id = com.aitchn.prism.client.ClientStates.id(material.createBlockData());
        }
        return id;
    }

    Material material() {
        return material;
    }

    /** The carrier called {@code name}, or null if there is none (an unreadable entry is treated as unmarked). */
    static @Nullable Carrier parse(@Nullable String name) {
        for (Carrier carrier : values()) {
            if (carrier.name().equals(name)) {
                return carrier;
            }
        }
        return null;
    }

    /** The carrier whose block is {@code type}, or null if {@code type} is not a carrier. */
    static @Nullable Carrier of(Material type) {
        for (Carrier carrier : values()) {
            if (carrier.material == type) {
                return carrier;
            }
        }
        return null;
    }

    /** The carrier earlier versions used for blocks that are now {@code SOLID} or {@code PASSABLE}, or null. */
    static @Nullable Carrier legacy(Material type) {
        return type == Material.NOTE_BLOCK ? SOLID : type == Material.TRIPWIRE ? PASSABLE : null;
    }
}
