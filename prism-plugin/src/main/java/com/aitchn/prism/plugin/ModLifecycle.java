package com.aitchn.prism.plugin;

import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.ModRegistrar;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.core.mod.ApiVersion;
import com.aitchn.prism.core.mod.ModCandidate;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.core.mod.ModManifest;
import com.aitchn.prism.core.mod.ModReport;
import java.io.IOException;
import java.util.Locale;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Connects Bukkit plugins to the mod lifecycle. A plugin that depends on Prism is a mod when its main class
 * implements {@link PrismMod}, when it calls {@link #register}, or when its jar contains a {@code prism/} folder.
 */
final class ModLifecycle implements ModRegistrar, Listener {

    static final ApiVersion API_VERSION = new ApiVersion(0, 1);
    private static final String DEFAULT_CONTENT_ROOT = "prism";

    private final Plugin prism;
    private final ModManager manager;
    private final Runnable onContentChanged;

    ModLifecycle(Plugin prism, ModManager manager, Runnable onContentChanged) {
        this.prism = prism;
        this.manager = manager;
        this.onContentChanged = onContentChanged;
    }

    @Override
    public void register(@NotNull Plugin plugin, @NotNull PrismMod mod) {
        if (!manager.isActive(plugin.getName())) {
            activate(plugin, mod);
        }
    }

    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        Plugin plugin = event.getPlugin();
        if (plugin == prism || manager.report(plugin.getName()).isPresent() || !dependsOnPrism(plugin)) {
            return;
        }
        if (plugin instanceof PrismMod mod) {
            activate(plugin, mod);
        } else if (hasContent(plugin)) {
            activate(plugin, new PrismMod() {
                @Override
                public void onInitialize(@NotNull ModContext context) {}
            });
        }
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (manager.isActive(event.getPlugin().getName())) {
            manager.deactivate(event.getPlugin().getName());
            onContentChanged.run();
        }
    }

    private void activate(Plugin plugin, PrismMod mod) {
        String id = plugin.getName();
        try (JarContent jar = JarContent.open(plugin)) {
            ModManifest manifest = jar.source().exists(ModManifest.FILE_NAME)
                    ? ModManifest.parse(jar.source().readText(ModManifest.FILE_NAME))
                    : ModManifest.NONE;
            if (manifest.prismApi().isPresent()
                    && !API_VERSION.satisfies(ApiVersion.parse(manifest.prismApi().get()))) {
                prism.getLogger().severe("PRISM_MOD_FAILED " + id + " needs Prism API " + manifest.prismApi().get()
                        + " but this Prism provides " + API_VERSION);
                return;
            }
            String namespace = manifest.namespace().orElseGet(() -> defaultNamespace(id));
            String root = manifest.contentPath().orElse(DEFAULT_CONTENT_ROOT);
            ModReport report = manager.activate(
                    new ModCandidate(id, namespace, root, jar.source(), mod, plugin.getLogger()));
            if (report.active()) {
                prism.getLogger().info("PRISM_MOD_ACTIVE " + id + " items=" + report.itemCount() + " blocks=" + report.blockCount());
                onContentChanged.run();
            } else {
                prism.getLogger().severe("PRISM_MOD_FAILED " + id + " (nothing was registered):");
                report.problems().forEach(problem -> prism.getLogger().severe("  " + problem));
            }
        } catch (IOException | ContentException | IllegalArgumentException e) {
            prism.getLogger().severe("PRISM_MOD_FAILED " + id + ": " + e.getMessage());
        }
    }

    private static String defaultNamespace(String pluginName) {
        return pluginName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private boolean dependsOnPrism(Plugin plugin) {
        var meta = plugin.getPluginMeta();
        return meta.getPluginDependencies().contains(prism.getName())
                || meta.getPluginSoftDependencies().contains(prism.getName());
    }

    private static boolean hasContent(Plugin plugin) {
        try (JarContent jar = JarContent.open(plugin)) {
            return jar.source().exists(ModManifest.FILE_NAME) || !jar.source().list(DEFAULT_CONTENT_ROOT).isEmpty();
        } catch (IOException e) {
            return false;
        }
    }
}
