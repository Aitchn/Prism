package com.aitchn.validation.botania;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.validation.botania.logic.Recipes;
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
 * mod has; this test computes the coverage, checks that finished content really exists, and fails below the target of
 * the current stage. Raise {@link #STAGE_TARGET} with every stage that merges.
 */
class InventoryTest {

    /** Minimum coverage (0 to 1) for the stage the repository is at. Stage 0: the migrated first slice. */
    static final double STAGE_TARGET = 0.13;

    private static final Path ROOT = Path.of(".");
    private static final Set<String> MECHANICS = Set.of("mechanics");

    private record Inventory(Map<String, Map<String, String>> sections) {
        double fraction(boolean mechanics) {
            double score = 0;
            int total = 0;
            for (var section : sections.entrySet()) {
                if (MECHANICS.contains(section.getKey()) != mechanics) {
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
            return 0.5 * fraction(false) + 0.5 * fraction(true);
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
            if (!MECHANICS.contains(section.getKey())) {
                for (String id : section.getValue().keySet()) {
                    assertTrue(seen.add(id), "listed in two content sections: " + id);
                }
            }
        }
    }

    @Test
    void finished_content_exists_in_the_resources() throws IOException {
        for (var section : read().sections().entrySet()) {
            if (MECHANICS.contains(section.getKey())) {
                continue;
            }
            for (var entry : section.getValue().entrySet()) {
                if (entry.getValue().equals("missing")) {
                    continue;
                }
                String id = entry.getKey();
                if (id.endsWith("_@")) {
                    for (String dye : Recipes.DYES) {
                        assertItem(id.substring(0, id.length() - 1) + dye, entry.getValue());
                    }
                } else {
                    assertItem(id, entry.getValue());
                }
            }
        }
    }

    private static void assertItem(String id, String status) {
        assertTrue(Files.exists(ROOT.resolve("src/main/resources/prism/items/" + id + ".yml")),
                id + " is marked " + status + " but has no item file");
    }

    @Test
    void coverage_reaches_the_target_of_the_current_stage() throws IOException {
        Inventory inventory = read();
        double coverage = inventory.coverage();
        System.out.printf("BOTANIA_COVERAGE %.1f%% (content %.1f%%, mechanics %.1f%%) target %.0f%%%n",
                coverage * 100, inventory.fraction(false) * 100, inventory.fraction(true) * 100, STAGE_TARGET * 100);
        assertTrue(coverage >= STAGE_TARGET,
                "coverage %.1f%% is below the stage target %.0f%%".formatted(coverage * 100, STAGE_TARGET * 100));
    }
}
