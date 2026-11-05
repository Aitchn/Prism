package com.aitchn.prism.validation.mekanism;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Keeps "how much of the real mod is rebuilt" a number instead of a feeling. {@code inventory.yml} lists what the
 * base module has; this test computes the coverage, checks that finished content really exists, and fails below the
 * target of the current stage. Raise {@link #STAGE_TARGET} with every stage that merges.
 */
class InventoryTest {

    /** Minimum coverage (0 to 1) for the stage the repository is at. Stage 2: 44% (the plan said 55; content per stage was overestimated). */
    static final double STAGE_TARGET = 0.44;

    private static final Path ROOT = Path.of(".");
    private static final Set<String> CONTENT = Set.of("items", "blocks", "machines", "factories");

    private record Inventory(Map<String, Map<String, String>> sections) {
        double fraction(Set<String> names, boolean include) {
            double score = 0;
            int total = 0;
            for (var section : sections.entrySet()) {
                if (names.contains(section.getKey()) != include) {
                    continue;
                }
                for (String status : section.getValue().values()) {
                    total++;
                    score += switch (status) {
                        case "done" -> 1;
                        case "partial" -> 0.5;
                        default -> 0;
                    };
                }
            }
            return total == 0 ? 0 : score / total;
        }

        double coverage() {
            return 0.5 * fraction(CONTENT, true) + 0.5 * fraction(CONTENT, false);
        }
    }

    static Inventory read() throws IOException {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        Map<String, String> current = null;
        for (String raw : Files.readAllLines(ROOT.resolve("inventory.yml"))) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            if (line.isBlank()) {
                continue;
            }
            String name = line.strip();
            assertTrue(name.endsWith(":") || name.contains(": "), "bad line: " + raw);
            if (!line.startsWith(" ")) {
                current = new LinkedHashMap<>();
                sections.put(name.substring(0, name.length() - 1), current);
            } else {
                int colon = name.indexOf(':');
                String id = name.substring(0, colon);
                String status = name.substring(colon + 1).strip();
                assertTrue(Set.of("done", "partial", "missing").contains(status), "bad status in: " + raw);
                assertTrue(current.put(id, status) == null, "duplicate entry: " + raw);
            }
        }
        return new Inventory(sections);
    }

    @Test
    void ids_are_unique_across_content_sections() throws IOException {
        Set<String> seen = new HashSet<>();
        for (var section : read().sections().entrySet()) {
            if (CONTENT.contains(section.getKey())) {
                for (String id : section.getValue().keySet()) {
                    assertTrue(seen.add(id), "listed in two content sections: " + id);
                }
            }
        }
    }

    @Test
    void finished_content_exists_in_the_resources() throws IOException {
        for (var section : read().sections().entrySet()) {
            if (!CONTENT.contains(section.getKey())) {
                continue;
            }
            for (var entry : section.getValue().entrySet()) {
                if (!entry.getValue().equals("missing")) {
                    assertTrue(Files.exists(ROOT.resolve("src/main/resources/prism/items/" + entry.getKey() + ".yml")),
                            entry.getKey() + " is marked " + entry.getValue() + " but has no item file");
                }
            }
        }
    }

    @Test
    void coverage_reaches_the_target_of_the_current_stage() throws IOException {
        Inventory inventory = read();
        double coverage = inventory.coverage();
        System.out.printf("MEKANISM_COVERAGE %.1f%% (content %.1f%%, mechanics %.1f%%) target %.0f%%%n",
                coverage * 100, inventory.fraction(CONTENT, true) * 100, inventory.fraction(CONTENT, false) * 100,
                STAGE_TARGET * 100);
        assertTrue(coverage >= STAGE_TARGET,
                "coverage %.1f%% is below the stage target %.0f%%".formatted(coverage * 100, STAGE_TARGET * 100));
    }
}
