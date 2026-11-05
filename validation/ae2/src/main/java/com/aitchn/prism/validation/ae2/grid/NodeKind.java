package com.aitchn.prism.validation.ae2.grid;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** Every block that can be part of an ME network, with the numbers the grid calculation needs. */
public enum NodeKind {
    CONTROLLER(Role.CONDUIT, Integer.MAX_VALUE, 0, 6.0, 0, 0),
    CABLE(Role.CONDUIT, 8, 0, 0.0, 0, 0),
    DENSE_CABLE(Role.CONDUIT, 32, 0, 0.0, 0, 0),
    DRIVE(Role.DEVICE, 0, 1, 2.0, 0, 0),
    TERMINAL(Role.DEVICE, 0, 1, 0.5, 0, 0),
    PATTERN_PROVIDER(Role.DEVICE, 0, 1, 1.0, 0, 0),
    ENERGY_CELL(Role.DEVICE, 0, 0, 0.0, 200_000, 0),
    CREATIVE_ENERGY_CELL(Role.DEVICE, 0, 0, 0.0, Long.MAX_VALUE, 0),
    CRAFTING_STORAGE_1K(Role.CRAFTING, 0, 0, 0.0, 0, 1024),
    CRAFTING_STORAGE_4K(Role.CRAFTING, 0, 0, 0.0, 0, 4096),
    CRAFTING_STORAGE_16K(Role.CRAFTING, 0, 0, 0.0, 0, 16384),
    CRAFTING_STORAGE_64K(Role.CRAFTING, 0, 0, 0.0, 0, 65536),
    CRAFTING_COPROCESSOR(Role.CRAFTING, 0, 0, 0.0, 0, 0);

    /** How a block takes part in connectivity. */
    public enum Role {
        /** Carries channels: controller and cables. */
        CONDUIT,
        /** Attaches to conduits and may need a channel. */
        DEVICE,
        /** Part of a crafting cluster; needs no channel. */
        CRAFTING
    }

    private final Role role;
    private final int channelCapacity;
    private final int channelsRequired;
    private final double idlePower;
    private final long energyCapacity;
    private final long craftingBytes;

    NodeKind(Role role, int channelCapacity, int channelsRequired, double idlePower, long energyCapacity,
            long craftingBytes) {
        this.role = role;
        this.channelCapacity = channelCapacity;
        this.channelsRequired = channelsRequired;
        this.idlePower = idlePower;
        this.energyCapacity = energyCapacity;
        this.craftingBytes = craftingBytes;
    }

    public @NotNull Role role() {
        return role;
    }

    /** Channels a conduit of this kind can carry (0 for non-conduits). */
    public int channelCapacity() {
        return channelCapacity;
    }

    public int channelsRequired() {
        return channelsRequired;
    }

    /** AE drained per tick while the device is online. */
    public double idlePower() {
        return idlePower;
    }

    public long energyCapacity() {
        return energyCapacity;
    }

    /** Bytes this crafting block adds to its cluster. */
    public long craftingBytes() {
        return craftingBytes;
    }

    /** The registry value of the block and its item: {@code ae2:<this>}. */
    public @NotNull String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @NotNull NodeKind fromId(@NotNull String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
