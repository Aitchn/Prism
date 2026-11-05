package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.api.item.ItemLoreBehavior;
import com.aitchn.prism.api.item.ItemUseBehavior;
import com.aitchn.prism.api.item.ItemUseContext;
import com.aitchn.prism.validation.mekanism.logic.EnergyBuffer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The Energy Tablet: a portable battery whose charge is the stack's own data, shown in its lore.
 *
 * <ul>
 *   <li>Right click a generator or machine: push energy into it (handled by that block, which owns the click).
 *   <li>Sneak + right click a generator or machine: draw energy out of it, found through the energy capability.
 *   <li>Right click a cable or tube, or air: read out the network or the charge.
 * </ul>
 */
public record TabletBehavior(int capacity) implements ItemUseBehavior, ItemLoreBehavior {

    static TabletBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of("capacity"));
        int capacity = section.getInt("capacity").orElse(1_000_000);
        if (capacity < 1) {
            throw section.error("capacity must be at least 1");
        }
        return new TabletBehavior(capacity);
    }

    @Override
    public void onUse(@NotNull ItemUseContext context) {
        context.cancelVanillaAction();
        Player player = context.player();
        Block clicked = context.clickedBlock();
        if (clicked == null) {
            long charge = Tablets.charge(context.stack());
            player.sendActionBar(Component.text("Energy Tablet: " + Text.number(charge) + " / " + Text.number(capacity) + " J"));
            return;
        }
        BlockFace face = context.clickedFace();
        long charge = Tablets.charge(context.stack());
        var hand = context.hand();
        boolean sneaking = player.isSneaking();
        // The clicked block may belong to another region than the player: hop to its thread before looking at it.
        Prism.scheduler().runAt(clicked.getLocation(), () -> {
            Optional<CustomBlock> custom = Prism.blocks().get(clicked);
            if (custom.isEmpty()) {
                Tablets.say(player, "That is not a Mekanism block");
                return;
            }
            String conduit = Grid.get().describeConduit(clicked);
            if (conduit != null) {
                Tablets.say(player, conduit);
                return;
            }
            Optional<EnergyBuffer> buffer = Prism.blocks().capability(clicked, face, MekanismCapabilities.ENERGY);
            if (buffer.isEmpty()) {
                Tablets.say(player, "That block stores no energy");
            } else if (sneaking) {
                long taken = Grid.get().draw(buffer.get(), Math.min(Tablets.STEP, Math.max(0, capacity - charge)));
                Tablets.change(player, hand, taken, "Drew " + Text.number(taken) + " J from the block");
            } else {
                Tablets.say(player, "Right click without sneaking to push energy in");
            }
        });
    }

    @Override
    public @NotNull List<String> lore(@NotNull ItemStack stack) {
        long charge = Tablets.charge(stack);
        return List.of("<gray>Energy: <white>" + Text.number(charge) + " <gray>/ <white>" + Text.number(capacity) + " J",
                Text.bar(capacity == 0 ? 0 : charge / (double) capacity, 20, "red"),
                "<dark_gray>Right click a machine: push 10,000 J",
                "<dark_gray>Sneak + right click: draw 10,000 J",
                "<dark_gray>Right click a cable: read the network");
    }
}
