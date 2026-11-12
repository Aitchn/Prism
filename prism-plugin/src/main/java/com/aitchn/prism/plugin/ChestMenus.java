package com.aitchn.prism.plugin;

import com.aitchn.prism.api.menu.ContainerMenu;
import com.aitchn.prism.api.menu.MenuButton;
import com.aitchn.prism.api.menu.MenuService;
import com.aitchn.prism.api.menu.MenuSpec;
import com.aitchn.prism.core.menu.MenuArt;
import com.aitchn.prism.core.menu.MenuTitles;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.aitchn.prism.api.text.Message;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Chest-style menus: used for Java players, and for Bedrock players because Geyser translates containers. */
final class ChestMenus implements MenuService, Listener {

    private final BukkitItemService items;
    private final PrismTranslations translations;
    private final PlatformScheduler scheduler;
    private final ContainerMenus containers;
    private final TextPrompts prompts;
    private final MenuArts arts;

    ChestMenus(BukkitItemService items, PrismTranslations translations, PlatformScheduler scheduler, MenuArts arts,
            java.util.logging.Logger log) {
        this.items = items;
        this.translations = translations;
        this.scheduler = scheduler;
        this.arts = arts;
        this.containers = new ContainerMenus(items, translations, scheduler, arts, log);
        this.prompts = new TextPrompts(scheduler, translations, log);
    }

    /** The listener behind {@link #prompt}; register it with the server. */
    TextPrompts prompts() {
        return prompts;
    }

    @Override
    public void prompt(@NotNull Player player, @NotNull String title, @NotNull String initial,
            @NotNull Consumer<String> onSubmit, @org.jetbrains.annotations.Nullable Runnable onCancel) {
        prompts.open(player, title, initial, onSubmit, onCancel);
    }

    /** The listener behind {@link #open(Player, ContainerMenu)}; register it with the server. */
    ContainerMenus containers() {
        return containers;
    }

    @Override
    public void open(@NotNull Player player, @NotNull ContainerMenu menu) {
        containers.open(player, menu);
    }

    private static final class Holder implements MenuHolder {
        /** The button id of each slot; null for a slot without a button. */
        private final List<String> ids;
        private final Consumer<String> handler;
        private Inventory inventory;

        Holder(List<String> ids, Consumer<String> handler) {
            this.ids = ids;
            this.handler = handler;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void open(@NotNull Player player, @NotNull MenuSpec spec, @NotNull Consumer<String> onSelect) {
        List<String> ids = new ArrayList<>();
        Holder holder = new Holder(ids, onSelect);
        MenuArt art = arts.art(spec.art());
        int rows = art != null ? art.rows() : spec.rows();
        // The title is fixed when the inventory is made, so it is translated for this viewer here; item names and lore
        // are translated in the packets. With art, the buttons fill the slots the picture uses (ADR-0028).
        Component title = arts.title(translations.render(Message.of(spec.title()), player), art,
                arts.layoutFor(player, art), art == null ? Map.of() : MenuTitles.initial(art));
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory = inventory;
        int next = 0;
        for (int slot = 0; slot < rows * 9; slot++) {
            boolean usable = art == null || !art.hidden(slot);
            if (usable && next < spec.buttons().size()) {
                MenuButton button = spec.buttons().get(next++);
                inventory.setItem(slot, icon(button));
                ids.add(button.id());
            } else {
                ids.add(null);
            }
        }
        scheduler.runOn(player, () -> player.openInventory(inventory), null);
    }

    private ItemStack icon(MenuButton button) {
        ItemStack stack = items.create(button.icon(), 1)
                .or(() -> VanillaItems.material(button.icon()).map(ItemStack::new))
                .orElseGet(() -> new ItemStack(Material.BARRIER));
        items.label(stack, Message.of(button.label()), List.of());
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot >= 0 && slot < holder.ids.size() && holder.ids.get(slot) != null) {
            // Closing or opening inventories inside the click transaction is unsafe; run after the event.
            Player player = (Player) event.getWhoClicked();
            String id = holder.ids.get(slot);
            scheduler.runOn(player, () -> {
                player.closeInventory();
                holder.handler.accept(id);
            }, null);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
