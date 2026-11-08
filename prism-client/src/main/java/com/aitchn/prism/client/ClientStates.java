package com.aitchn.prism.client;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.data.BlockData;

/** Block state ids as they appear in packets. All methods are safe to call from any thread. */
public final class ClientStates {

    private static final Map<Integer, Boolean> LEGACY = new ConcurrentHashMap<>();

    private ClientStates() {}

    /** The global block state id of {@code data}, the number a block packet carries for it. */
    public static int id(BlockData data) {
        return SpigotConversionUtil.fromBukkitBlockData(data).getGlobalId();
    }

    /**
     * Whether {@code id} is a state of a carrier that earlier versions used (note block, tripwire). A position that
     * still holds one of them is a custom block whose chunk has not been migrated yet, so it is drawn like the new
     * carrier instead of being taken for a foreign change.
     */
    public static boolean isLegacyCarrier(int id) {
        return LEGACY.computeIfAbsent(id, ClientStates::computeLegacy);
    }

    private static boolean computeLegacy(int id) {
        StateType type = WrappedBlockState.getByGlobalId(id).getType();
        return type == StateTypes.NOTE_BLOCK || type == StateTypes.TRIPWIRE;
    }
}
