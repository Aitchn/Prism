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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Counts how often this stack was used, in the stack's own data, and shows the count in its lore (Java and Bedrock).
 * Combine it with any other use behavior.
 */
public record UseCounterBehavior() implements ItemUseBehavior, ItemLoreBehavior {

    public static final NamespacedKey USES = NamespacedKey.fromString("examplemod:uses");

    static UseCounterBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of());
        return new UseCounterBehavior();
    }

    @Override
    public void onUse(@NotNull ItemUseContext context) {
        ItemStack stack = context.stack();
        Prism.items().editData(stack, data ->
                data.set(USES, PersistentDataType.INTEGER, data.getOrDefault(USES, PersistentDataType.INTEGER, 0) + 1));
        context.player().getInventory().setItem(context.hand(), stack);
    }

    @Override
    public @NotNull List<String> lore(@NotNull ItemStack stack) {
        int uses = stack.getPersistentDataContainer().getOrDefault(USES, PersistentDataType.INTEGER, 0);
        return List.of("<gray>Used <white>" + uses + "</white> times");
    }
}
