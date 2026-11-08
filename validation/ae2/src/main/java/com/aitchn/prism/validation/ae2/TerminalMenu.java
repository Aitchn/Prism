package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.menu.ClickKind;
import com.aitchn.prism.api.menu.ContainerMenu;
import com.aitchn.prism.api.menu.MenuView;
import com.aitchn.prism.api.menu.SlotRole;
import com.aitchn.prism.validation.ae2.runtime.NetworkRuntime;
import com.aitchn.prism.validation.ae2.runtime.StackCodec;
import com.aitchn.prism.validation.ae2.storage.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The ME terminal as a real container: the stored items fill the top four rows (left click takes a stack, right click
 * one item), the bottom row is an input row (drop items in, or shift-click them from the inventory) and the row between
 * holds the controls. It refreshes while open, so counts follow crafting and other players' changes.
 */
final class TerminalMenu implements ContainerMenu {

    private static final int ITEM_SLOTS = 36;
    private static final int PREV = 36;
    private static final int NEXT = 38;
    private static final int ENCODE = 40;
    private static final int CRAFT = 41;
    private static final int JOBS = 42;
    private static final int STATUS = 44;
    private static final int INPUT_FROM = 45;

    private final Ae2Services services;
    private final NetworkRuntime runtime;
    private final List<ItemKey> shown = new ArrayList<>();
    private int page;

    TerminalMenu(Ae2Services services, NetworkRuntime runtime) {
        this.services = services;
        this.runtime = runtime;
    }

    @Override
    public @NotNull String title() {
        return "<dark_aqua>ME Terminal";
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public @NotNull SlotRole role(int slot) {
        return slot >= INPUT_FROM ? SlotRole.INPUT : SlotRole.BUTTON;
    }

    @Override
    public void onOpen(@NotNull MenuView view) {
        draw(view);
    }

    @Override
    public int refreshTicks() {
        return 10;
    }

    @Override
    public void onRefresh(@NotNull MenuView view) {
        if (!runtime.powered()) {
            view.close();
            return;
        }
        draw(view);
    }

    @Override
    public void onButton(@NotNull MenuView view, int slot, @NotNull ClickKind click) {
        if (slot < ITEM_SLOTS) {
            if (slot < shown.size()) {
                withdraw(view, shown.get(slot), click);
            }
        } else if (slot == PREV) {
            page = Math.max(0, page - 1);
        } else if (slot == NEXT) {
            page++;
        } else if (slot == ENCODE) {
            services.ui.openEncode(view.player());
            return;
        } else if (slot == CRAFT) {
            services.ui.openCraftables(view.player(), runtime);
            return;
        } else if (slot == JOBS) {
            services.ui.openJobs(view.player(), runtime);
            return;
        } else if (slot == STATUS) {
            services.ui.status(view.player(), runtime);
        }
        draw(view);
    }

    @Override
    public void onSlotChanged(@NotNull MenuView view, int slot) {
        ItemStack stack = view.item(slot);
        if (slot < INPUT_FROM || stack == null) {
            return;
        }
        long accepted = runtime.storage().insert(StackCodec.key(stack), stack.getAmount());
        if (accepted > 0) {
            services.grid.markStorageDirty(runtime);
        }
        if (accepted >= stack.getAmount()) {
            view.setItem(slot, null);
        } else if (accepted > 0) {
            ItemStack rest = stack.clone();
            rest.setAmount((int) (stack.getAmount() - accepted));
            view.setItem(slot, rest);
        }
        draw(view);
    }

    @Override
    public void onClose(@NotNull MenuView view) {
        for (int slot = INPUT_FROM; slot < view.size(); slot++) {
            ItemStack left = view.item(slot);
            if (left != null) {
                view.setItem(slot, null);
                view.player().getInventory().addItem(left).values()
                        .forEach(rest -> view.player().getWorld().dropItem(view.player().getLocation(), rest));
            }
        }
    }

    private void withdraw(MenuView view, ItemKey key, ClickKind click) {
        ItemStack template = StackCodec.stack(key, 1).orElse(null);
        if (template == null) {
            return;
        }
        long want = click == ClickKind.LEFT || click == ClickKind.SHIFT_LEFT ? template.getMaxStackSize() : 1;
        long taken = runtime.storage().extract(key, want);
        if (taken <= 0) {
            return;
        }
        services.grid.markStorageDirty(runtime);
        ItemStack out = template.clone();
        out.setAmount((int) taken);
        view.player().getInventory().addItem(out).values()
                .forEach(rest -> view.player().getWorld().dropItem(view.player().getLocation(), rest));
    }

    private void draw(MenuView view) {
        List<Map.Entry<ItemKey, Long>> entries = new ArrayList<>(runtime.storage().snapshot().entrySet());
        entries.sort(Map.Entry.<ItemKey, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.<ItemKey, Long>comparingByKey()));
        int pages = Math.max(1, (entries.size() + ITEM_SLOTS - 1) / ITEM_SLOTS);
        page = Math.min(page, pages - 1);
        shown.clear();
        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            int index = page * ITEM_SLOTS + slot;
            ItemStack icon = null;
            if (index < entries.size()) {
                var entry = entries.get(index);
                icon = StackCodec.stack(entry.getKey(), 1).orElse(null);
                if (icon != null) {
                    shown.add(entry.getKey());
                    List<Component> lore = new ArrayList<>(icon.lore() == null ? List.of() : icon.lore());
                    lore.add(line("Stored: " + entry.getValue(), NamedTextColor.GRAY));
                    lore.add(line("Left click: a stack, right click: one", NamedTextColor.DARK_GRAY));
                    icon.lore(lore);
                }
            }
            if (icon == null && index < entries.size()) {
                shown.add(entries.get(index).getKey()); // keeps slots aligned when a key cannot be rebuilt
            }
            view.setItem(slot, icon);
        }
        view.setIcon(PREV, key("arrow"), "<yellow>Previous page", List.of());
        view.setIcon(NEXT, key("spectral_arrow"), "<yellow>Next page", List.of("<gray>Page " + (page + 1) + "/" + pages));
        view.setIcon(ENCODE, key("writable_book"), "<aqua>Encode a pattern", List.of());
        view.setIcon(CRAFT, key("crafting_table"), "<aqua>Request crafting", List.of());
        view.setIcon(JOBS, key("clock"), "<aqua>Crafting jobs", List.of());
        view.setIcon(STATUS, key("book"), "<gray>Network status", List.of());
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static PrismKey key(String vanilla) {
        return new PrismKey("minecraft", vanilla);
    }
}
