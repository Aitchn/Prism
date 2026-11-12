package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.menu.ClickKind;
import com.aitchn.prism.api.menu.ContainerMenu;
import com.aitchn.prism.api.menu.MenuView;
import com.aitchn.prism.api.menu.SlotRole;
import com.aitchn.prism.api.text.Message;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.menu.MenuArt;
import com.aitchn.prism.core.menu.MenuLayout;
import com.aitchn.prism.core.menu.MenuTitles;
import com.aitchn.prism.core.menu.SlotRules;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.ScheduledTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
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
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Container menus with real slots, rendered as chest inventories (Geyser translates them for Bedrock). Slot roles are
 * enforced here through {@link SlotRules}; everything else is the menu's own logic.
 */
final class ContainerMenus implements Listener {

    /** Ticks between two title changes of one menu at most (pixel widgets, ADR-0028). */
    static final int TITLE_TICKS = 2;

    private final BukkitItemService items;
    private final PrismTranslations translations;
    private final PlatformScheduler scheduler;
    private final MenuArts arts;
    private final Logger log;

    ContainerMenus(BukkitItemService items, PrismTranslations translations, PlatformScheduler scheduler,
            MenuArts arts, Logger log) {
        this.items = items;
        this.translations = translations;
        this.scheduler = scheduler;
        this.arts = arts;
        this.log = log;
    }

    private final class View implements MenuView, MenuHolder {
        final Player player;
        final ContainerMenu menu;
        Inventory inventory;
        boolean open;
        ScheduledTask refresh;
        /** Slots changed by a click whose onSlotChanged has not run yet; flushed on close so no change is lost. */
        final java.util.Set<Integer> pending = new java.util.LinkedHashSet<>();
        /** The menu's art and what this viewer sees of it (null: the plain chest). */
        MenuArt art;
        MenuLayout layout;
        int rows;
        Component titleText;
        /** Each widget's step or state; the title is resent when a drawn one changes. */
        final Map<String, Integer> values = new LinkedHashMap<>();
        boolean titleDirty;
        String sentGlyphs = "";
        ScheduledTask titleTask;

        View(Player player, ContainerMenu menu) {
            this.player = player;
            this.menu = menu;
        }

        boolean hidden(int slot) {
            return art != null && art.hidden(slot);
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
            if (!hidden(slot)) { // a hidden slot stays empty (ADR-0028)
                inventory.setItem(slot, stack);
            }
        }

        @Override
        public void setIcon(int slot, @NotNull PrismKey icon, @NotNull Message name, @NotNull List<Message> lore) {
            if (!hidden(slot)) {
                icon(slot, icon, name, lore);
            }
        }

        private void icon(int slot, PrismKey icon, Message name, List<Message> lore) {
            ItemStack stack = items.create(icon, 1)
                    .or(() -> VanillaItems.material(icon).map(ItemStack::new))
                    .orElseGet(() -> new ItemStack(Material.BARRIER));
            // Keys, not text: every viewer reads the icon in their own language (ADR-0026).
            items.label(stack, name, lore);
            inventory.setItem(slot, stack);
        }

        @Override
        public void setGauge(int slot, @NotNull PrismKey gauge, double fraction, @NotNull Message name,
                @NotNull List<Message> lore) {
            if (!hidden(slot)) {
                gauge(slot, gauge, fraction, name, lore);
            }
        }

        @Override
        public void setWidget(@NotNull String id, double fraction, @NotNull Message name,
                @NotNull List<Message> lore) {
            MenuArt.Widget widget = art == null ? null : art.widget(id);
            if (widget == null || widget.kind() == MenuArt.Kind.ICON) {
                return;
            }
            int step = widget.step(fraction);
            if (layout != null && layout.widgets().containsKey(id)) {
                changed(id, step);
            } else if (widget.fallbackSlot() >= 0) {
                gauge(widget.fallbackSlot(), widget.fallbackItems().getFirst(), fraction, name, lore);
            }
        }

        @Override
        public void setWidgetState(@NotNull String id, int state, @NotNull Message name,
                @NotNull List<Message> lore) {
            MenuArt.Widget widget = art == null ? null : art.widget(id);
            if (widget == null || widget.kind() != MenuArt.Kind.ICON) {
                return;
            }
            int clamped = widget.state(state);
            if (layout != null && layout.widgets().containsKey(id)) {
                changed(id, clamped);
            } else if (widget.fallbackSlot() >= 0) {
                icon(widget.fallbackSlot(), widget.fallbackItems().get(clamped), name, lore);
            }
        }

        @Override
        public boolean showsArt() {
            return layout != null;
        }

