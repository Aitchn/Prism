package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Drives a network every tick (power drain, crafting jobs, saving) from its controller, on the controller's thread. */
record ControllerTickBehavior(@NotNull Ae2Services services) implements BlockTickBehavior {

    static ControllerTickBehavior fromYaml(ConfigSection section, Ae2Services services) throws ContentException {
        section.rejectUnknownKeys(Set.of());
        return new ControllerTickBehavior(services);
    }

    @Override
    public void onTick(@NotNull BlockContext context) {
        services.grid.tickFrom(NodeBehavior.pos(context.custom().block().getLocation()));
    }
}
