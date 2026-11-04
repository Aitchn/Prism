package com.aitchn.prism.api.mod;

import org.jetbrains.annotations.NotNull;

/**
 * Entry point of a mod. Implement it on the plugin's main class, or pass an instance to
 * {@link ModRegistrar#register}. Lifecycle: {@code onInitialize} (register types), content is read and validated,
 * registrations are published atomically, then {@code onContentLoaded}.
 */
public interface PrismMod {

    void onInitialize(@NotNull ModContext context);

    default void onContentLoaded(@NotNull ContentView view) {}

    default void onDisable() {}
}
