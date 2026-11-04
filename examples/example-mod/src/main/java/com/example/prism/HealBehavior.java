package com.example.prism;

import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.api.item.ItemUseBehavior;
import com.aitchn.prism.api.item.ItemUseContext;
import java.util.Set;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Heals the player by {@code amount} half-hearts when the item is used. */
public record HealBehavior(double amount) implements ItemUseBehavior {

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
        context.cancelVanillaAction();
    }
}
