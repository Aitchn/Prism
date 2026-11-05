package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.menu.MenuButton;
import com.aitchn.prism.api.menu.MenuSpec;
import com.aitchn.prism.validation.ae2.crafting.CraftingJob;
import com.aitchn.prism.validation.ae2.crafting.CraftingPlan;
import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.crafting.PatternCodec;
import com.aitchn.prism.validation.ae2.grid.GridNetwork;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import com.aitchn.prism.validation.ae2.runtime.NetworkRuntime;
import com.aitchn.prism.validation.ae2.runtime.StackCodec;
import com.aitchn.prism.validation.ae2.storage.ItemKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Entry points of the mod's screens. Item-moving screens are {@link com.aitchn.prism.api.menu.ContainerMenu}s (terminal,
 * drive, pattern provider, encoder); choices (what to craft, how many, start or cancel) stay button lists.
 */
final class Ae2Ui {

    private static final PrismKey PANE = new PrismKey("minecraft", "gray_stained_glass_pane");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Ae2Services services;

    Ae2Ui(Ae2Services services) {
        this.services = services;
    }

    // --- dispatch --------------------------------------------------------------------------------------------------

    void interact(Player player, CustomBlock block, NodeKind kind) {
        GridPos pos = NodeBehavior.pos(block.block().getLocation());
        Optional<NetworkRuntime> network = services.grid.networkAt(pos);
        if (network.isEmpty()) {
            say(player, "<red>This block is not part of any ME network.");
            return;
        }
        NetworkRuntime runtime = network.get();
        GridNetwork topology = runtime.topology();
        switch (kind) {
            case CONTROLLER -> status(player, runtime);
            case CABLE, DENSE_CABLE -> say(player, "<gray>" + kind.id() + ": <white>"
                    + topology.channelsUsed().getOrDefault(pos, 0) + "/" + kind.channelCapacity() + " <gray>channels");
            case ENERGY_CELL, CREATIVE_ENERGY_CELL -> chargeCell(player, runtime, kind);
            case CRAFTING_STORAGE_1K, CRAFTING_STORAGE_4K, CRAFTING_STORAGE_16K, CRAFTING_STORAGE_64K,
                    CRAFTING_COPROCESSOR -> clusterInfo(player, runtime, pos);
            default -> {
                GridNetwork.DeviceState state = topology.states().get(pos);
                if (state != GridNetwork.DeviceState.ONLINE) {
                    say(player, "<red>Offline: " + String.valueOf(state).toLowerCase(Locale.ROOT).replace('_', ' '));
                } else if (!runtime.powered()) {
                    say(player, "<red>The network has no power.");
                } else if (kind == NodeKind.TERMINAL) {
                    openTerminal(player, runtime);
                } else if (kind == NodeKind.DRIVE) {
                    services.grid.drive(pos).ifPresent(drive ->
                            Prism.menus().open(player, new DriveMenu(services, pos, drive)));
                } else if (kind == NodeKind.PATTERN_PROVIDER) {
                    ProviderMenu menu = ProviderMenu.of(services, block);
                    if (menu == null) {
                        say(player, "<red>Someone else is using this provider.");
                    } else {
                        Prism.menus().open(player, menu);
                    }
                }
            }
        }
    }

    void openTerminal(Player player, NetworkRuntime runtime) {
        Prism.menus().open(player, new TerminalMenu(services, runtime));
    }

    void openEncode(Player player) {
        Prism.menus().open(player, new EncodeMenu(services));
    }

