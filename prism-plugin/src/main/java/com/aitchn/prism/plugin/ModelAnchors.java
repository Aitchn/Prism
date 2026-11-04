package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.model.BetterModelBridge;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Blocks drawn by BetterModel models. The persistent anchor entity (the block's display entity, without an item)
 * survives restarts; the BetterModel instance does not, so it is re-created whenever the anchor's chunk loads or the
 * models are reloaded, and closed when the chunk unloads.
 */
final class ModelAnchors implements Listener {

    private final NamespacedKey markerKey;
    private final Registry<BlockDefinition> definitions;
    private final PlatformScheduler scheduler;
    private final Logger log;
    private final Map<UUID, BetterModelBridge.Handle> active = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> waiting = new ConcurrentHashMap<>();

    ModelAnchors(NamespacedKey markerKey, Registry<BlockDefinition> definitions, PlatformScheduler scheduler, Logger log) {
        this.markerKey = markerKey;
        this.definitions = definitions;
        this.scheduler = scheduler;
        this.log = log;
    }

    /** BetterModel's name for a Prism model key. Also the file name (without extension) written by the sync. */
    static String modelName(PrismKey model) {
        return "prism_" + model.namespace() + "_" + model.value().replace('/', '_');
    }

    static Optional<PrismKey> modelOf(BlockDefinition definition) {
        return definition.components().get(PrismComponents.MODEL_REF).map(PrismComponents.ModelRef::model);
    }

    int activeCount() {
        return active.size();
    }

    /** Starts the model for {@code anchor}; if BetterModel does not have it yet the anchor waits for a reload. */
    void attach(Entity anchor, PrismKey block) {
        if (active.containsKey(anchor.getUniqueId())) {
            return;
        }
        Optional<PrismKey> model = definitions.get(block).flatMap(ModelAnchors::modelOf);
        if (model.isEmpty()) {
            return;
        }
        try {
            BetterModelBridge.Handle handle = BetterModelBridge.spawn(modelName(model.get()), anchor.getLocation());
            if (handle == null) {
                waiting.put(anchor.getUniqueId(), anchor);
                log.info("PRISM_MODEL_WAITING " + block + " model=" + modelName(model.get()));
            } else {
                waiting.remove(anchor.getUniqueId());
                active.put(anchor.getUniqueId(), handle);
                log.info("PRISM_MODEL_ATTACHED " + block + " model=" + modelName(model.get()));
            }
        } catch (RuntimeException | LinkageError e) {
            log.warning("PRISM_MODEL_ATTACH_FAIL " + block + " " + e);
        }
    }

    void detach(UUID anchorId) {
        waiting.remove(anchorId);
        BetterModelBridge.Handle handle = active.remove(anchorId);
        if (handle != null) {
            handle.close();
        }
    }

    /** Retries every anchor that was waiting for models, each on the thread that owns it. */
    void retryWaiting() {
        for (Entity anchor : waiting.values()) {
            scheduler.runOn(anchor, () -> {
                String raw = anchor.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
                if (raw != null) {
                    attach(anchor, PrismKey.parse(raw));
                }
            }, () -> waiting.remove(anchor.getUniqueId()));
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof ItemDisplay) {
                String raw = entity.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
                if (raw != null) {
                    attach(entity, PrismKey.parse(raw));
                }
            }
        }
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (active.containsKey(entity.getUniqueId()) || waiting.containsKey(entity.getUniqueId())) {
                detach(entity.getUniqueId());
            }
        }
    }
}
