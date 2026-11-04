package com.aitchn.prism.testkit;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.core.content.ContentSource;
import com.aitchn.prism.core.content.PathContentSource;
import com.aitchn.prism.core.mod.ModCandidate;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.core.mod.ModReport;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Loads a mod's content through the real Prism lifecycle without a server: types are registered, YAML is decoded and
 * validated, and the resulting registries can be queried. Every vanilla item id is accepted as a base item.
 *
 * <pre>{@code
 * var harness = ModTestHarness.load("examplemod", new ExampleMod(), Path.of("src/main/resources"));
 * assertTrue(harness.report().active(), harness.report().problems().toString());
 * }</pre>
 */
public final class ModTestHarness {

    private final ModManager manager;
    private final ModReport report;

    private ModTestHarness(ModManager manager, ModReport report) {
        this.manager = manager;
        this.report = report;
    }

    /** Loads content from a directory that contains {@code prism/} (usually {@code src/main/resources}). */
    public static @NotNull ModTestHarness load(@NotNull String namespace, @NotNull PrismMod mod, @NotNull Path resources) {
        return load(namespace, mod, new PathContentSource(resources));
    }

    public static @NotNull ModTestHarness load(@NotNull String namespace, @NotNull PrismMod mod, @NotNull ContentSource source) {
        ModManager manager = new ModManager(key -> key.namespace().equals(PrismKey.MINECRAFT_NAMESPACE));
        ModReport report = manager.activate(
                new ModCandidate(namespace, namespace, "prism", source, mod, Logger.getLogger("prism-test")));
        return new ModTestHarness(manager, report);
    }

    public @NotNull ModReport report() {
        return report;
    }

    public @NotNull ModManager manager() {
        return manager;
    }

    public @NotNull ItemDefinition item(@NotNull String key) {
        return manager.items().require(PrismKey.parse(key));
    }
}
