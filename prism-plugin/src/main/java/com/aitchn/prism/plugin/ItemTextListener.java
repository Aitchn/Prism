package com.aitchn.prism.plugin;

import com.aitchn.prism.platform.spi.PlatformScheduler;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The server-side half of per-viewer item text (ADR-0026).
 *
 * <ul>
 *   <li>Remembers each player's locale (join, {@link PlayerLocaleChangeEvent}) for the packet rewriter, and resends
 *       the open inventory when it changes, so the new language shows at once.
 *   <li>Normalises stacks made before item text moved into packets: they had a name and lore baked in, so they would
 *       not stack with new ones. It happens when a stack is seen (the player's inventory and ender chest on join, a
 *       container when it is opened, an item when it is picked up), and costs one persistent data lookup per stack.
 * </ul>
 */
final class ItemTextListener implements Listener {

    private final BukkitItemService items;
    private final PrismTranslations translations;
    private final PlatformScheduler scheduler;

    ItemTextListener(BukkitItemService items, PrismTranslations translations, PlatformScheduler scheduler) {
        this.items = items;
        this.translations = translations;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        translations.remember(player.getUniqueId(), player.locale());
        normalize(player.getInventory());
        normalize(player.getEnderChest());
        // Whatever was sent before the locale was known is sent again, now in the player's language.
        scheduler.runOn(player, player::updateInventory, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        Player player = event.getPlayer();
        translations.remember(player.getUniqueId(), event.locale());
        scheduler.runOn(player, player::updateInventory, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        translations.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Inventory top = event.getInventory();
        InventoryHolder holder = top.getHolder(false); // no block state snapshot
        if (!(holder instanceof MenuHolder)) {
            normalize(top);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        ItemStack stack = item.getItemStack();
        if (items.normalize(stack)) {
            item.setItemStack(stack);
        }
    }

    private void normalize(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack != null && items.normalize(stack)) {
                inventory.setItem(slot, stack);
            }
        }
    }
}