    void status(Player player, NetworkRuntime runtime) {
        GridNetwork topology = runtime.topology();
        say(player, "<aqua>ME network <gray>" + topology.status().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        say(player, "<gray>Power: <white>" + (topology.hasCreativeEnergy() ? "infinite"
                : String.format(Locale.ROOT, "%.0f / %d AE", runtime.power().stored(), topology.energyCapacity()))
                + " <gray>drain <white>" + String.format(Locale.ROOT, "%.1f", topology.idleDrain()) + " AE/t"
                + (runtime.powered() ? "" : " <red>(unpowered)"));
        long online = topology.states().values().stream().filter(s -> s == GridNetwork.DeviceState.ONLINE).count();
        say(player, "<gray>Devices online: <white>" + online + "/" + topology.states().size()
                + " <gray>Crafting CPUs: <white>" + topology.clusters().stream().filter(
                        GridNetwork.CraftingCluster::valid).count());
        say(player, "<gray>Items stored: <white>" + runtime.storage().snapshot().values().stream()
                .mapToLong(Long::longValue).sum());
    }

    private void clusterInfo(Player player, NetworkRuntime runtime, GridPos pos) {
        runtime.topology().clusters().stream().filter(c -> c.members().contains(pos)).findFirst().ifPresent(c ->
                say(player, "<gray>Crafting CPU: <white>" + c.bytes() + " bytes, " + c.parallelism()
                        + " parallel " + (c.valid() ? "" : "<red>(needs a storage block)")
                        + (runtime.jobs().containsKey(NetworkRuntime.anchor(c)) ? " <yellow>busy" : "")));
    }

    private void chargeCell(Player player, NetworkRuntime runtime, NodeKind kind) {
        ItemStack held = player.getInventory().getItemInMainHand();
        int energy = held.getType().isAir() ? 0 : Prism.items().identify(held).map(Ae2Content::energyOf).orElse(0);
        if (kind == NodeKind.CREATIVE_ENERGY_CELL || energy <= 0) {
            say(player, kind == NodeKind.CREATIVE_ENERGY_CELL ? "<light_purple>Infinite energy."
                    : "<gray>Hold a charged certus quartz crystal to charge the cell. Stored: <white>"
                            + (long) runtime.power().stored() + " AE");
            return;
        }
        double accepted = runtime.power().inject(runtime.topology(), (double) energy * held.getAmount());
        int used = (int) Math.ceil(accepted / energy);
        if (used > 0 && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - used);
        }
        say(player, "<green>Charged " + (long) accepted + " AE <gray>(now " + (long) runtime.power().stored() + ")");
    }

    // --- crafting ----------------------------------------------------------------------------------------------------

    void openCraftables(Player player, NetworkRuntime runtime) {
        Set<ItemKey> outputs = new LinkedHashSet<>();
        runtime.patterns().stream().sorted(Comparator.comparing(Pattern::id)).forEach(p -> outputs.add(p.output()));
        if (outputs.isEmpty()) {
            say(player, "<gray>No patterns. Put encoded patterns into a pattern provider.");
            openTerminal(player, runtime);
            return;
        }
        List<MenuButton> buttons = new ArrayList<>();
        for (ItemKey output : outputs) {
            StackCodec.stack(output, 1).ifPresent(stack -> buttons.add(new MenuButton("make:" + output.id(),
                    "<white>" + escape(StackCodec.name(stack)), StackCodec.icon(stack))));
            if (buttons.size() == 45) {
                break;
            }
        }
        buttons.add(new MenuButton("back", "<gray>Back", key("minecraft", "barrier")));
        Prism.menus().open(player, new MenuSpec("<dark_aqua>Craftable items", buttons), id -> {
            if (id.startsWith("make:")) {
                openAmount(player, runtime, new ItemKey(id.substring(5)));
            } else {
                openTerminal(player, runtime);
            }
        });
    }

    private void openAmount(Player player, NetworkRuntime runtime, ItemKey target) {
        Optional<ItemStack> stack = StackCodec.stack(target, 1);
        if (stack.isEmpty()) {
            return;
        }
        List<MenuButton> buttons = new ArrayList<>();
        for (long amount : new long[] {1, 4, 16, 64, 256}) {
            buttons.add(new MenuButton("amount:" + amount, "<white>Craft <gold>" + amount,
                    StackCodec.icon(stack.get())));
        }
        buttons.add(new MenuButton("back", "<gray>Back", key("minecraft", "barrier")));
        Prism.menus().open(player, new MenuSpec("<dark_aqua>How many?", buttons), id -> {
            if (id.startsWith("amount:")) {
                openPlan(player, runtime, target, Long.parseLong(id.substring(7)));
            } else {
                openCraftables(player, runtime);
            }
        });
    }

