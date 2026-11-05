package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.validation.ae2.runtime.Ae2State;
import com.aitchn.prism.validation.ae2.runtime.GridManager;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/** Everything the block behaviors and menus share, created once when Prism initializes the mod. */
final class Ae2Services {

    final JavaPlugin plugin;
    final Ae2State state;
    final GridManager grid;
    final Ae2Ui ui;
    final NamespacedKey cellIdKey;
    final NamespacedKey patternKey;

    Ae2Services(@NotNull JavaPlugin plugin, @NotNull Ae2State state) {
        this.plugin = plugin;
        this.state = state;
        this.grid = new GridManager(state);
        this.cellIdKey = new NamespacedKey(plugin, "cell_id");
        this.patternKey = new NamespacedKey(plugin, "pattern");
        this.ui = new Ae2Ui(this);
    }
}
