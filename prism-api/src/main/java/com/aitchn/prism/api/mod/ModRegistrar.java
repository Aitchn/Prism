package com.aitchn.prism.api.mod;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/** Explicit mod registration, for plugins whose main class is not a {@link PrismMod}. */
public interface ModRegistrar {

    /** Registers {@code plugin} as a mod. Call from {@code onEnable}; the plugin must depend on Prism. */
    void register(@NotNull Plugin plugin, @NotNull PrismMod mod);
}
