package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.runtime.StackCodec;
import com.aitchn.prism.validation.ae2.storage.ItemKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
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
        ItemStack one = target.asOne();
        Recipe recipe = Bukkit.getRecipesFor(one).stream()
                .filter(r -> r instanceof ShapedRecipe || r instanceof ShapelessRecipe).findFirst().orElse(null);
        if (recipe == null) {
            Ae2Ui.say(player, "<red>No crafting recipe makes that item.");
            return;
        }
        List<RecipeChoice> choices = new java.util.ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            Map<Character, RecipeChoice> map = shaped.getChoiceMap();
            for (String row : shaped.getShape()) {
                for (char c : row.toCharArray()) {
                    RecipeChoice choice = map.get(c);
                    if (choice != null) {
                        choices.add(choice);
                    }
                }
            }
        } else {
            choices.addAll(((ShapelessRecipe) recipe).getChoiceList());
        }
        Map<ItemKey, Long> inputs = new HashMap<>();
        for (RecipeChoice choice : choices) {
            ItemStack ingredient = firstOf(choice);
            if (ingredient == null) {
                Ae2Ui.say(player, "<red>That recipe uses an ingredient type patterns cannot encode.");
                return;
            }
            inputs.merge(StackCodec.key(ingredient), 1L, Long::sum);
        }
        Pattern pattern = new Pattern("enc-" + UUID.randomUUID(), inputs, StackCodec.key(one),
                resultAmount(recipe));
        Optional<ItemStack> encoded = services.ui.patternItem(pattern);
        if (encoded.isEmpty()) {
            Ae2Ui.say(player, "<red>The encoded pattern item is not registered.");
            return;
        }
        blank.setAmount(blank.getAmount() - 1);
        player.getInventory().addItem(encoded.get()).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        Ae2Ui.say(player, "<green>Pattern encoded.");
    }

    private static long resultAmount(Recipe recipe) {
        return Math.max(1, recipe.getResult().getAmount());
    }

    private static ItemStack firstOf(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice material && !material.getChoices().isEmpty()) {
            return new ItemStack(material.getChoices().get(0));
        }
        if (choice instanceof RecipeChoice.ExactChoice exact && !exact.getChoices().isEmpty()) {
            return exact.getChoices().get(0).clone();
        }
        return null;
    }
}
