package com.aitchn.prism.plugin;

import org.bukkit.inventory.InventoryHolder;

/**
 * Marks the inventories of Prism's own menus. Their stacks are drawn by Prism (icons with translation keys, a mod's
 * display items), so the legacy-stack normalisation of {@link ItemTextListener} leaves them alone.
 */
interface MenuHolder extends InventoryHolder {}
