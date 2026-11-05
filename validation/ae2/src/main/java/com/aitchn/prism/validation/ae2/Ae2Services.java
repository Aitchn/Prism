package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.validation.ae2.runtime.GridManager;
import org.jetbrains.annotations.NotNull;

/** Everything the behaviors and menus share, created once when Prism initializes the mod. */
final class Ae2Services {

    final GridManager grid = new GridManager();
    final Ae2Ui ui;

    Ae2Services(@NotNull org.bukkit.plugin.java.JavaPlugin plugin) {
        this.ui = new Ae2Ui(this);
    }
}
