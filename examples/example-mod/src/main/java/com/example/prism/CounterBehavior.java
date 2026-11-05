package com.example.prism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockDropBehavior;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockNeighborBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;
import java.util.function.IntSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A block that counts: one per {@code interval} ticks, stored in its per-block data so it survives restarts. It
 * counts neighbour changes too and offers its count to neighbours as a capability. Right click opens
 * {@link CounterMenu}: a live count, a button switching between the {@code idle} and {@code active} looks, and one
 * slot that stores an item in the block (dropped when the block is broken). A click while holding a block passes the
 * click on, so the block is placed against the counter as with any vanilla block. Broken in survival, the dropped counter keeps
 * its tick count, and placing it again continues from there.
 */
public record CounterBehavior(int interval) implements BlockPlaceBehavior, BlockTickBehavior, BlockNeighborBehavior,
        BlockInteractBehavior, BlockDropBehavior, BlockRemoveBehavior, BlockCapabilityProvider {

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
        PersistentDataContainer data = context.custom().data();
        data.set(PLACED, PersistentDataType.LONG, System.currentTimeMillis());
        ItemStack from = context.placedFrom();
        Integer carried = from == null ? null : from.getPersistentDataContainer().get(TICKS, PersistentDataType.INTEGER);
        if (carried != null) {
            data.set(TICKS, PersistentDataType.INTEGER, carried);
        }
    }

    @Override
    public void onDrop(@NotNull BlockContext context, @NotNull ItemStack drop) {
        int ticks = context.custom().data().getOrDefault(TICKS, PersistentDataType.INTEGER, 0);
        if (ticks > 0) {
            Prism.items().editData(drop, data -> data.set(TICKS, PersistentDataType.INTEGER, ticks));
        }
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
        ItemStack held = context.heldItem();
        if (held != null && held.getType().isBlock() && !held.isEmpty()) {
            context.pass(); // holding a block: place it against the counter, as with a vanilla block
            return;
        }
        CounterMenu menu = CounterMenu.of(context.custom());
        if (menu == null) {
            context.player().sendActionBar(Component.text("Someone else is using this counter"));
        } else {
            Prism.menus().open(context.player(), menu);
        }
        context.cancel();
    }

    @Override
    public void onRemove(@NotNull BlockContext context) {
        CustomBlock custom = context.custom();
        byte[] stored = custom.data().get(CounterMenu.STORED, PersistentDataType.BYTE_ARRAY);
        // Always tell an open menu, even with nothing stored: the viewer may put an item in before it closes.
        boolean viewed = CounterMenu.blockRemoved(custom.block().getLocation());
        if (stored != null && !viewed) {
            custom.block().getWorld().dropItemNaturally(custom.block().getLocation().toCenterLocation(),
                    ItemStack.deserializeBytes(stored));
        }
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