    private void openPlan(Player player, NetworkRuntime runtime, ItemKey target, long amount) {
        CraftingPlan plan = runtime.plan(target, amount);
        List<MenuButton> buttons = new ArrayList<>();
        for (CraftingPlan.Step step : plan.steps()) {
            StackCodec.stack(step.pattern().output(), 1).ifPresent(stack -> buttons.add(new MenuButton(
                    "info:" + buttons.size(), "<white>Craft " + escape(StackCodec.name(stack)) + " <gray>x"
                            + step.crafts() * step.pattern().outputCount(), StackCodec.icon(stack))));
        }
        plan.extractFromStorage().forEach((key, count) -> StackCodec.stack(key, 1).ifPresent(stack ->
                buttons.add(new MenuButton("info:" + buttons.size(), "<green>From storage: " + escape(
                        StackCodec.name(stack)) + " <gray>x" + count, StackCodec.icon(stack)))));
        plan.missing().forEach((key, count) -> StackCodec.stack(key, 1).ifPresent(stack ->
                buttons.add(new MenuButton("info:" + buttons.size(), "<red>Missing: " + escape(
                        StackCodec.name(stack)) + " <gray>x" + count, StackCodec.icon(stack)))));
        if (plan.cycle()) {
            buttons.add(new MenuButton("info:cycle", "<red>The patterns loop back on themselves",
                    key("minecraft", "barrier")));
        }
        buttons.add(new MenuButton("bytes", "<gray>Needs " + plan.bytes() + " bytes of crafting storage",
                key("minecraft", "paper")));
        if (plan.feasible()) {
            buttons.add(new MenuButton("start", "<green>Start crafting", key("minecraft", "emerald")));
        }
        buttons.add(new MenuButton("cancel", "<gray>Cancel", key("minecraft", "barrier")));
        Prism.menus().open(player, new MenuSpec("<dark_aqua>Crafting plan", buttons.size() > 54
                ? buttons.subList(0, 54) : buttons), id -> {
            if (id.equals("start")) {
                start(player, runtime, plan);
            } else if (id.startsWith("info:") || id.equals("bytes")) {
                openPlan(player, runtime, target, amount);
            } else {
                openTerminal(player, runtime);
            }
        });
    }

    private void start(Player player, NetworkRuntime runtime, CraftingPlan plan) {
        Optional<GridNetwork.CraftingCluster> cluster = runtime.freeClusterFor(plan);
        if (cluster.isEmpty()) {
            say(player, "<red>No free crafting CPU with " + plan.bytes() + " bytes.");
        } else if (!runtime.startJob(plan, cluster.get())) {
            say(player, "<red>The items needed are no longer in storage.");
        } else {
            say(player, "<green>Crafting started.");
        }
        services.grid.markStorageDirty(runtime);
        openTerminal(player, runtime);
    }

    void openJobs(Player player, NetworkRuntime runtime) {
        List<MenuButton> buttons = new ArrayList<>();
        Map<GridPos, CraftingJob> jobs = runtime.jobs();
        jobs.forEach((anchor, job) -> StackCodec.stack(job.plan().target(), 1).ifPresent(stack ->
                buttons.add(new MenuButton("cancel:" + anchor.encode(), "<white>" + escape(StackCodec.name(stack))
                        + " <gray>x" + job.plan().amount() + " - " + job.craftsLeft() + " crafts left <red>(click to cancel)",
                        StackCodec.icon(stack)))));
        if (buttons.isEmpty()) {
            say(player, "<gray>No crafting jobs are running.");
            openTerminal(player, runtime);
            return;
        }
        buttons.add(new MenuButton("back", "<gray>Back", key("minecraft", "barrier")));
        Prism.menus().open(player, new MenuSpec("<dark_aqua>Crafting jobs", buttons), id -> {
            if (id.startsWith("cancel:")) {
                CraftingJob job = jobs.get(GridPos.decode(id.substring(7)));
                if (job != null) {
                    job.cancel();
                    services.grid.markStorageDirty(runtime);
                }
            }
            openTerminal(player, runtime);
        });
    }

    // --- patterns ----------------------------------------------------------------------------------------------------

    /** A new encoded pattern item carrying {@code pattern} in its own data. */
    Optional<ItemStack> patternItem(Pattern pattern) {
        Optional<ItemStack> item = Prism.items().create(new PrismKey("ae2", "encoded_pattern"), 1);
        item.ifPresent(stack -> Prism.items().editData(stack,
                data -> data.set(Ae2Content.PATTERN, PersistentDataType.STRING, PatternCodec.encode(pattern))));
        return item;
    }

    // --- helpers -----------------------------------------------------------------------------------------------------

    private static PrismKey key(String namespace, String value) {
        return new PrismKey(namespace, value);
    }

    private static String escape(String text) {
        return MINI.escapeTags(text);
    }

    static void say(Player player, String miniMessage) {
        Component message = MINI.deserialize(miniMessage);
        player.sendMessage(message);
    }
}
