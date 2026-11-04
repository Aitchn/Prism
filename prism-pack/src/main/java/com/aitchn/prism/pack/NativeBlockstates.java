package com.aitchn.prism.pack;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.pack.json.JsonWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Generates {@code assets/minecraft/blockstates/note_block.json} for the native-state block backend. The file
 * replaces the vanilla one, so every one of the 800 states is listed: allocated blocks that have an appearance point
 * at their model {@code <ns>:block/<value>}, everything else keeps the vanilla note block model.
 */
public final class NativeBlockstates {

    public static final String PATH = "assets/minecraft/blockstates/note_block.json";

    private static final String VANILLA_MODEL = "minecraft:block/note_block";

    private NativeBlockstates() {}

    public static @NotNull String noteBlock(
            @NotNull Map<PrismKey, Integer> assignments, @NotNull Set<PrismKey> withAppearance) {
        Map<Integer, String> models = new LinkedHashMap<>();
        assignments.forEach((key, index) -> {
            if (withAppearance.contains(key)) {
                models.put(index, key.namespace() + ":block/" + key.value());
            }
        });
        Map<String, Object> variants = new LinkedHashMap<>();
        for (int i = 0; i < NoteBlockStates.COUNT; i++) {
            variants.put(NoteBlockStates.state(i).variant(), Map.of("model", models.getOrDefault(i, VANILLA_MODEL)));
        }
        return JsonWriter.write(Map.of("variants", variants));
    }
}
