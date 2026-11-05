package com.aitchn.prism.pack;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.pack.json.JsonWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Generates {@code assets/minecraft/blockstates/note_block.json} for native-state blocks. The file replaces the
 * vanilla one, so every state is listed (the 800 Prism can allocate and the vanilla head-instrument ones): allocated looks point at their model with the rotation of
 * their facing, everything else keeps the vanilla note block model. Ported from the M5b native backend.
 */
public final class NativeBlockstates {

    public static final String PATH = "assets/minecraft/blockstates/note_block.json";

    private static final String VANILLA_MODEL = "minecraft:block/note_block";

    /**
     * Instruments a note block takes from a mob head on top. Prism never allocates them, but the replacement file must
     * still list them or a vanilla note block under a head shows the missing model.
     */
    static final java.util.List<String> HEAD_INSTRUMENTS = java.util.List.of(
            "zombie", "skeleton", "creeper", "dragon", "wither_skeleton", "piglin", "custom_head");

    /** How one state is drawn: a model rotated by {@code x} and {@code y} degrees (multiples of 90). */
    public record StateModel(@NotNull PrismKey model, int x, int y) {}

    private NativeBlockstates() {}

    /** @param states state index to its model; indices absent here keep the vanilla look */
    public static @NotNull String noteBlock(@NotNull Map<Integer, StateModel> states) {
        Map<String, Object> variants = new LinkedHashMap<>();
        for (int i = 0; i < NoteBlockStates.COUNT; i++) {
            StateModel state = states.get(i);
            Map<String, Object> variant = new LinkedHashMap<>();
            variant.put("model", state == null ? VANILLA_MODEL : state.model().toString());
            if (state != null && state.x() != 0) {
                variant.put("x", state.x());
            }
            if (state != null && state.y() != 0) {
                variant.put("y", state.y());
            }
            variants.put(NoteBlockStates.state(i).variant(), variant);
        }
        for (String instrument : HEAD_INSTRUMENTS) {
            for (int note = 0; note < NoteBlockStates.NOTES; note++) {
                for (boolean powered : new boolean[] {false, true}) {
                    variants.put(new NoteBlockStates.State(instrument, note, powered).variant(),
                            Map.of("model", VANILLA_MODEL));
                }
            }
        }
        return JsonWriter.write(Map.of("variants", variants));
    }
}
