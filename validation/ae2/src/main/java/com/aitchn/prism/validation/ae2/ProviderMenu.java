package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.menu.ContainerMenu;
import com.aitchn.prism.api.menu.MenuView;
import com.aitchn.prism.api.menu.SlotRole;
import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.crafting.PatternCodec;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * The pattern provider's slots: nine input slots that only accept encoded patterns. The patterns live in the block's
 * own data; every change is written there and the provider's patterns are reloaded. One viewer per block, because two
 * viewers editing the same slot could duplicate a pattern.
 */
final class ProviderMenu implements ContainerMenu {

    static final int SLOTS = 9;

    private static final Map<Location, ProviderMenu> OPEN = new ConcurrentHashMap<>();

    private final Ae2Services services;
    private final Location at;
    private final ItemStack[] initial;
    private volatile boolean removed;

    private ProviderMenu(Ae2Services services, Location at, ItemStack[] initial) {
        this.services = services;
        this.at = at;
        this.initial = initial;
    }

    /** Builds the menu from the block; call on the block's thread. Null if someone else has it open. */
    static ProviderMenu of(Ae2Services services, CustomBlock block) {
        Location at = block.block().getLocation();
        ProviderMenu menu = new ProviderMenu(services, at, storedStacks(block.data()).toArray(new ItemStack[0]));
        return OPEN.putIfAbsent(at, menu) == null ? menu : null;
    }

    /** True when a menu is open on the block at {@code at}; its viewer then receives the items on close. */
    static boolean blockRemoved(Location at) {
        ProviderMenu menu = OPEN.get(at);
        if (menu == null) {
            return false;
        }
        menu.removed = true;
        return true;
    }

    static List<ItemStack> storedStacks(PersistentDataContainer data) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            byte[] bytes = data.get(Ae2Content.slotKey("pat", slot), PersistentDataType.BYTE_ARRAY);
            if (bytes != null) {
                stacks.add(ItemStack.deserializeBytes(bytes));
            }
        }
        return stacks;
    }

    static List<Pattern> readPatterns(PersistentDataContainer data) {
        List<Pattern> patterns = new ArrayList<>();
        for (ItemStack stack : storedStacks(data)) {
            readPattern(stack).ifPresent(patterns::add);
        }
        return patterns;
    }

    static Optional<Pattern> readPattern(ItemStack stack) {
        String text = stack.getPersistentDataContainer().get(Ae2Content.PATTERN, PersistentDataType.STRING);
        return text == null ? Optional.empty() : PatternCodec.decode(text);
    }

    @Override
    public @NotNull String title() {
        return "<dark_aqua>Pattern Provider";
    }

    @Override
    public int rows() {
        return 1;
    }

    @Override
    public @NotNull SlotRole role(int slot) {
        return SlotRole.INPUT;
    }

    @Override
    public boolean accepts(int slot, @NotNull ItemStack stack) {
        return readPattern(stack).isPresent();
    }

    @Override
    public void onOpen(@NotNull MenuView view) {
        for (int slot = 0; slot < initial.length && slot < SLOTS; slot++) {
            view.setItem(slot, initial[slot]);
        }
    }

    @Override
    public void onSlotChanged(@NotNull MenuView view, int slot) {
        byte[][] bytes = new byte[SLOTS][];
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = view.item(i);
            bytes[i] = stack == null ? null : stack.serializeAsBytes();
        }
        Prism.scheduler().runAt(at, () -> Prism.blocks().get(at.getBlock()).ifPresent(block -> {
            for (int i = 0; i < SLOTS; i++) {
                if (bytes[i] == null) {
                    block.data().remove(Ae2Content.slotKey("pat", i));
                } else {
                    block.data().set(Ae2Content.slotKey("pat", i), PersistentDataType.BYTE_ARRAY, bytes[i]);
                }
            }
            services.grid.setPatterns(new GridPos(at.getWorld().getName(), at.getBlockX(), at.getBlockY(),
                    at.getBlockZ()), readPatterns(block.data()));
        }));
    }

    @Override
    public void onClose(@NotNull MenuView view) {
        OPEN.remove(at, this);
        if (!removed) {
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            ItemStack left = view.item(i);
            if (left != null) {
                view.setItem(i, null);
                view.player().getInventory().addItem(left).values()
                        .forEach(rest -> view.player().getWorld().dropItem(view.player().getLocation(), rest));
            }
        }
    }
}
