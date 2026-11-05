package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.menu.ClickKind;
import com.aitchn.prism.api.menu.ContainerMenu;
import com.aitchn.prism.api.menu.MenuView;
import com.aitchn.prism.api.menu.SlotRole;
import com.aitchn.prism.core.menu.SlotRules;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Container menus with real slots, rendered as chest inventories (Geyser translates them for Bedrock). Slot roles are
 * enforced here through {@link SlotRules}; everything else is the menu's own logic.
 */
final class ContainerMenus implements Listener {

    private final ItemService items;
    private final PlatformScheduler scheduler;
    private final Logger log;

    ContainerMenus(ItemService items, PlatformScheduler scheduler, Logger log) {
        this.items = items;
        this.scheduler = scheduler;
        this.log = log;
    }

    private final class View implements MenuView, InventoryHolder {
        final Player player;
        final ContainerMenu menu;
        Inventory inventory;
        boolean open;
        ScheduledTask refresh;
        /** Slots changed by a click whose onSlotChanged has not run yet; flushed on close so no change is lost. */
        final java.util.Set<Integer> pending = new java.util.LinkedHashSet<>();

        View(Player player, ContainerMenu menu) {
            this.player = player;
            this.menu = menu;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        @Override
        public @NotNull Player player() {
            return player;
        }

        @Override
        public int size() {
            return inventory.getSize();
        }

        @Override
        public @Nullable ItemStack item(int slot) {
            ItemStack stack = inventory.getItem(slot);
            return stack == null || stack.isEmpty() ? null : stack.clone();
        }

        @Override
        public void setItem(int slot, @Nullable ItemStack stack) {
            inventory.setItem(slot, stack);
        }

        @Override
        public void setIcon(int slot, @NotNull PrismKey icon, @NotNull String name, @NotNull List<String> lore) {
            ItemStack stack = items.create(icon, 1)
                    .or(() -> VanillaItems.material(icon).map(ItemStack::new))
                    .orElseGet(() -> new ItemStack(Material.BARRIER));
            stack.editMeta(meta -> {
                meta.displayName(text(name));
                List<Component> lines = new ArrayList<>();
                lore.forEach(line -> lines.add(text(line)));
                meta.lore(lines);
            });
            inventory.setItem(slot, stack);
        }

        @Override
        public void close() {
            scheduler.runOn(player, () -> {
                if (open && player.getOpenInventory().getTopInventory().getHolder() == this) {
                    player.closeInventory();
                }
            }, null);
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }

    private static Component text(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    void open(Player player, ContainerMenu menu) {
        int rows = menu.rows();
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("a container menu has 1 to 6 rows, got " + rows);
        }
        scheduler.runOn(player, () -> {
            View view = new View(player, menu);
            view.inventory = Bukkit.createInventory(view, rows * 9, text(menu.title()));
            try {
                menu.onOpen(view);
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "Menu " + menu.getClass().getName() + " failed to open", e);
                return;
            }
            view.open = true;
            player.openInventory(view.inventory);
            int every = menu.refreshTicks();
            if (every > 0) {
                view.refresh = scheduler.runOnTimer(player, () -> {
                    if (view.open) {
                        guard(view, () -> menu.onRefresh(view));
                    }
                }, null, every, every);
            }
        }, null);
    }

    private void guard(View view, Runnable call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Menu " + view.menu.getClass().getName() + " failed", e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof View view)) {
            return;
        }
        boolean top = event.getClickedInventory() == view.inventory;
        int slot = event.getSlot();
        SlotRules.Role role = top ? role(view, slot) : null;
        SlotRules.Action action = action(event, top);
        ItemStack incoming = switch (action) {
            case PUT, SWAP_CURSOR -> event.getCursor();
            case HOTBAR_SWAP -> event.getClick() == ClickType.SWAP_OFFHAND
                    ? event.getWhoClicked().getInventory().getItemInOffHand()
                    : event.getHotbarButton() >= 0 ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
            case SHIFT_IN -> event.getCurrentItem();
            default -> null;
        };
        boolean incomingEmpty = incoming == null || incoming.isEmpty();
        boolean accepts = !incomingEmpty && top && accepts(view, slot, incoming);
        SlotRules.Outcome outcome = SlotRules.decide(role, action, accepts, incomingEmpty);
        if (top && role == SlotRules.Role.BUTTON) {
            event.setCancelled(true);
            ClickKind kind = kind(event.getClick());
            scheduler.runOn(view.player, () -> guard(view, () -> view.menu.onButton(view, slot, kind)), null);
            return;
        }
        switch (outcome) {
            case DENY -> event.setCancelled(true);
            case INSERT -> {
                event.setCancelled(true);
                ItemStack moving = event.getCurrentItem();
                if (moving != null && !moving.isEmpty()) {
                    List<Integer> changed = insert(view, moving);
                    event.setCurrentItem(moving.isEmpty() ? null : moving);
                    changed.forEach(changedSlot -> changedLater(view, changedSlot));
                }
            }
            case ALLOW -> {
                if (top) {
                    changedLater(view, slot);
                }
            }
        }
    }

