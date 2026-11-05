package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Reading and changing the charge of Energy Tablet stacks. The charge is the stack's own persistent data. */
final class Tablets {

    static final PrismKey KEY = new PrismKey("mekanism", "energy_tablet");
    static final NamespacedKey CHARGE = NamespacedKey.fromString("mekanism:charge");
    /** Joules moved by one click. */
    static final long STEP = 10_000;

    private Tablets() {}

    static boolean is(ItemStack stack) {
        return Prism.items().identify(stack).filter(KEY::equals).isPresent();
    }

    static long charge(ItemStack stack) {
        return stack.getPersistentDataContainer().getOrDefault(CHARGE, PersistentDataType.LONG, 0L);
    }

    static long capacity(ItemStack stack) {
        return Prism.items().definition(stack).flatMap(d -> d.behaviors(TabletBehavior.class).stream().findFirst())
                .map(TabletBehavior::capacity).orElse(0);
    }

    /** Shows a message on the player's action bar, from any thread. */
    static void say(Player player, String message) {
        Prism.scheduler().runOn(player, () -> player.sendActionBar(Component.text(message)), null);
    }

    /**
     * Adds {@code delta} joules to the tablet in {@code hand} (negative removes) and says {@code message}. Runs on the
     * player's thread; does nothing to the tablet if the player no longer holds one.
     */
    static void change(Player player, EquipmentSlot hand, long delta, String message) {
        if (hand == null) {
            return;
        }
        Prism.scheduler().runOn(player, () -> {
            ItemStack now = player.getInventory().getItem(hand);
            if (now == null || !is(now)) {
                return;
            }
            long updated = Math.max(0, Math.min(capacity(now), charge(now) + delta));
            Prism.items().editData(now, data -> data.set(CHARGE, PersistentDataType.LONG, updated));
            player.getInventory().setItem(hand, now);
            player.sendActionBar(Component.text(message + " (" + Text.number(updated) + " J stored)"));
        }, null);
    }
}
