package com.aitchn.validation.botania;

import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.validation.botania.logic.ManaCollector;
import java.util.function.Supplier;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Answers {@link ManaCapabilities#MANA} for pools and spreaders, so bursts and other mods find them through Prism. */
record ManaProvider(Supplier<BotaniaRuntime> runtime) implements BlockCapabilityProvider {

    @Override
    public <T> @Nullable T capability(
            @NotNull CustomBlock block, @Nullable BlockFace side, @NotNull BlockCapability<T> capability) {
        if (!capability.equals(ManaCapabilities.MANA)) {
            return null;
        }
        ManaCollector collector = runtime.get().network().collector(BukkitWorld.pos(block.block())).orElse(null);
        return collector == null ? null : capability.type().cast(collector);
    }
}
