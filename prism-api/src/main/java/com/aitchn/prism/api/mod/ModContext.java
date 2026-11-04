package com.aitchn.prism.api.mod;

import com.aitchn.prism.api.content.Behavior;
import com.aitchn.prism.api.content.BehaviorFactory;
import com.aitchn.prism.api.content.BehaviorType;
import com.aitchn.prism.api.content.ComponentDecoder;
import com.aitchn.prism.api.content.ComponentType;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

/** Handed to {@link PrismMod#onInitialize}: what a mod may register before its content is read. */
public interface ModContext {

    /** The mod's namespace (default: the lower-cased plugin name). */
    @NotNull String namespace();

    @NotNull Logger logger();

    /** Registers {@code namespace:path} as a component type usable in this and later-loaded mods' YAML. */
    <T> @NotNull ComponentType<T> registerComponent(
            @NotNull String path, @NotNull Class<T> valueType, @NotNull ComponentDecoder<T> decoder);

    /** Registers {@code namespace:path} as a behavior type usable in YAML. */
    <B extends Behavior> @NotNull BehaviorType<B> registerBehavior(
            @NotNull String path, @NotNull Class<B> behaviorType, @NotNull BehaviorFactory<B> factory);
}
