package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Lets other blocks charge the ME network through {@link Ae2Content#ENERGY_SINK}. */
record EnergyCellBehavior(@NotNull Ae2Services services) implements BlockCapabilityProvider {

    static EnergyCellBehavior fromYaml(ConfigSection section, Ae2Services services) throws ContentException {
        section.rejectUnknownKeys(Set.of());
        return new EnergyCellBehavior(services);
    }

    @Override
    public <T> @Nullable T capability(@NotNull CustomBlock block, @Nullable BlockFace side,
            @NotNull BlockCapability<T> capability) {
        if (!capability.equals(Ae2Content.ENERGY_SINK)) {
            return null;
        }
        var pos = NodeBehavior.pos(block.block().getLocation());
        EnergySink sink = amount -> services.grid.networkAt(pos)
                .map(runtime -> runtime.power().inject(runtime.topology(), amount)).orElse(0.0);
        return capability.type().cast(sink);
    }
}
