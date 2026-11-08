package com.aitchn.prism.pack;

import com.aitchn.prism.core.look.BorrowedFamily;
import com.aitchn.prism.core.look.ModelRef;
import com.aitchn.prism.core.look.ResolvedPool;
import com.aitchn.prism.core.look.StateRef;
import com.aitchn.prism.pack.json.JsonWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * The replacement blockstate files of the borrowed families. A blockstate file replaces the game's whole file, so each
 * lists every state of the block on the server: borrowed states that a look uses get its model, the shell state gets the
 * transparent cube, and every other state keeps the model the game gives it (the replacement is therefore invisible to
 * real vanilla blocks, which a client is shown in a canonical state anyway).
 */
public final class LookBlockstates {

    /** The model of the invisible shell: a full cube drawn with a fully transparent texture. */
    public static final String SHELL_MODEL = "prism:block/shell";
    public static final String SHELL_TEXTURE_PATH = "assets/prism/textures/block/shell.png";
    public static final String SHELL_MODEL_PATH = "assets/prism/models/block/shell.json";

    private LookBlockstates() {}

    /** {@code assets/minecraft/blockstates/<block>.json} for a block id such as {@code minecraft:note_block}. */
    public static @NotNull String path(@NotNull String block) {
        return "assets/minecraft/blockstates/" + block.substring(block.indexOf(':') + 1) + ".json";
    }

    /**
     * @param family the usable family
     * @param custom the borrowed states in use and the model each draws (a model the pack could not give a look is
     *               absent and keeps its vanilla model)
     * @param shell  whether the family's shell state is in use
     */
    public static @NotNull String write(@NotNull ResolvedPool.Family family, @NotNull Map<StateRef, ModelRef> custom,
            boolean shell) {
        BorrowedFamily catalog = family.family();
        List<StateRef> states = new ArrayList<>(family.allStates());
        states.sort(Comparator.comparing(StateRef::variantKey));
        Map<String, Object> variants = new LinkedHashMap<>();
        for (StateRef state : states) {
            ModelRef model = custom.get(state);
            if (model == null && shell && family.shell().filter(state::equals).isPresent()) {
                model = new ModelRef(SHELL_MODEL);
            }
            if (model == null) {
                model = catalog.vanillaModel(state);
            }
            Map<String, Object> variant = new LinkedHashMap<>();
            variant.put("model", model.model());
            if (model.x() != 0) {
                variant.put("x", model.x());
            }
            if (model.y() != 0) {
                variant.put("y", model.y());
            }
            variants.put(state.variantKey(), variant);
        }
        return JsonWriter.write(Map.of("variants", variants));
    }

    /** Adds the shell's model and its transparent texture (once). */
    static void addShell(@NotNull Map<String, byte[]> files) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("parent", "minecraft:block/cube_all");
        model.put("textures", Map.of("all", "prism:block/shell"));
        files.put(SHELL_MODEL_PATH, JsonWriter.write(model).getBytes(StandardCharsets.UTF_8));
        files.put(SHELL_TEXTURE_PATH, GaugeIcons.encode(new int[GaugeIcons.SIZE * GaugeIcons.SIZE]));
    }
}
