package com.aitchn.prism.plugin;

import com.aitchn.prism.api.item.ItemHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ItemHandler} over a Bukkit inventory: insert stacks onto similar stacks first, then into empty slots; extract
 * the first accepted kind. Furnaces follow hopper rules: the top side fills the input, the other sides the fuel, and
 * only the bottom gives the result.
 */
final class InventoryItemHandler implements ItemHandler {

    private final Inventory inventory;
    private final int[] insertSlots;
    private final int[] extractSlots;

    private InventoryItemHandler(Inventory inventory, int[] insertSlots, int[] extractSlots) {
        this.inventory = inventory;
        this.insertSlots = insertSlots;
        this.extractSlots = extractSlots;
    }

    static InventoryItemHandler allSlots(Inventory inventory) {
        int[] all = IntStream.range(0, inventory.getSize()).toArray();
        return new InventoryItemHandler(inventory, all, all);
    }

    /** {@code side} is the face of the container being asked, as in {@code BlockService.capability}. */
    static InventoryItemHandler forContainer(Inventory inventory, @Nullable BlockFace side) {
        InventoryType type = inventory.getType();
        if (type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER) {
            int[] none = {};
            if (side == BlockFace.UP) {
                return new InventoryItemHandler(inventory, new int[] {0}, none);
            }
            if (side == BlockFace.DOWN) {
                return new InventoryItemHandler(inventory, none, new int[] {2});
            }
            return new InventoryItemHandler(inventory, side == null ? new int[] {0, 1} : new int[] {1},
                    side == null ? new int[] {2} : none);
        }
        return allSlots(inventory);
    }

    @Override
    public @NotNull ItemStack insert(@NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.empty();
        }
        if (inventory.getType() == InventoryType.SHULKER_BOX && Tag.SHULKER_BOXES.isTagged(stack.getType())) {
            return stack.clone();
        }
        int left = stack.getAmount();
        int limit = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());
        // Pass 0 tops up similar stacks, pass 1 fills empty slots.
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int slot : insertSlots) {
                if (left == 0) {
                    break;
                }
                ItemStack there = inventory.getItem(slot);
                boolean empty = there == null || there.isEmpty();
                if (pass == 0 && !empty && there.isSimilar(stack)) {
                    int moved = Math.min(left, limit - there.getAmount());
                    if (moved > 0) {
                        if (!simulate) {
                            there.setAmount(there.getAmount() + moved);
                            inventory.setItem(slot, there);
                        }
                        left -= moved;
                    }
                } else if (pass == 1 && empty && fits(slot, stack)) {
                    int moved = Math.min(left, limit);
                    if (!simulate) {
                        inventory.setItem(slot, stack.asQuantity(moved));
                    }
                    left -= moved;
                }
            }
        }
        return left == 0 ? ItemStack.empty() : stack.asQuantity(left);
    }

    /** Furnace fuel slots only take fuel; everything else takes anything. */
    private boolean fits(int slot, ItemStack stack) {
        InventoryType type = inventory.getType();
        boolean furnace = type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE
                || type == InventoryType.SMOKER;
        return !furnace || slot != 1 || stack.getType().isFuel();
    }

    @Override
    public @NotNull ItemStack extract(@NotNull Predicate<ItemStack> filter, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.empty();
        }
        ItemStack kind = null;
        int taken = 0;
        for (int slot : extractSlots) {
            ItemStack there = inventory.getItem(slot);
            if (there == null || there.isEmpty()) {
                continue;
            }
            if (kind == null) {
                if (!filter.test(there.clone())) {
                    continue;
                }
                kind = there.asOne();
            } else if (!there.isSimilar(kind)) {
                continue;
            }
            int moved = Math.min(amount - taken, there.getAmount());
            if (!simulate) {
                there.setAmount(there.getAmount() - moved);
                inventory.setItem(slot, there.isEmpty() ? null : there);
            }
            taken += moved;
            if (taken == amount) {
                break;
            }
        }
        return kind == null ? ItemStack.empty() : kind.asQuantity(taken);
    }

    @Override
    public @NotNull List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && !stack.isEmpty()) {
                out.add(stack.clone());
            }
        }
        return out;
    }
}
