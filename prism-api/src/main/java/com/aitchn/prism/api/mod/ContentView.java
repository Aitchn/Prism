package com.aitchn.prism.api.mod;

import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.registry.RegistrySnapshot;
import org.jetbrains.annotations.NotNull;

/** The frozen content of one mod, passed to {@link PrismMod#onContentLoaded}. */
public interface ContentView {

    @NotNull RegistrySnapshot<ItemDefinition> items();
}
