package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.platform.PlatformInfo;
import com.aitchn.prism.client.ClientStates;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.look.DisplayTable;
import com.aitchn.prism.core.look.LookAllocator;
import com.aitchn.prism.core.look.LookRequest;
import com.aitchn.prism.core.look.LookTable;
import com.aitchn.prism.core.look.PhysicsClass;
import com.aitchn.prism.core.look.RenderSettings;
import com.aitchn.prism.core.look.ResolvedPool;
import com.aitchn.prism.core.menu.MenuArt;
import com.aitchn.prism.core.menu.MenuLayout;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.pack.BedrockPackBuilder;
import com.aitchn.prism.pack.JavaPackBuilder;
import com.aitchn.prism.pack.PackFormats;
import com.aitchn.prism.pack.PackServer;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Keeps the Java resource pack in step with the active mods: resolves appearance immediately (cheap), compresses and
 * serves the zip in the background, and sends the pack to players. The pack id is fixed so a new build replaces the
 * previous one on the client.
 */
final class PackManager implements Listener {

    static final UUID PACK_ID = UUID.nameUUIDFromBytes("prism:content-pack".getBytes(StandardCharsets.UTF_8));

    private final Plugin plugin;
    private final ModManager mods;
    private final PlatformScheduler scheduler;
    private final PlatformInfo platform;
    private final Appearance appearance;
    private final FileConfiguration config;
    private final AtomicBoolean buildQueued = new AtomicBoolean();
    private volatile boolean serverStarted;
    private volatile PackServer server;
    private volatile RenderSettings settings = RenderSettings.DEFAULT;
    private volatile boolean solidShells;
    private volatile ResolvedPool pool;
    private volatile ClientLooks looks = ClientLooks.EMPTY;
    private volatile GeyserInstaller geyser;
    private final BedrockBlocks bedrock;
    private final Object looksLock = new Object();
    private volatile Runnable relooked = () -> {};
    private volatile java.util.function.Consumer<java.util.Map<PrismKey, MenuLayout>> menusChanged = layouts -> {};
    private final java.util.concurrent.atomic.AtomicInteger generation = new java.util.concurrent.atomic.AtomicInteger();
    private BedrockPackBuilder.Result bedrockCache;
    private int bedrockCacheGeneration = -1;
    private final java.util.Map<UUID, Published> packs = new java.util.concurrent.ConcurrentHashMap<>();

    private record Published(String url, byte[] hash, String sha1) {}

    PackManager(Plugin plugin, ModManager mods, PlatformScheduler scheduler, PlatformInfo platform,
            Appearance appearance, FileConfiguration config) {
        this.plugin = plugin;
        this.mods = mods;
        this.scheduler = scheduler;
        this.platform = platform;
        this.appearance = appearance;
        this.config = config;
        this.bedrock = new BedrockBlocks(plugin.getLogger(),
                BedrockBlocks.Mode.parse(config.getString("bedrock.synthetic-blocks", "auto"), plugin.getLogger()),
                Math.max(0, config.getInt("bedrock.synthetic-base", 0)),
                plugin.getDataFolder().toPath().resolve("geyser"), this::bedrockResult, this::relook,
                this::reinstallGeyser, () -> geyser);
    }

    /** The synthetic block states of Geyser viewers (ADR-0024). */
    BedrockBlocks bedrockBlocks() {
        return bedrock;
    }

    /**
     * @param settings    how looks are drawn by default and how display looks are lit ({@code blocks.render},
     *                    {@code blocks.display.lighting}, ADR-0025)
     * @param solidShells whether display looks may use the opaque shell of the solid-layer note block
     *                    ({@code blocks.display.solid-shells}; face lighting only)
     */
    void configure(RenderSettings settings, boolean solidShells) {
        this.settings = settings;
        this.solidShells = solidShells;
    }

    /** What clients are shown for each look right now; replaced as a whole when the content changes. */
    ClientLooks looks() {
        return looks;
    }

