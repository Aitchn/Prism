package com.aitchn.prism.client;

import com.aitchn.prism.core.look.LookTable;
import com.aitchn.prism.core.look.StateRef;
import org.jetbrains.annotations.NotNull;

/**
 * What a client is shown for the real vanilla blocks of a borrowed family: the states Prism borrows for custom blocks
 * would otherwise be drawn as custom looks wherever a vanilla block of that family has the same state (a note block a
 * player placed, a dispenser's facing). Every real state of such a family is replaced by its canonical sibling, which
 * the resource pack draws exactly as vanilla does. Ids are those of the server version, as in packets.
 */
public final class ClientCanon {

    private static final ClientCanon NONE = new ClientCanon(new int[0]);

    private final int[] canon;

    private ClientCanon(int[] canon) {
        this.canon = canon;
    }

    /** A canonicalization that changes nothing. */
    public static @NotNull ClientCanon none() {
        return NONE;
    }

    /** Builds the table for the families {@code table} uses. Needs the packet library's block mappings. */
    public static @NotNull ClientCanon of(@NotNull LookTable table) {
        if (table.activeBlocks().isEmpty()) {
            return NONE;
        }
        int[] canon = new int[ClientStates.idCount()];
        for (int id = 0; id < canon.length; id++) {
            canon[id] = id;
        }
        for (String block : table.activeBlocks()) {
            for (int id : ClientStates.ids(block)) {
                StateRef wanted = table.canonical(ClientStates.state(id));
                canon[id] = ClientStates.id(wanted);
            }
        }
        return new ClientCanon(canon);
    }

    /** Whether nothing is ever replaced. */
    public boolean isEmpty() {
        return canon.length == 0;
    }

    /** The state a client is shown for the real block state {@code id}. */
    public int of(int id) {
        return id >= 0 && id < canon.length ? canon[id] : id;
    }

    /** Whether {@code id} is replaced by another state. */
    public boolean affects(int id) {
        return id >= 0 && id < canon.length && canon[id] != id;
    }
}
