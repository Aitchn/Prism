package com.aitchn.prism.api.recipe;

import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import org.jetbrains.annotations.NotNull;

/** Turns one recipe file (without its {@code type} key) into a recipe value. */
@FunctionalInterface
public interface RecipeDecoder<R> {

    /** @param namespace the namespace of the mod the file belongs to, the default for unqualified keys */
    @NotNull R decode(@NotNull ConfigSection section, @NotNull String namespace) throws ContentException;
}