    /** Copies the Bedrock pack and mappings into a Geyser running on this server after every build. */
    void installInto(GeyserInstaller installer) {
        this.geyser = installer;
    }

    /** Re-resolves appearance now, and schedules a zip build once the server has started. */
    void contentChanged() {
        List<BlockDefinition> blocks = blockList();
        bedrock.plan(allLookIds(blocks));
        LookTable table = allocate(blocks);
        JavaPackBuilder.Resolved resolved = JavaPackBuilder.resolve(input(blocks, table));
        try {
            ClientLooks built = ClientLooks.of(table, DisplayTable.of(table, resolved.displayPlans()));
            synchronized (looksLock) {
                looks = built.withBedrock(bedrock.activeIds());
            }
        } catch (RuntimeException e) {
            looks = ClientLooks.EMPTY;
            plugin.getLogger().severe("PRISM_LOOKS_FAIL " + e);
        }
        generation.incrementAndGet();
        table.warnings().forEach(warning -> plugin.getLogger().warning("PRISM_LOOK_WARNING " + warning));
        plugin.getLogger().info("PRISM_LOOKS total=" + table.placements().size()
                + " state=" + table.count(LookTable.Tier.STATE) + " display=" + table.count(LookTable.Tier.DISPLAY)
                + " unplaced=" + table.unplaced().size() + " families=" + table.activeBlocks().size());
        appearance.update(resolved.itemsWithAppearance(), resolved.blocksWithAppearance());
        menusChanged.accept(resolved.menus().layouts());
        if (serverStarted && config.getBoolean("pack.enabled", true)) {
            queueBuild();
        }
    }

    /** Gives the current looks the synthetic ids that were just registered (called when Geyser accepted them). */
    private void relook() {
        synchronized (looksLock) {
            looks = looks.withBedrock(bedrock.activeIds());
        }
        relooked.run();
    }

    /** Receives the menu art layouts (ADR-0028) of every content change. */
    void onMenus(java.util.function.Consumer<java.util.Map<PrismKey, MenuLayout>> listener) {
        this.menusChanged = listener;
    }

    /** Called after Geyser viewers got synthetic ids, to publish the looks of the chunks that are already loaded. */
    void onRelook(Runnable listener) {
        this.relooked = listener;
    }

    /** Writes the Geyser files of the legacy route again, after the API route failed. */
    private void reinstallGeyser() {
        GeyserInstaller installer = geyser;
        if (installer != null) {
            BedrockPackBuilder.Result result = bedrockResult();
            installer.install(result.mcpack(), result.mappings(), result.blockMappings(), false);
        }
    }

    /** Builds the Bedrock pack of the current content once per content change. */
    private synchronized BedrockPackBuilder.Result bedrockResult() {
        int current = generation.get();
        if (bedrockCache == null || bedrockCacheGeneration != current) {
            bedrockCache = BedrockPackBuilder.build(bedrockInput());
            bedrockCacheGeneration = current;
        }
        return bedrockCache;
    }

    /** The Bedrock pack build of the current content, on the calling thread (for the self tests). */
    BedrockPackBuilder.Result bedrockNow() {
        return bedrockResult();
    }