    /** Moves {@code moving} into accepting input slots, stacking first. Mutates {@code moving}; returns the slots. */
    private List<Integer> insert(View view, ItemStack moving) {
        List<Integer> changed = new ArrayList<>();
        for (int pass = 0; pass < 2 && !moving.isEmpty(); pass++) {
            for (int slot = 0; slot < view.inventory.getSize() && !moving.isEmpty(); slot++) {
                if (role(view, slot) != SlotRules.Role.INPUT || !accepts(view, slot, moving)) {
                    continue;
                }
                ItemStack there = view.inventory.getItem(slot);
                if (pass == 0 && there != null && !there.isEmpty() && there.isSimilar(moving)) {
                    int room = Math.min(there.getMaxStackSize(), view.inventory.getMaxStackSize()) - there.getAmount();
                    int moved = Math.min(room, moving.getAmount());
                    if (moved > 0) {
                        there.setAmount(there.getAmount() + moved);
                        moving.setAmount(moving.getAmount() - moved);
                        view.inventory.setItem(slot, there);
                        changed.add(slot);
                    }
                } else if (pass == 1 && (there == null || there.isEmpty())) {
                    int moved = Math.min(moving.getAmount(), Math.min(moving.getMaxStackSize(),
                            view.inventory.getMaxStackSize()));
                    ItemStack placed = moving.clone();
                    placed.setAmount(moved);
                    view.inventory.setItem(slot, placed);
                    moving.setAmount(moving.getAmount() - moved);
                    changed.add(slot);
                }
            }
        }
        return changed;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof View view)) {
            return;
        }
        List<SlotRules.Role> touched = new ArrayList<>();
        List<Integer> topSlots = new ArrayList<>();
        boolean accepts = true;
        for (int raw : event.getRawSlots()) {
            if (raw < view.inventory.getSize()) {
                touched.add(role(view, raw));
                topSlots.add(raw);
                accepts &= accepts(view, raw, event.getOldCursor());
            }
        }
        if (!SlotRules.allowDrag(touched, accepts)) {
            event.setCancelled(true);
            return;
        }
        topSlots.forEach(slot -> changedLater(view, slot));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof View view && view.open) {
            if (view.refresh != null) {
                view.refresh.cancel();
            }
            // The click results are applied by now, so pending changes can be reported before the close.
            List<Integer> flush = new ArrayList<>(view.pending);
            view.pending.clear();
            flush.forEach(slot -> guard(view, () -> view.menu.onSlotChanged(view, slot)));
            view.open = false;
            guard(view, () -> view.menu.onClose(view));
        }
    }

    private void changedLater(View view, int slot) {
        if (!view.pending.add(slot)) {
            return; // already scheduled for this tick
        }
        scheduler.runOn(view.player, () -> {
            if (view.open && view.pending.remove(slot)) {
                guard(view, () -> view.menu.onSlotChanged(view, slot));
            }
        }, null);
    }

    private SlotRules.Role role(View view, int slot) {
        try {
            SlotRole role = view.menu.role(slot);
            return role == null ? SlotRules.Role.BUTTON : SlotRules.Role.valueOf(role.name());
        } catch (RuntimeException e) {
            return SlotRules.Role.BUTTON;
        }
    }

    private boolean accepts(View view, int slot, ItemStack stack) {
        try {
            return view.menu.accepts(slot, stack.clone());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static SlotRules.Action action(InventoryClickEvent event, boolean top) {
        return switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE -> SlotRules.Action.TAKE;
            case PLACE_ALL, PLACE_SOME, PLACE_ONE -> SlotRules.Action.PUT;
            case SWAP_WITH_CURSOR -> SlotRules.Action.SWAP_CURSOR;
            case HOTBAR_SWAP -> SlotRules.Action.HOTBAR_SWAP;
            case MOVE_TO_OTHER_INVENTORY -> top ? SlotRules.Action.SHIFT_OUT : SlotRules.Action.SHIFT_IN;
            case DROP_ALL_SLOT, DROP_ONE_SLOT -> SlotRules.Action.DROP;
            case COLLECT_TO_CURSOR -> SlotRules.Action.COLLECT;
            case CLONE_STACK -> SlotRules.Action.CLONE;
            default -> SlotRules.Action.OTHER;
        };
    }

    private static ClickKind kind(ClickType click) {
        return switch (click) {
            case LEFT -> ClickKind.LEFT;
            case RIGHT -> ClickKind.RIGHT;
            case SHIFT_LEFT -> ClickKind.SHIFT_LEFT;
            case SHIFT_RIGHT -> ClickKind.SHIFT_RIGHT;
            case MIDDLE -> ClickKind.MIDDLE;
            case NUMBER_KEY -> ClickKind.NUMBER_KEY;
            case DROP, CONTROL_DROP -> ClickKind.DROP;
            case DOUBLE_CLICK -> ClickKind.DOUBLE;
            default -> ClickKind.OTHER;
        };
    }
}
