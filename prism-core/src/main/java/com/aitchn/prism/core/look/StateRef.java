package com.aitchn.prism.core.look;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/**
 * A vanilla block state by name: the block id and its properties, sorted by property name. Platform independent: the
 * server turns it into block data, the pack turns it into a blockstate variant key.
 */
public record StateRef(@NotNull String block, @NotNull Map<String, String> properties) implements Comparable<StateRef> {

    public StateRef {
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }

    /** {@code minecraft:note_block[instrument=harp,note=0,powered=false]}, the form block data parses. */
    public @NotNull String blockData() {
        return block + (properties.isEmpty() ? "" : "[" + variantKey() + "]");
    }

    /** {@code instrument=harp,note=0,powered=false}, the key of a blockstate variant. */
    public @NotNull String variantKey() {
        StringBuilder out = new StringBuilder();
        properties.forEach((name, value) -> {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append(name).append('=').append(value);
        });
        return out.toString();
    }

    /** Parses {@code block[a=b,c=d]} (the brackets are optional); the block id gets the {@code minecraft:} namespace. */
    public static @NotNull StateRef parse(@NotNull String text) {
        int open = text.indexOf('[');
        String block = open < 0 ? text : text.substring(0, open);
        Map<String, String> properties = new TreeMap<>();
        if (open >= 0) {
            String inner = text.substring(open + 1, text.lastIndexOf(']'));
            for (String pair : inner.split(",")) {
                if (!pair.isEmpty()) {
                    int eq = pair.indexOf('=');
                    properties.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
        }
        return new StateRef(block.contains(":") ? block : "minecraft:" + block, properties);
    }

    @Override
    public int compareTo(@NotNull StateRef other) {
        int byBlock = block.compareTo(other.block);
        return byBlock != 0 ? byBlock : variantKey().compareTo(other.variantKey());
    }
}