    /** Every look id of every block, whatever its backend: the synthetic ids are allocated for all of them. */
    private static List<String> allLookIds(List<BlockDefinition> blocks) {
        List<String> ids = new ArrayList<>();
        for (BlockDefinition block : blocks) {
            for (Appearances.Look look : Appearances.looks(block)) {
                ids.add(look.id());
            }
        }
        return ids;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        serverStarted = true;
        bedrock.serverLoaded();
        if (config.getBoolean("pack.enabled", true)) {
            queueBuild();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    void shutdown() {
        PackServer running = server;
        if (running != null) {
            running.close();
        }
    }

    /** Builds the Java pack from the current looks on the calling thread (for the synthetic self test). */
    JavaPackBuilder.Result buildNow() {
        return JavaPackBuilder.build(input());
    }

    /** The Java pack's files for the current looks, unzipped, on the calling thread (for the self tests). */
    JavaPackBuilder.Resolved resolveNow() {
        return JavaPackBuilder.resolve(input());
    }

    private void queueBuild() {
        if (buildQueued.compareAndSet(false, true)) {
            scheduler.runAsync(this::build);
        }
    }

    private void build() {
        buildQueued.set(false);
        try {
            JavaPackBuilder.Result result = JavaPackBuilder.build(input());
            result.warnings().forEach(warning -> plugin.getLogger().warning("PRISM_PACK_WARNING " + warning));
            publish(PACK_ID, "PRISM_PACK_READY", result.sha1(), result.zip(), result.fileCount());
            if (!result.menus().layouts().isEmpty()) {
                plugin.getLogger().info("PRISM_PACK_MENUS menus=" + result.menus().layouts().size()
                        + " backgrounds=" + result.menus().backgrounds() + " widgets=" + result.menus().widgets()
                        + " glyphs=" + result.menus().glyphs());
            }
            BedrockPackBuilder.Result built = bedrockResult();
            built.warnings().forEach(warning -> plugin.getLogger().warning("PRISM_PACK_WARNING " + warning));
            ensureServer().publishBedrock(built.sha1(), built.mcpack(), built.mappings());
            plugin.getLogger().info("PRISM_BEDROCK_PACK_READY sha1=" + built.sha1() + " items="
                    + built.itemsWithIcon().size() + " blocks=" + built.blockLooks().size()
                    + " sounds=" + built.soundEvents() + " flipbooks=" + built.flipbooks()
                    + " geometries=" + built.geometries() + " gauges=" + built.gaugeIcons()
                    + " bytes=" + built.mcpack().length + " synthetic=" + built.syntheticBlocks().size());
            GeyserInstaller installer = geyser;
            if (installer != null) {
                installer.install(built.mcpack(), built.mappings(), built.blockMappings(), bedrock.apiRoute());
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("PRISM_PACK_FAIL " + e);
        }
    }

    /**
     * Serves and sends a pack built by someone else (BetterModel's own pack) next to Prism's. Several packs per
     * client are supported since 1.20.3; Prism does not merge them.
     */
    void publishExternal(String name, byte[] zip) {
        if (!config.getBoolean("pack.enabled", true)) {
            return;
        }
        try {
            String sha1 = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(zip));
            publish(UUID.nameUUIDFromBytes(("prism:external:" + name).getBytes(StandardCharsets.UTF_8)),
                    "PRISM_EXTERNAL_PACK_READY name=" + name, sha1, zip, -1);
        } catch (IOException | java.security.NoSuchAlgorithmException | RuntimeException e) {
            plugin.getLogger().severe("PRISM_PACK_FAIL " + name + " " + e);
        }
    }

    private void publish(UUID id, String marker, String sha1, byte[] zip, int files) throws IOException {
        ensureServer().publish(id.toString(), sha1, zip);
        String url = "http://" + publicHost() + ":" + config.getInt("pack.port", 8765) + "/" + sha1 + ".zip";
        packs.put(id, new Published(url, HexFormat.of().parseHex(sha1), sha1));
        plugin.getLogger().info(marker + " sha1=" + sha1 + (files >= 0 ? " files=" + files : "")
                + " bytes=" + zip.length + " url=" + url);
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runOn(player, () -> send(player), null);
        }
    }

    private synchronized PackServer ensureServer() throws IOException {
        if (server == null) {
            PackServer created = new PackServer(config.getString("pack.bind", "0.0.0.0"), config.getInt("pack.port", 8765));
            created.start();
            server = created;
        }
        return server;
    }

    private void send(Player player) {
        Component prompt = MiniMessage.miniMessage().deserialize(config.getString("pack.prompt", ""));
        boolean required = config.getBoolean("pack.required", false);
        if (packs.isEmpty()) {
            return;
        }
        // One request with every pack: the single-pack setResourcePack overload clears previously sent packs.
        List<ResourcePackInfo> infos = new ArrayList<>();
        packs.forEach((id, pack) -> infos.add(ResourcePackInfo.resourcePackInfo(
                id, URI.create(pack.url()), pack.sha1())));
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(infos).prompt(prompt).required(required).replace(true).build());
    }

