package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.menu.ClickKind;
import com.aitchn.prism.api.menu.ContainerMenu;
import com.aitchn.prism.api.menu.MenuView;
import com.aitchn.prism.api.menu.SlotRole;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.runtime.DriveState;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The drive's GUI. Cells are shown as buttons (clicking one takes it out with its current contents, so a cell that the
 * network changed meanwhile never goes out stale) and put in through one input slot.
 */
final class DriveMenu implements ContainerMenu {

    private static final int[] CELL_SLOTS = {2, 3, 4, 5, 6, 11, 12, 13, 14, 15};
    private static final int INSERT = 22;
    private static final PrismKey EMPTY = new PrismKey("minecraft", "gray_stained_glass_pane");

    private final Ae2Services services;
    private final GridPos pos;
    private final DriveState drive;

    DriveMenu(Ae2Services services, GridPos pos, DriveState drive) {
        this.services = services;
        this.pos = pos;
        this.drive = drive;
    }

    @Override
    public @NotNull String title() {
        return "<dark_aqua>ME Drive";
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public @NotNull SlotRole role(int slot) {
        return slot == INSERT ? SlotRole.INPUT : SlotRole.BUTTON;
    }

    @Override
    public boolean accepts(int slot, @NotNull ItemStack stack) {
        return Prism.items().identify(stack).flatMap(Ae2Content::cellTier).isPresent();
    }

    @Override
    public void onOpen(@NotNull MenuView view) {
        draw(view);
        view.setIcon(INSERT - 1, new PrismKey("minecraft", "hopper"), "<green>Put a storage cell below",
                List.of("<gray>Shift-click a cell from your inventory"));
    }

    @Override
    public int refreshTicks() {
        return 20;
    }

    @Override
    public void onRefresh(@NotNull MenuView view) {
        draw(view);
    }

    @Override
    public void onButton(@NotNull MenuView view, int slot, @NotNull ClickKind click) {
        for (int index = 0; index < CELL_SLOTS.length; index++) {
            if (CELL_SLOTS[index] == slot) {
                int cellSlot = index;
                drive.eject(cellSlot).ifPresent(stack -> give(view, stack));
                changed();
                draw(view);
            }
        }
    }

    @Override
    public void onSlotChanged(@NotNull MenuView view, int slot) {
        if (slot != INSERT) {
            return;
        }
        ItemStack stack = view.item(INSERT);
        if (stack == null) {
            return;
        }
        int free = drive.firstFree();
        if (free < 0 || !drive.bind(free, stack)) {
            return; // stays in the slot; the player can take it back
        }
        ItemStack rest = stack.clone();
        rest.setAmount(stack.getAmount() - 1);
        view.setItem(INSERT, rest.getAmount() > 0 ? rest : null);
        changed();
        draw(view);
    }

    @Override
    public void onClose(@NotNull MenuView view) {
        ItemStack left = view.item(INSERT);
        if (left != null) {
            view.setItem(INSERT, null);
            give(view, left);
        }
    }

    private void changed() {
        services.grid.refresh();
        services.grid.persist(pos, drive::write);
    }

    private void draw(MenuView view) {
        for (int index = 0; index < CELL_SLOTS.length; index++) {
            int slot = CELL_SLOTS[index];
            StorageCell cell = drive.cell(index).orElse(null);
            if (cell == null) {
                view.setIcon(slot, EMPTY, "<gray>Empty slot", List.of());
            } else {
                view.setIcon(slot, Ae2Content.cellKey(cell.tier()), "<white>" + cell.tier().name() + " storage cell",
                        List.of("<gray>" + cell.usedBytes() + "/" + cell.tier().bytes() + " bytes, " + cell.types()
                                + " types", "<red>Click to take out"));
            }
        }
    }

    private static void give(MenuView view, ItemStack stack) {
        view.player().getInventory().addItem(stack).values()
                .forEach(rest -> view.player().getWorld().dropItem(view.player().getLocation(), rest));
    }
}
