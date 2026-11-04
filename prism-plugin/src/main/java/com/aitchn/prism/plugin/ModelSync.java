package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.model.BetterModelBridge;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * BetterModel only reads models from its own folder, so Prism keeps generated copies of every active mod's
 * {@code prism/models/*.bbmodel} there, named {@code prism_<namespace>_<name>.bbmodel}. This is the one place Prism
 * writes mod content to disk: the files are owned by Prism, rewritten only when they differ, and removed when the
 * mod goes away. BetterModel is reloaded only when something changed, and its pack is then served next to Prism's.
 */
final class ModelSync implements Listener {

    private static final String PREFIX = "prism_";

    private final Plugin prism;
    private final ModManager mods;
    private final PlatformScheduler scheduler;
    private final PackManager packs;
    private final ModelAnchors anchors;
    private final Logger log;
    private volatile boolean started;
    private volatile com.aitchn.prism.api.block.BlockService selfTestBlocks;
    private volatile PrismKey selfTestKey;
    private volatile boolean handlerRegistered;

    ModelSync(Plugin prism, ModManager mods, PlatformScheduler scheduler, PackManager packs, ModelAnchors anchors) {
        this.prism = prism;
        this.mods = mods;
        this.scheduler = scheduler;
        this.packs = packs;
        this.anchors = anchors;
        this.log = prism.getLogger();
    }

    /** Optional smoke-test hook: after the first sync, place this model block, expect a BetterModel instance, remove it. */
    void selfTest(com.aitchn.prism.api.block.BlockService blocks, PrismKey key) {
        this.selfTestBlocks = blocks;
        this.selfTestKey = key;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        started = true;
        queue();
    }

    /** Called when mods come or go. Before the server has started nothing happens; startup does one sync. */
    void contentChanged() {
        if (started) {
            queue();
        }
    }

    private void queue() {
        scheduler.runAsync(this::sync);
    }

    private synchronized void sync() {
        Plugin betterModel = Bukkit.getPluginManager().getPlugin("BetterModel");
        if (betterModel == null || !betterModel.isEnabled()) {
            log.warning("PRISM_MODELS_SKIPPED BetterModel is not enabled");
            return;
        }
        try {
            Path folder = betterModel.getDataFolder().toPath().resolve("models");
            Files.createDirectories(folder);
            int changed = write(folder);
            registerReloadHandler();
            String reload = "not needed";
            if (changed > 0) {
                reload = BetterModelBridge.reload();
            }
            log.info("PRISM_MODELS_SYNCED changed=" + changed + " reload=" + reload);
            publishBetterModelPack(betterModel);
            anchors.retryWaiting();
            runSelfTest();
        } catch (IOException | RuntimeException | LinkageError e) {
            log.severe("PRISM_MODELS_FAIL " + e);
        }
    }

    private void runSelfTest() {
        var blocks = selfTestBlocks;
        PrismKey key = selfTestKey;
        selfTestBlocks = null;
        if (blocks == null || key == null || Bukkit.getWorlds().isEmpty()) {
            return;
        }
        org.bukkit.Location at = Bukkit.getWorlds().get(0).getSpawnLocation().add(3, 0, 0);
        scheduler.runAt(at, () -> {
            try {
                int before = anchors.activeCount();
                boolean placed = blocks.place(key, at);
                boolean attached = anchors.activeCount() > before;
                boolean removed = blocks.remove(at.getBlock(), false);
                boolean closed = anchors.activeCount() == before;
                log.info((placed && attached && removed && closed ? "PRISM_MODEL_SELFTEST_OK " : "PRISM_MODEL_SELFTEST_FAIL ")
                        + key + " placed=" + placed + " attached=" + attached + " removed=" + removed + " closed=" + closed);
            } catch (RuntimeException | LinkageError e) {
                log.severe("PRISM_MODEL_SELFTEST_FAIL " + key + " " + e);
            }
        });
    }

    private int write(Path folder) throws IOException {
        int changed = 0;
        Set<String> expected = new HashSet<>();
        for (Map.Entry<PrismKey, byte[]> model : mods.models().entrySet()) {
            String name = ModelAnchors.modelName(model.getKey()) + ".bbmodel";
            expected.add(name);
            Path file = folder.resolve(name);
            if (!Files.exists(file) || !Arrays.equals(Files.readAllBytes(file), model.getValue())) {
                Files.write(file, model.getValue());
                changed++;
            }
        }
        try (Stream<Path> existing = Files.list(folder)) {
            for (Path file : existing.toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith(PREFIX) && name.endsWith(".bbmodel") && !expected.contains(name)) {
                    Files.delete(file);
                    changed++;
                }
            }
        }
        return changed;
    }

    private void registerReloadHandler() {
        if (!handlerRegistered) {
            handlerRegistered = true;
            BetterModelBridge.onReloaded(result -> {
                // A manual /bettermodel reload changes the pack and the model set.
                scheduler.runAsync(() -> {
                    Plugin betterModel = Bukkit.getPluginManager().getPlugin("BetterModel");
                    if (betterModel != null) {
                        publishBetterModelPack(betterModel);
                    }
                    anchors.retryWaiting();
                });
            });
        }
    }

    private void publishBetterModelPack(Plugin betterModel) {
        Path zip = betterModel.getDataFolder().toPath().resolve("build.zip");
        if (!Files.isRegularFile(zip)) {
            log.warning("PRISM_MODEL_PACK_MISSING " + zip + " (BetterModel has built no zip pack; check its pack settings)");
            return;
        }
        try {
            packs.publishExternal("bettermodel", Files.readAllBytes(zip));
        } catch (IOException e) {
            log.warning("PRISM_MODEL_PACK_FAIL " + e);
        }
    }
}
