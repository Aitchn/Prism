package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.menu.MenuButton;
import com.aitchn.prism.api.menu.MenuSpec;
import com.aitchn.prism.validation.ae2.crafting.CraftingJob;
import com.aitchn.prism.validation.ae2.crafting.CraftingPlan;
import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.crafting.PatternCodec;
import com.aitchn.prism.validation.ae2.grid.GridNetwork;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import com.aitchn.prism.validation.ae2.runtime.Ae2State;
import com.aitchn.prism.validation.ae2.runtime.NetworkRuntime;
import com.aitchn.prism.validation.ae2.runtime.StackCodec;
import com.aitchn.prism.validation.ae2.storage.ItemKey;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Every menu and message of the mod. Prism's {@link MenuSpec} is a plain list of buttons, so terminals are built from
 * what it offers: item buttons, padding to push the controls to the bottom row, and a re-open after each click.
 */
final class Ae2Ui {

    private static final int PAGE_SIZE = 36;
    private static final long[] WITHDRAW = {1, 16, 64};
    private static final PrismKey PANE = new PrismKey("minecraft", "gray_stained_glass_pane");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Ae2Services services;
    private final Map<UUID, Integer> withdrawMode = new ConcurrentHashMap<>();

    Ae2Ui(Ae2Services services) {
        this.services = services;
    }

    // --- dispatch --------------------------------------------------------------------------------------------------

