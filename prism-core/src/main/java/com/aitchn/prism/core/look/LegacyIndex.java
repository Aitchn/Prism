package com.aitchn.prism.core.look;

import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.core.block.TripwireStates;
import java.util.Map;
import java.util.OptionalInt;
import org.jetbrains.annotations.NotNull;

/**
 * The single-number state ids Bedrock mappings use for the two families Geyser draws: note block states 0 to 799 and
 * tripwire states from {@link TripwireStates#OFFSET}. Bedrock gets no mapping for borrowed states of other families
 * (stage 3 of the block carrier redesign).
 */
public final class LegacyIndex {

    private LegacyIndex() {}

    public static @NotNull OptionalInt of(@NotNull StateRef state) {
        Map<String, String> p = state.properties();
        if (state.block().equals("minecraft:note_block")) {
            int index = NoteBlockStates.index(p.get("instrument"), Integer.parseInt(p.get("note")),
                    "true".equals(p.get("powered")));
            return index <= 0 ? OptionalInt.empty() : OptionalInt.of(index);
        }
        if (state.block().equals("minecraft:tripwire") && "true".equals(p.get("disarmed"))) {
            return OptionalInt.of(TripwireStates.id("true".equals(p.get("attached")), "true".equals(p.get("east")),
                    "true".equals(p.get("north")), "true".equals(p.get("powered")), "true".equals(p.get("south")),
                    "true".equals(p.get("west"))));
        }
        return OptionalInt.empty();
    }
}
