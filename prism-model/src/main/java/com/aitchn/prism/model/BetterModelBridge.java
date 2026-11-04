package com.aitchn.prism.model;

import java.util.function.Consumer;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlugin;
import kr.toxicity.model.api.tracker.Tracker;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * The only class that touches BetterModel. Everything else refers to models by Prism key and to running models by
 * {@link Handle}, so BetterModel stays replaceable.
 */
public final class BetterModelBridge {

    /** A running model instance. Closing it removes it for every viewer. */
    public interface Handle extends AutoCloseable {
        @Override
        void close();
    }

    private BetterModelBridge() {}

    /**
     * Reloads BetterModel's model folder and pack. Blocking and heavy: call it off the tick threads and only when
     * model files actually changed.
     *
     * @return a one-line description of the outcome, starting with {@code OK} or {@code FAILED}
     */
    public static @NotNull String reload() {
        BetterModelPlugin.ReloadResult result = BetterModel.plugin().reload();
        if (result instanceof BetterModelPlugin.ReloadResult.Success success) {
            return "OK models=" + BetterModel.modelKeys().size() + " firstLoad=" + success.firstLoad();
        }
        if (result instanceof BetterModelPlugin.ReloadResult.Failure failure) {
            return "FAILED " + failure.throwable();
        }
        return "FAILED unknown result " + result;
    }

    public static boolean hasModel(@NotNull String name) {
        return BetterModel.modelOrNull(name) != null;
    }

    /** Spawns {@code name} at {@code location} on the calling thread; null if the model is not loaded. */
    public static Handle spawn(@NotNull String name, @NotNull Location location) {
        var renderer = BetterModel.modelOrNull(name);
        if (renderer == null) {
            return null;
        }
        Tracker tracker = renderer.create(location);
        return tracker::close;
    }

    /** Runs {@code onReload} after every BetterModel reload (including its own first load). */
    public static void onReloaded(@NotNull Consumer<String> onReload) {
        BetterModel.plugin().addReloadEndHandler(result -> onReload.accept(
                result instanceof BetterModelPlugin.ReloadResult.Success ? "OK" : "FAILED"));
    }
}
