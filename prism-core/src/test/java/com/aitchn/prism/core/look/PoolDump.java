package com.aitchn.prism.core.look;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the borrowed-family catalogue as JSON for {@code tools/verify-borrowed-families.py}, which checks it against
 * the game's own blockstate files. Run with {@code java -cp <test classes> com.aitchn.prism.core.look.PoolDump out.json}.
 */
public final class PoolDump {

    private PoolDump() {}

    public static void main(String[] args) throws IOException {
        List<String> families = new ArrayList<>();
        for (BorrowedFamily family : LookPool.families()) {
            List<StateRef> all = LookPool.defaultStates(family);
            List<String> states = new ArrayList<>();
            for (StateRef state : all) {
                ModelRef model = family.vanillaModel(state);
                states.add("{\"state\":" + q(state.variantKey()) + ",\"model\":" + q(model.model()) + ",\"x\":" + model.x()
                        + ",\"y\":" + model.y() + ",\"canonical\":" + q(family.canonical(state).variantKey()) + "}");
            }
            List<String> borrowed = new ArrayList<>();
            family.borrowable().forEach(state -> borrowed.add(q(state.variantKey())));
            families.add("{\"block\":" + q(family.block()) + ",\"class\":" + q(family.physics().id()) + ",\"borrowable\":["
                    + String.join(",", borrowed) + "],\"shell\":" + family.shell().map(s -> q(s.variantKey())).orElse("null")
                    + ",\"states\":[" + String.join(",", states) + "]}");
        }
        Files.writeString(Path.of(args[0]), "[" + String.join(",\n", families) + "]\n", StandardCharsets.UTF_8);
    }

    private static String q(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
