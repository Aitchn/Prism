package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /ae2 give <item> [amount]} and {@code /ae2 encode}. Prism has no creative tab or recipe registry for custom
 * content, so test items come from a command and patterns are encoded from vanilla recipes.
 */
final class Ae2Command implements CommandExecutor {

    private final Ae2Services services;

    Ae2Command(Ae2Services services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            int amount = args.length > 2 ? parse(args[2]) : 1;
            Optional<ItemStack> stack = Prism.items().create(new PrismKey(Ae2Content.NAMESPACE,
                    args[1].toLowerCase(java.util.Locale.ROOT)), Math.max(1, amount));
            if (stack.isEmpty()) {
                Ae2Ui.say(player, "<red>No such AE2 item: " + args[1]);
            } else {
                player.getInventory().addItem(stack.get()).values()
                        .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("encode")) {
            encode(player);
            return true;
        }
        Ae2Ui.say(player, "<gray>/ae2 give <item> [amount] <dark_gray>- /ae2 encode <gray>(item to make in the main hand, blank pattern in the off hand)");
        return true;
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void encode(Player player) {
        ItemStack target = player.getInventory().getItemInMainHand();
        ItemStack blank = player.getInventory().getItemInOffHand();
        boolean hasBlank = !blank.getType().isAir()
                && Prism.items().identify(blank).map(k -> k.value().equals("blank_pattern")).orElse(false);
        if (target.getType().isAir() || !hasBlank) {
            Ae2Ui.say(player, "<red>Hold the item to make in your main hand and a blank pattern in your off hand.");
            return;
        }
        Encoder.Result result = Encoder.encode(target);
        if (result.pattern().isEmpty()) {
            Ae2Ui.say(player, "<red>" + result.error());
            return;
        }
        Optional<ItemStack> encoded = services.ui.patternItem(result.pattern().get());
        if (encoded.isEmpty()) {
            Ae2Ui.say(player, "<red>The encoded pattern item is not registered.");
            return;
        }
        blank.setAmount(blank.getAmount() - 1);
        player.getInventory().addItem(encoded.get()).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        Ae2Ui.say(player, "<green>Pattern encoded.");
    }
}
