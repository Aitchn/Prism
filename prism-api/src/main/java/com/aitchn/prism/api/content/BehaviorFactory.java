package com.aitchn.prism.api.content;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface BehaviorFactory<B extends Behavior> {

    @NotNull B create(@NotNull ConfigSection section) throws ContentException;
}
