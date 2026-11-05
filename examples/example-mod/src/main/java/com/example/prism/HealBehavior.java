package com.example.prism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.api.item.ItemLoreBehavior;
import com.aitchn.prism.api.item.ItemUseBehavior;
import com.aitchn.prism.api.item.ItemUseContext;
import java.util.List;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Heals the player by {@code amount} half-hearts when the item is used, and counts the uses in the stack's own data;
 * the count shows in the item's lore on Java and Bedrock.
 */
public record HealBehavior(double amount) implements ItemUseBehavior, ItemLoreBehavior {

    public static final NamespacedKey USES = NamespacedKey.fromString("examplemod:uses");

    static HealBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of("amount"));
        double amount = section.requireDouble("amount");
        if (amount <= 0) {
            throw section.error("amount must be positive");
        }
        return new HealBehavior(amount);
    }

    @Override
    public void onUse(@NotNull ItemUseContext context) {
        Player player = context.player();
        player.setHealth(Math.min(20.0, player.getHealth() + amount));
        ItemStack stack = context.stack();
        Prism.items().editData(stack, data ->
                data.set(USES, PersistentDataType.INTEGER, data.getOrDefault(USES, PersistentDataType.INTEGER, 0) + 1));
        player.getInventory().setItem(context.hand(), stack);
        context.cancelVanillaAction();
    }

    @Override
    public @NotNull List<String> lore(@NotNull ItemStack stack) {
        int uses = stack.getPersistentDataContainer().getOrDefault(USES, PersistentDataType.INTEGER, 0);
        return List.of("<gray>Used <white>" + uses + "</white> times");
    }
}
