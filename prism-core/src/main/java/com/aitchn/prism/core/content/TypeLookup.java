package com.aitchn.prism.core.content;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.Behavior;
import com.aitchn.prism.api.content.BehaviorType;
import com.aitchn.prism.api.content.ComponentType;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/** Where the loader finds the component and behavior types referenced by YAML. */
public interface TypeLookup {

    @NotNull Optional<ComponentType<?>> component(@NotNull PrismKey key);

    @NotNull Optional<BehaviorType<? extends Behavior>> behavior(@NotNull PrismKey key);
}
