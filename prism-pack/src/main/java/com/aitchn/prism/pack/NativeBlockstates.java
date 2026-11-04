package com.aitchn.prism.pack;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.pack.json.JsonWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Generates {@code assets/minecraft/blockstates/note_block.json} for the native-state block backend. The file
 * replaces the vanilla one, so every one of the 800 states is listed: allocated blocks that have an appearance point
 * at their resolved model, everything else keeps the vanilla note block model.
 */
public final class NativeBlockstates {

    public static final String PATH = "assets/minecraft/blockstates/note_block.json";

    private static final String VANILLA_MODEL = "minecraft:block/note_block";

    private NativeBlockstates() {}

    /**
     * @param assignments block to state index
     * @param models      the resolved model of every block that has an appearance (see
     *                    {@link JavaPackBuilder#blockModel}); blocks absent here keep the vanilla look
     */
    public static @NotNull String noteBlock(
            @NotNull Map<PrismKey, Integer> assignments, @NotNull Map<PrismKey, PrismKey> modelByBlock) {
        Map<Integer, String> models = new LinkedHashMap<>();
        assignments.forEach((key, index) -> {
            PrismKey model = modelByBlock.get(key);
            if (model != null) {
                models.put(index, model.namespace() + ":" + model.value());
            }
        });
        Map<String, Object> variants = new LinkedHashMap<>();
        for (int i = 0; i < NoteBlockStates.COUNT; i++) {
            variants.put(NoteBlockStates.state(i).variant(), Map.of("model", models.getOrDefault(i, VANILLA_MODEL)));
        }
        return JsonWriter.write(Map.of("variants", variants));
    }
}
