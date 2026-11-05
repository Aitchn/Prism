package com.aitchn.prism.validation.ae2.crafting;

import com.aitchn.prism.validation.ae2.storage.ItemKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/** Text form of a {@link Pattern}, for item data and for storage. Patterns are separated by a blank line. */
public final class PatternCodec {

    private PatternCodec() {}

    public static @NotNull String encode(@NotNull Pattern pattern) {
        StringBuilder out = new StringBuilder();
        out.append("id\t").append(pattern.id()).append('\n');
        out.append("out\t").append(pattern.output().id()).append('\t').append(pattern.outputCount()).append('\n');
        pattern.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.append("in\t").append(e.getKey().id()).append('\t').append(e.getValue()).append('\n'));
        return out.toString();
    }

    public static @NotNull Optional<Pattern> decode(@NotNull String text) {
        String id = null;
        ItemKey output = null;
        long outputCount = 0;
        Map<ItemKey, Long> inputs = new LinkedHashMap<>();
        try {
            for (String line : text.split("\n")) {
                String[] parts = line.split("\t");
                switch (parts[0]) {
                    case "id" -> id = parts[1];
                    case "out" -> {
                        output = new ItemKey(parts[1]);
                        outputCount = Long.parseLong(parts[2]);
                    }
                    case "in" -> inputs.merge(new ItemKey(parts[1]), Long.parseLong(parts[2]), Long::sum);
                    default -> { }
                }
            }
            if (id == null || output == null) {
                return Optional.empty();
            }
            return Optional.of(new Pattern(id, inputs, output, outputCount));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static @NotNull String encodeAll(@NotNull List<Pattern> patterns) {
        return String.join("\n", patterns.stream().map(PatternCodec::encode).toList());
    }

    public static @NotNull List<Pattern> decodeAll(@NotNull String text) {
        List<Pattern> patterns = new ArrayList<>();
        for (String block : text.split("\n(?=id\t)")) {
            decode(block).ifPresent(patterns::add);
        }
        return patterns;
    }
}