        private void changed(String id, int value) {
            Integer before = values.put(id, value);
            if (before == null || before != value) {
                titleDirty = true;
            }
        }

        /** Sends the title again if a drawn widget changed since the last one; runs every {@link #TITLE_TICKS}. */
        void flushTitle() {
            if (!titleDirty || !open) {
                return;
            }
            titleDirty = false;
            String glyphs = MenuTitles.glyphs(layout, values);
            if (glyphs.equals(sentGlyphs) || player.getOpenInventory().getTopInventory().getHolder() != this) {
                return;
            }
            if (arts.retitle(player, rows, arts.title(titleText, art, layout, values))) {
                sentGlyphs = glyphs;
                player.updateInventory(); // reopening the screen clears it client side: resend slots and cursor
            }
        }

        private void gauge(int slot, PrismKey gauge, double fraction, Message name, List<Message> lore) {
            icon(slot, gauge, name, lore);
            var spec = items.definition(gauge).flatMap(definition ->
                    definition.components().get(PrismComponents.GAUGE));
            ItemStack stack = inventory.getItem(slot);
            if (spec.isEmpty() || stack == null) {
                return;
            }
            PrismKey look = Appearances.gaugeLook(gauge, spec.get().step(fraction));
            stack.editMeta(meta -> {
                if (meta.hasItemModel()) { // only when the pack has the gauge's looks
                    Keys.bukkit(look).ifPresent(meta::setItemModel);
                }
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
        public boolean isChangePending(int slot) {
            return pending.contains(slot);
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }

    void open(Player player, ContainerMenu menu) {
        PrismKey artKey;
        try {
            artKey = menu.art();
        } catch (RuntimeException e) {
            artKey = null;
        }
        MenuArt art = arts.art(artKey);
        int rows = art != null ? art.rows() : menu.rows();
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("a container menu has 1 to 6 rows, got " + rows);
        }
        scheduler.runOn(player, () -> {
            View view = new View(player, menu);
            view.art = art;
            view.rows = rows;
            view.layout = arts.layoutFor(player, art);
            if (art != null) {
                view.values.putAll(MenuTitles.initial(art));
            }
            view.titleText = translations.render(Message.of(menu.title()), player);
            view.inventory = Bukkit.createInventory(view, rows * 9, view.titleText);
            try {
                menu.onOpen(view);
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "Menu " + menu.getClass().getName() + " failed to open", e);
                return;
            }
            if (art != null) {
                // The title is fixed when the inventory is made: make it again now that onOpen set the widgets, so
                // the first frame is already right. The contents move over as they are.
                Inventory filled = view.inventory;
                view.inventory = Bukkit.createInventory(view, rows * 9,
                        arts.title(view.titleText, art, view.layout, view.values));
                view.inventory.setContents(filled.getContents());
                view.titleDirty = false;
                if (view.layout != null) {
                    view.sentGlyphs = MenuTitles.glyphs(view.layout, view.values);
                }
            }
            view.open = true;
            player.openInventory(view.inventory);
            if (view.layout != null && !view.layout.widgets().isEmpty()) {
                view.titleTask = scheduler.runOnTimer(player, view::flushTitle, null, TITLE_TICKS, TITLE_TICKS);
            }
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
        if (top && view.hidden(slot)) {
            event.setCancelled(true); // outside the picture: nothing happens (ADR-0028)
            return;
        }
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
        if (top && outcome == SlotRules.Outcome.ALLOW && !allowChange(view, slot)) {
            outcome = SlotRules.Outcome.DENY;
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
                if (role(view, slot) != SlotRules.Role.INPUT || !accepts(view, slot, moving)
                        || !allowChange(view, slot)) {
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
                if (view.hidden(raw)) {
                    event.setCancelled(true);
                    return;
                }
                SlotRules.Role role = role(view, raw);
                touched.add(role);
                topSlots.add(raw);
                accepts &= accepts(view, raw, event.getOldCursor())
                        && (role == SlotRules.Role.BUTTON || allowChange(view, raw));
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
            if (view.titleTask != null) {
                view.titleTask.cancel();
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
        if (view.hidden(slot)) {
            return SlotRules.Role.BUTTON; // never an input: shift-clicks skip it
        }
        try {
            SlotRole role = view.menu.role(slot);
            return role == null ? SlotRules.Role.BUTTON : SlotRules.Role.valueOf(role.name());
        } catch (RuntimeException e) {
            return SlotRules.Role.BUTTON;
        }
    }

    private boolean allowChange(View view, int slot) {
        try {
            return view.menu.allowChange(view, slot);
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Menu " + view.menu.getClass().getName() + " failed in allowChange", e);
            return false;
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