    private String publicHost() {
        String configured = config.getString("pack.public-host", "");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String serverIp = Bukkit.getIp();
        return serverIp.isBlank() ? "127.0.0.1" : serverIp;
    }

    private List<ItemDefinition> itemList() {
        List<ItemDefinition> items = new ArrayList<>();
        for (PrismKey key : mods.items().keys()) {
            mods.items().get(key).ifPresent(items::add);
        }
        return items;
    }

    private BedrockPackBuilder.Input bedrockInput() {
        List<BlockDefinition> blocks = blockList();
        return new BedrockPackBuilder.Input(mods.assets(), itemList(), "Prism Bedrock content", blocks,
                bedrockLooks(looks.table()), bedrock.plannedForBuild());
    }

    private List<BlockDefinition> blockList() {
        List<BlockDefinition> blocks = new ArrayList<>();
        for (PrismKey key : mods.blocks().keys()) {
            mods.blocks().get(key).ifPresent(blocks::add);
        }
        return blocks;
    }

    /** Look id to the note block or tripwire state index Geyser's block mappings know, for the looks Bedrock can draw. */
    private static java.util.Map<String, Integer> bedrockLooks(LookTable table) {
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        table.placements().keySet().forEach(id -> table.bedrockIndex(id).ifPresent(index -> result.put(id, index)));
        return result;
    }

    /**
     * Asks the pool for a client representation of every look of every block drawn as a client-side look (a borrowed
     * state or the display layer) under the {@code blocks.render} setting, in a fixed order, so the same content always
     * gets the same table and the same pack. Nothing is remembered from earlier boots (ADR-0023).
     */
    private LookTable allocate(List<BlockDefinition> blocks) {
        List<LookRequest> requests = new ArrayList<>();
        for (BlockDefinition block : blocks) {
            boolean state = PrismBlockService.usesCarrier(block);
            boolean bedrockOnly = !state && PrismBlockService.bedrockOnlyLooks(block);
            if (!state && !bedrockOnly) {
                continue;
            }
            PhysicsClass physics = Appearances.physics(block);
            PrismComponents.Render render = Appearances.render(block);
            for (Appearances.Look look : Appearances.looks(block)) {
                requests.add(new LookRequest(look.id(), physics, render, bedrockOnly));
            }
        }
        ResolvedPool resolved = pool;
        if (resolved == null) {
            try {
                resolved = ResolvedPool.resolve(family -> ClientStates.states(family.block()));
            } catch (RuntimeException e) {
                plugin.getLogger().severe("PRISM_LOOKS_FAIL cannot read the block states of this server: " + e);
                resolved = new ResolvedPool(List.of(), List.of());
            }
            pool = resolved;
        }
        return LookAllocator.allocate(resolved, requests, solidShells, settings);
    }

    private JavaPackBuilder.Input input() {
        return input(blockList(), looks.table());
    }

    private JavaPackBuilder.Input input(List<BlockDefinition> blocks, LookTable table) {
        List<ItemDefinition> items = itemList();
        int format = config.getInt("pack.format", 0);
        if (format <= 0) {
            format = PackFormats.forVersion(platform.minecraftVersion()).number();
        }
        List<MenuArt> menus = new ArrayList<>();
        for (PrismKey key : mods.menus().keys()) {
            mods.menus().get(key).ifPresent(menus::add);
        }
        return new JavaPackBuilder.Input(mods.assets(), items, blocks, format, "Prism content pack", table,
                settings.lighting(), menus);
    }
}