    void interact(Player player, GridPos pos, NodeKind kind) {
        Optional<NetworkRuntime> network = services.grid.networkAt(pos);
        if (network.isEmpty()) {
            say(player, "<red>This block is not registered with any ME network.");
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
                    say(player, "<red>Offline: " + state.name().toLowerCase(Locale.ROOT).replace('_', ' '));
                } else if (!runtime.powered()) {
                    say(player, "<red>The network has no power.");
                } else if (kind == NodeKind.TERMINAL) {
                    openTerminal(player, runtime, 0);
                } else if (kind == NodeKind.DRIVE) {
                    openDrive(player, runtime, pos);
                } else if (kind == NodeKind.PATTERN_PROVIDER) {
                    openProvider(player, runtime, pos);
                }
            }
        }
    }

    private void status(Player player, NetworkRuntime runtime) {
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

    // --- terminal ----------------------------------------------------------------------------------------------------

    void openTerminal(Player player, NetworkRuntime runtime, int requestedPage) {
        List<Map.Entry<ItemKey, Long>> entries = new ArrayList<>(runtime.storage().snapshot().entrySet());
        entries.sort(Map.Entry.<ItemKey, Long>comparingByValue().reversed().thenComparing(Map.Entry.<ItemKey, Long>comparingByKey()));
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int mode = withdrawMode.getOrDefault(player.getUniqueId(), 0);

        List<MenuButton> buttons = new ArrayList<>();
        int from = page * PAGE_SIZE;
        for (int i = from; i < Math.min(entries.size(), from + PAGE_SIZE); i++) {
            ItemKey key = entries.get(i).getKey();
            Optional<ItemStack> stack = StackCodec.stack(key, 1);
            if (stack.isEmpty()) {
                continue;
            }
            buttons.add(new MenuButton("take:" + key.id(), "<white>" + escape(StackCodec.name(stack.get()))
                    + " <gray>x" + entries.get(i).getValue(), StackCodec.icon(stack.get())));
        }
        for (int pad = buttons.size(); pad < PAGE_SIZE; pad++) {
            buttons.add(new MenuButton("pad:" + pad, " ", PANE));
        }
        buttons.add(new MenuButton("prev", "<yellow>Previous page", key("minecraft", "arrow")));
        buttons.add(new MenuButton("next", "<yellow>Next page <gray>(" + (page + 1) + "/" + pages + ")",
                key("minecraft", "spectral_arrow")));
        buttons.add(new MenuButton("mode", "<gold>Withdraw: <white>" + (WITHDRAW[mode] == 64 ? "full stack"
                : String.valueOf(WITHDRAW[mode])), key("minecraft", "hopper")));
        buttons.add(new MenuButton("hand", "<green>Insert held item", key("minecraft", "chest")));
        buttons.add(new MenuButton("all", "<green>Insert whole inventory", key("minecraft", "ender_chest")));
        buttons.add(new MenuButton("craft", "<aqua>Request crafting", key("minecraft", "crafting_table")));
        buttons.add(new MenuButton("jobs", "<aqua>Crafting jobs", key("minecraft", "clock")));
        buttons.add(new MenuButton("info", "<gray>Network status", key("minecraft", "book")));
        buttons.add(new MenuButton("refresh", "<gray>Refresh", key("minecraft", "compass")));

        Prism.menus().open(player, new MenuSpec("<dark_aqua>ME Terminal", buttons), id -> {
            if (id.startsWith("take:")) {
                withdraw(player, runtime, new ItemKey(id.substring(5)), WITHDRAW[mode]);
                openTerminal(player, runtime, page);
                return;
            }
            switch (id) {
                case "prev" -> openTerminal(player, runtime, page - 1);
                case "next" -> openTerminal(player, runtime, page + 1);
                case "mode" -> {
                    withdrawMode.put(player.getUniqueId(), (mode + 1) % WITHDRAW.length);
                    openTerminal(player, runtime, page);
                }
                case "hand" -> {
                    insertHeld(player, runtime);
                    openTerminal(player, runtime, page);
                }
                case "all" -> {
                    insertInventory(player, runtime);
                    openTerminal(player, runtime, page);
                }
                case "craft" -> openCraftables(player, runtime);
                case "jobs" -> openJobs(player, runtime);
                case "info" -> status(player, runtime);
                case "refresh" -> openTerminal(player, runtime, page);
                default -> openTerminal(player, runtime, page);
            }
        });
    }

    private void withdraw(Player player, NetworkRuntime runtime, ItemKey key, long wanted) {
        Optional<ItemStack> template = StackCodec.stack(key, 1);
        if (template.isEmpty()) {
            return;
        }
        long want = wanted >= 64 ? template.get().getMaxStackSize() : Math.min(wanted, template.get().getMaxStackSize());
        long taken = runtime.storage().extract(key, want);
        if (taken <= 0) {
            return;
        }
        give(player, key, taken);
        markDirty(runtime);
    }

    private void give(Player player, ItemKey key, long amount) {
        long left = amount;
        while (left > 0) {
            int part = (int) Math.min(left, 64);
            Optional<ItemStack> stack = StackCodec.stack(key, part);
            if (stack.isEmpty()) {
                return;
            }
            part = Math.min(part, stack.get().getMaxStackSize());
            stack.get().setAmount(part);
            player.getInventory().addItem(stack.get()).values()
                    .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            left -= part;
        }
    }

    private void insertHeld(Player player, NetworkRuntime runtime) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            say(player, "<gray>Your hand is empty.");
            return;
        }
        long accepted = runtime.storage().insert(StackCodec.key(held), held.getAmount());
        held.setAmount((int) (held.getAmount() - accepted));
        markDirty(runtime);
        if (accepted == 0) {
            say(player, "<red>The network has no room for that.");
        }
    }

    private void insertInventory(Player player, NetworkRuntime runtime) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            long accepted = runtime.storage().insert(StackCodec.key(stack), stack.getAmount());
            stack.setAmount((int) (stack.getAmount() - accepted));
        }
        player.getInventory().setStorageContents(contents);
        markDirty(runtime);
    }

    /** Cells changed: schedule a write and refresh nothing structural. */
    private void markDirty(NetworkRuntime runtime) {
        runtime.topology().online(NodeKind.DRIVE).forEach(drive -> services.state.driveCells(drive)
                .forEach(services.state::markCell));
    }

    // --- crafting ----------------------------------------------------------------------------------------------------

    private void openCraftables(Player player, NetworkRuntime runtime) {
        Set<ItemKey> outputs = new LinkedHashSet<>();
        runtime.patterns().stream().sorted(Comparator.comparing(Pattern::id)).forEach(p -> outputs.add(p.output()));
        if (outputs.isEmpty()) {
            say(player, "<gray>No patterns. Put encoded patterns into a pattern provider.");
            openTerminal(player, runtime, 0);
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
                openTerminal(player, runtime, 0);
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
                openTerminal(player, runtime, 0);
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
        markDirty(runtime);
        openTerminal(player, runtime, 0);
    }

    private void openJobs(Player player, NetworkRuntime runtime) {
        List<MenuButton> buttons = new ArrayList<>();
        Map<GridPos, CraftingJob> jobs = runtime.jobs();
        jobs.forEach((anchor, job) -> StackCodec.stack(job.plan().target(), 1).ifPresent(stack ->
                buttons.add(new MenuButton("cancel:" + anchor.encode(), "<white>" + escape(StackCodec.name(stack))
                        + " <gray>x" + job.plan().amount() + " - " + job.craftsLeft() + " crafts left <red>(click to cancel)",
                        StackCodec.icon(stack)))));
        if (buttons.isEmpty()) {
            say(player, "<gray>No crafting jobs are running.");
            openTerminal(player, runtime, 0);
            return;
        }
        buttons.add(new MenuButton("back", "<gray>Back", key("minecraft", "barrier")));
        Prism.menus().open(player, new MenuSpec("<dark_aqua>Crafting jobs", buttons), id -> {
            if (id.startsWith("cancel:")) {
                CraftingJob job = jobs.get(GridPos.decode(id.substring(7)));
                if (job != null) {
                    job.cancel();
                    markDirty(runtime);
                }
            }
            openTerminal(player, runtime, 0);
        });
    }

    // --- drive -------------------------------------------------------------------------------------------------------

    private void openDrive(Player player, NetworkRuntime runtime, GridPos pos) {
        List<MenuButton> buttons = new ArrayList<>();
        for (int slot = 0; slot < Ae2State.DRIVE_SLOTS; slot++) {
            Optional<StorageCell> cell = services.state.driveCell(pos, slot);
            buttons.add(cell.map(c -> new MenuButton("slot:" + buttons.size(), "<white>" + c.tier().name()
                    + " cell <gray>" + c.usedBytes() + "/" + c.tier().bytes() + " bytes, " + c.types()
                    + " types <red>(click to remove)", Ae2Content.cellIcon(c.tier()))).orElseGet(() ->
                    new MenuButton("slot:" + buttons.size(), "<gray>Empty slot <dark_gray>(hold a cell, click to insert)",
                            PANE)));
        }
        buttons.add(new MenuButton("close", "<gray>Close", key("minecraft", "barrier")));
        Prism.menus().open(player, new MenuSpec("<dark_aqua>ME Drive", buttons), id -> {
            if (!id.startsWith("slot:")) {
                return;
            }
            int slot = Integer.parseInt(id.substring(5));
            if (services.state.driveCell(pos, slot).isPresent()) {
                services.state.takeCellFromDrive(pos, slot).ifPresent(cell -> give(player, cell));
            } else {
                insertCell(player, pos, slot);
            }
            services.grid.refresh();
            openDrive(player, runtime, pos);
        });
    }

    private void insertCell(Player player, GridPos pos, int slot) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<StorageCell.Tier> tier = held.getType().isAir() ? Optional.empty()
                : Prism.items().identify(held).flatMap(Ae2Content::cellTier);
        if (tier.isEmpty()) {
            say(player, "<red>Hold a storage cell in your main hand.");
            return;
        }
        String stored = held.getItemMeta().getPersistentDataContainer().get(services.cellIdKey,
                PersistentDataType.STRING);
        StorageCell cell = null;
        if (stored != null) {
            try {
                cell = services.state.cell(UUID.fromString(stored)).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // an unreadable id means a fresh cell
            }
        }
        if (cell == null) {
            cell = new StorageCell(UUID.randomUUID(), tier.get());
            services.state.registerCell(cell);
        }
        services.state.putCellInDrive(pos, slot, cell);
        held.setAmount(held.getAmount() - 1);
    }

    /** Hands the player the item form of {@code cell}, carrying its id so it finds its contents again. */
    private void give(Player player, StorageCell cell) {
        Optional<ItemStack> item = cellItem(cell);
        item.ifPresent(stack -> player.getInventory().addItem(stack).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest)));
    }

    Optional<ItemStack> cellItem(StorageCell cell) {
        Optional<ItemStack> item = Prism.items().create(Ae2Content.cellKey(cell.tier()), 1);
        item.ifPresent(stack -> stack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(services.cellIdKey, PersistentDataType.STRING,
                    cell.id().toString());
            meta.lore(List.of(MINI.deserialize("<gray>" + cell.usedBytes() + "/" + cell.tier().bytes() + " bytes, "
                    + cell.types() + " types").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                    false)));
        }));
        return item;
    }

    void dropDriveContents(GridPos pos, Location at) {
        for (int slot = 0; slot < Ae2State.DRIVE_SLOTS; slot++) {
            services.state.takeCellFromDrive(pos, slot).flatMap(this::cellItem).ifPresent(stack ->
                    at.getWorld().dropItemNaturally(at.clone().add(0.5, 0.5, 0.5), stack));
        }
        services.state.removeDrive(pos);
    }

    // --- pattern provider --------------------------------------------------------------------------------------------

    private void openProvider(Player player, NetworkRuntime runtime, GridPos pos) {
        List<MenuButton> buttons = new ArrayList<>();
        List<Pattern> patterns = services.state.patterns(pos);
        for (int i = 0; i < patterns.size() && i < 45; i++) {
            Pattern pattern = patterns.get(i);
            final int index = i;
            StackCodec.stack(pattern.output(), 1).ifPresent(stack -> buttons.add(new MenuButton(
                    "rm:" + index, "<white>" + escape(StackCodec.name(stack)) + " <gray>x"
                            + pattern.outputCount() + " <red>(click to remove)", StackCodec.icon(stack))));
        }
        buttons.add(new MenuButton("add", "<green>Insert the encoded pattern you hold", key("minecraft", "paper")));
        buttons.add(new MenuButton("close", "<gray>Close", key("minecraft", "barrier")));
        Prism.menus().open(player, new MenuSpec("<dark_aqua>Pattern Provider", buttons), id -> {
            if (id.startsWith("rm:")) {
                services.state.removePattern(pos, Integer.parseInt(id.substring(3))).ifPresent(p ->
                        patternItem(p).ifPresent(stack -> player.getInventory().addItem(stack).values().forEach(
                                rest -> player.getWorld().dropItem(player.getLocation(), rest))));
            } else if (id.equals("add")) {
                ItemStack held = player.getInventory().getItemInMainHand();
                Optional<Pattern> pattern = readPattern(held);
                if (pattern.isPresent()) {
                    services.state.addPattern(pos, pattern.get());
                    held.setAmount(held.getAmount() - 1);
                } else {
                    say(player, "<red>Hold an encoded pattern in your main hand.");
                }
            } else {
                return;
            }
            services.grid.refresh();
            openProvider(player, runtime, pos);
        });
    }

    Optional<Pattern> readPattern(ItemStack stack) {
        if (stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String text = stack.getItemMeta().getPersistentDataContainer().get(services.patternKey,
                PersistentDataType.STRING);
        return text == null ? Optional.empty() : PatternCodec.decode(text);
    }

    Optional<ItemStack> patternItem(Pattern pattern) {
        Optional<ItemStack> item = Prism.items().create(new PrismKey("ae2", "encoded_pattern"), 1);
        item.ifPresent(stack -> stack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(services.patternKey, PersistentDataType.STRING,
                    PatternCodec.encode(pattern));
            String name = StackCodec.stack(pattern.output(), 1).map(StackCodec::name).orElse("?");
            meta.lore(List.of(MINI.deserialize("<gray>Makes " + pattern.outputCount() + " " + escape(name))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        }));
        return item;
    }

    void dropProviderContents(GridPos pos, Location at) {
        for (Pattern pattern : services.state.removeProvider(pos)) {
            patternItem(pattern).ifPresent(stack ->
                    at.getWorld().dropItemNaturally(at.clone().add(0.5, 0.5, 0.5), stack));
        }
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
