package com.example.prism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockNeighborBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;
import java.util.function.IntSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A block that counts: one per {@code interval} ticks, stored in its per-block data so it survives restarts. It
 * counts neighbour changes too, offers its count to neighbours as a capability, and on right click switches between
 * its {@code idle} and {@code active} looks and shows its numbers.
 */
public record CounterBehavior(int interval) implements BlockPlaceBehavior, BlockTickBehavior, BlockNeighborBehavior,
        BlockInteractBehavior, BlockCapabilityProvider {

    /** What this block offers to its neighbours. A mod-defined capability: Prism only does the lookup. */
    public static final BlockCapability<IntSupplier> COUNT =
            BlockCapability.of(new PrismKey("examplemod", "count"), IntSupplier.class);

    public static final NamespacedKey TICKS = NamespacedKey.fromString("examplemod:ticks");
    public static final NamespacedKey NEIGHBORS = NamespacedKey.fromString("examplemod:neighbors");
    public static final NamespacedKey PLACED = NamespacedKey.fromString("examplemod:placed");

    static CounterBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of("interval"));
        int interval = section.getInt("interval").orElse(20);
        if (interval < 1) {
            throw section.error("interval must be at least 1");
        }
        return new CounterBehavior(interval);
    }

    @Override
    public int tickInterval() {
        return interval;
    }

    @Override
    public void onPlace(@NotNull BlockContext context) {
        context.custom().data().set(PLACED, PersistentDataType.LONG, System.currentTimeMillis());
    }

    @Override
    public void onTick(@NotNull BlockContext context) {
        increment(context.custom().data(), TICKS);
    }

    @Override
    public void onNeighborChanged(@NotNull BlockContext context, @NotNull BlockFace side) {
        increment(context.custom().data(), NEIGHBORS);
    }

    @Override
    public void onInteract(@NotNull BlockContext context) {
        if (context.player() == null) {
            return;
        }
        CustomBlock custom = context.custom();
        String next = "active".equals(custom.variant()) ? "idle" : "active";
        Prism.blocks().setVariant(custom.block(), next);
        PersistentDataContainer data = custom.data();
        context.player().sendActionBar(Component.text("Counter " + next + ", facing " + custom.facing()
                + ": " + data.getOrDefault(TICKS, PersistentDataType.INTEGER, 0) + " counts, "
                + data.getOrDefault(NEIGHBORS, PersistentDataType.INTEGER, 0) + " neighbour changes"));
        context.cancel();
    }

    @Override
    public <T> @Nullable T capability(
            @NotNull CustomBlock block, @Nullable BlockFace side, @NotNull BlockCapability<T> capability) {
        if (!capability.equals(COUNT)) {
            return null;
        }
        PersistentDataContainer data = block.data();
        IntSupplier count = () -> data.getOrDefault(TICKS, PersistentDataType.INTEGER, 0);
        return capability.type().cast(count);
    }

    private static void increment(PersistentDataContainer data, NamespacedKey key) {
        data.set(key, PersistentDataType.INTEGER, data.getOrDefault(key, PersistentDataType.INTEGER, 0) + 1);
    }
}
