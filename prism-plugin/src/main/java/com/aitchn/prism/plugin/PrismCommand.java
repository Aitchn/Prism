package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.menu.MenuButton;
import com.aitchn.prism.api.menu.MenuService;
import com.aitchn.prism.api.menu.MenuSpec;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /prism give <player> <item> [amount]} and {@code /prism items}: an item browser that works on Java and Bedrock
 * (chest menu), so every custom item can be obtained in game without a mod-specific command. {@code /prism bench} is the
 * display layer's load test ({@link DisplayBench}), under its own permission.
 */
final class PrismCommand implements TabExecutor {

    static final String PERMISSION = "prism.command.give";
    static final String BENCH_PERMISSION = "prism.command.bench";
    private static final int PAGE_SIZE = 45;

    private final Registry<ItemDefinition> definitions;
    private final ItemService items;
    private final MenuService menus;
    private final PlatformScheduler scheduler;
    private final DisplayBench bench;

    PrismCommand(Registry<ItemDefinition> definitions, ItemService items, MenuService menus,
            PlatformScheduler scheduler, DisplayBench bench) {
        this.definitions = definitions;
        this.items = items;
        this.menus = menus;
        this.scheduler = scheduler;
        this.bench = bench;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("/prism give <player> <item> [amount] | /prism items"
                    + " | /prism bench displays <count> [radius] | /prism bench clear", NamedTextColor.GRAY));
            return true;
        }
        if (args[0].equalsIgnoreCase("bench")) {
            if (!sender.hasPermission(BENCH_PERMISSION)) {
                sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            } else {
                bench.run(sender, args);
            }
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "items" -> {
                if (sender instanceof Player player) {
                    openBrowser(player, 0);
                } else {
                    sender.sendMessage(Component.text("Only players can open the item browser.", NamedTextColor.RED));
                }
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand " + args[0], NamedTextColor.RED));
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("/prism give <player> <item> [amount]", NamedTextColor.GRAY));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("No online player " + args[1], NamedTextColor.RED));
            return;
        }
        PrismKey key;
        int amount;
        try {
            key = PrismKey.parse(args[2]);
            amount = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return;
        }
        if (amount < 1 || amount > 64 * 36) {
            sender.sendMessage(Component.text("Amount must be between 1 and " + 64 * 36, NamedTextColor.RED));
            return;
        }
        if (items.create(key, 1).isEmpty()) {
            sender.sendMessage(Component.text("Unknown item " + key, NamedTextColor.RED));
            return;
        }
        int total = amount;
        scheduler.runOn(target, () -> {
            hand(target, key, total);
            sender.sendMessage(Component.text("Gave " + total + " " + key + " to " + target.getName(),
                    NamedTextColor.GREEN));
        }, null);
    }

    /** Adds the items to the player's inventory; what does not fit is dropped at their feet. */
    private void hand(Player player, PrismKey key, int amount) {
        int stackSize = definitions.get(key).flatMap(d -> d.components().get(PrismComponents.MAX_STACK_SIZE))
                .map(PrismComponents.MaxStackSize::value).orElse(64);
        int left = amount;
        while (left > 0) {
            int now = Math.min(left, stackSize);
            Optional<ItemStack> stack = items.create(key, now);
            if (stack.isEmpty()) {
                return;
            }
            player.getInventory().addItem(stack.get()).values()
                    .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            left -= now;
        }
    }

    private void openBrowser(Player player, int page) {
        List<PrismKey> keys = definitions.keys().stream().sorted().toList();
        if (keys.isEmpty()) {
            player.sendMessage(Component.text("No custom items are registered.", NamedTextColor.GRAY));
            return;
        }
        int pages = (keys.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int current = Math.max(0, Math.min(page, pages - 1));
        List<MenuButton> buttons = new ArrayList<>();
        for (PrismKey key : keys.subList(current * PAGE_SIZE, Math.min(keys.size(), (current + 1) * PAGE_SIZE))) {
            // An empty label shows the item's own name, translated for the viewer (ADR-0026).
            buttons.add(new MenuButton("item:" + key, "", key));
        }
        if (pages > 1) {
            while (buttons.size() % 9 != 0) {
                buttons.add(new MenuButton("none:" + buttons.size(), " ", PrismKey.parse("minecraft:gray_stained_glass_pane")));
            }
            buttons.add(new MenuButton("page:" + (current - 1), "<yellow>Previous page", PrismKey.parse("minecraft:arrow")));
            buttons.add(new MenuButton("page:" + (current + 1), "<yellow>Next page (" + (current + 1) + "/" + pages + ")",
                    PrismKey.parse("minecraft:arrow")));
        }
        menus.open(player, new MenuSpec("Prism items", buttons), id -> {
            if (id.startsWith("item:")) {
                PrismKey key = PrismKey.parse(id.substring(5));
                hand(player, key, definitions.get(key).flatMap(d -> d.components().get(PrismComponents.MAX_STACK_SIZE))
                        .map(PrismComponents.MaxStackSize::value).orElse(64));
                openBrowser(player, current);
            } else if (id.startsWith("page:")) {
                openBrowser(player, Integer.parseInt(id.substring(5)));
            }
        });
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        boolean benches = sender.hasPermission(BENCH_PERMISSION);
        if (args.length > 1 && args[0].equalsIgnoreCase("bench")) {
            return benches && args.length == 2 ? filter(List.of("displays", "clear"), args[1]) : List.of();
        }
        if (!sender.hasPermission(PERMISSION)) {
            return args.length == 1 && benches ? filter(List.of("bench"), args[0]) : List.of();
        }
        return switch (args.length) {
            case 1 -> filter(benches ? List.of("give", "items", "bench") : List.of("give", "items"), args[0]);
            case 2 -> args[0].equalsIgnoreCase("give")
                    ? filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]) : List.of();
            case 3 -> args[0].equalsIgnoreCase("give")
                    ? filter(definitions.keys().stream().map(PrismKey::toString).sorted().toList(), args[2]) : List.of();
            default -> List.of();
        };
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
