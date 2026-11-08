package com.aitchn.prism.client;

import com.aitchn.prism.core.look.StateRef;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.data.BlockData;

/** Block state ids as they appear in packets. All methods are safe to call from any thread. */
public final class ClientStates {

    private static final Map<Integer, Boolean> LEGACY = new ConcurrentHashMap<>();

    /** Global ids of every block on this server version, grouped by block name ({@code minecraft:note_block}). */
    private static volatile Map<String, int[]> byBlock;

    private ClientStates() {}

    /**
     * The global block state id of {@code state}, as packets of this server version carry it.
     *
     * @throws IllegalArgumentException if this version has no such state
     */
    public static int id(StateRef state) {
        WrappedBlockState found = WrappedBlockState.getByString(state.blockData());
        if (found.getGlobalId() == 0 && !state.block().equals("minecraft:air")) {
            throw new IllegalArgumentException("no block state " + state.blockData() + " on this server version");
        }
        return found.getGlobalId();
    }

    /** The block state with global id {@code id}. */
    public static StateRef state(int id) {
        return StateRef.parse(WrappedBlockState.getByGlobalId(id, false).toString());
    }

    /** Every state of {@code block} on this server version, in id order; empty if the block does not exist. */
    public static List<StateRef> states(String block) {
        int[] ids = index().getOrDefault(block, new int[0]);
        List<StateRef> states = new ArrayList<>(ids.length);
        for (int id : ids) {
            states.add(state(id));
        }
        return states;
    }

    /** The global ids of every state of {@code block}; empty if the block does not exist. */
    static int[] ids(String block) {
        return index().getOrDefault(block, new int[0]);
    }

    /** One more than the highest global state id of this server version. */
    public static int stateCount() {
        return idCount();
    }

    static int idCount() {
        int max = 0;
        for (int[] ids : index().values()) {
            max = Math.max(max, ids[ids.length - 1] + 1);
        }
        return max;
    }

    private static Map<String, int[]> index() {
        Map<String, int[]> built = byBlock;
        if (built == null) {
            synchronized (ClientStates.class) {
                built = byBlock;
                if (built == null) {
                    byBlock = built = enumerate();
                }
            }
        }
        return built;
    }

    /** Global ids are consecutive from 0 (air); the first id the mappings do not know ends the walk. */
    private static Map<String, int[]> enumerate() {
        Map<String, List<Integer>> collected = new HashMap<>();
        for (int id = 1;; id++) {
            WrappedBlockState state = WrappedBlockState.getByGlobalId(id, false);
            if (state.getGlobalId() != id) {
                break;
            }
            String text = state.toString();
            int open = text.indexOf('[');
            String block = open < 0 ? text : text.substring(0, open);
            collected.computeIfAbsent(block.contains(":") ? block : "minecraft:" + block, k -> new ArrayList<>())
                    .add(id);
        }
        Map<String, int[]> result = new HashMap<>();
        collected.forEach((block, ids) -> result.put(block, ids.stream().mapToInt(Integer::intValue).toArray()));
        return result;
    }

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
