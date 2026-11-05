package com.aitchn.validation.botania;

import com.aitchn.prism.api.item.ItemLoreBehavior;
import com.aitchn.validation.botania.logic.Tablet;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/** Shows the mana stored in a tablet's own stack data. */
record TabletLore() implements ItemLoreBehavior {

    @Override
    public @NotNull List<String> lore(@NotNull ItemStack stack) {
        return List.of("<aqua>Mana: " + stored(stack) + " / " + Tablet.CAPACITY,
                "<gray>Right click a pool to charge, sneak to discharge");
    }

    static int stored(ItemStack stack) {
        var meta = stack.getItemMeta();
        return meta == null ? 0
                : meta.getPersistentDataContainer().getOrDefault(BotaniaRuntime.TABLET_MANA, PersistentDataType.INTEGER, 0);
    }
}
