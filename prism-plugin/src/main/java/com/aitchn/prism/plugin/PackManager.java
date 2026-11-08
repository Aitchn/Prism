package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.platform.PlatformInfo;
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

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("prism:content-pack".getBytes(StandardCharsets.UTF_8));

    private final Plugin plugin;
    private final ModManager mods;
    private final PlatformScheduler scheduler;
    private final PlatformInfo platform;
    private final Appearance appearance;
    private final FileConfiguration config;
    private final AtomicBoolean buildQueued = new AtomicBoolean();
    private volatile boolean serverStarted;
    private volatile PackServer server;
    private volatile com.aitchn.prism.core.block.StateAllocator nativeStates;
    private volatile String backend = "display";
    private volatile GeyserInstaller geyser;
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
    }

    /** Native block looks get note block states in both packs; {@code backend} is the {@code blocks.backend} setting. */
    void useNativeStates(com.aitchn.prism.core.block.StateAllocator allocator, String backend) {
        this.nativeStates = allocator;
        this.backend = backend;
    }

    /** Copies the Bedrock pack and mappings into a Geyser running on this server after every build. */
    void installInto(GeyserInstaller installer) {
        this.geyser = installer;
    }

    /** Re-resolves appearance now, and schedules a zip build once the server has started. */
    void contentChanged() {
        JavaPackBuilder.Resolved resolved = JavaPackBuilder.resolve(input());
        appearance.update(resolved.itemsWithAppearance(), resolved.blocksWithAppearance());
        if (serverStarted && config.getBoolean("pack.enabled", true)) {
            queueBuild();
        }
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        serverStarted = true;
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
            BedrockPackBuilder.Result bedrock = BedrockPackBuilder.build(bedrockInput());
            bedrock.warnings().forEach(warning -> plugin.getLogger().warning("PRISM_PACK_WARNING " + warning));
            ensureServer().publishBedrock(bedrock.sha1(), bedrock.mcpack(), bedrock.mappings());
            plugin.getLogger().info("PRISM_BEDROCK_PACK_READY sha1=" + bedrock.sha1() + " items="
                    + bedrock.itemsWithIcon().size() + " blocks=" + bedrock.blockLooks().size()
                    + " bytes=" + bedrock.mcpack().length);
            GeyserInstaller installer = geyser;
            if (installer != null) {
                installer.install(bedrock.mcpack(), bedrock.mappings(), bedrock.blockMappings());
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
                nativeLooks(blocks));
    }

    private List<BlockDefinition> blockList() {
        List<BlockDefinition> blocks = new ArrayList<>();
        for (PrismKey key : mods.blocks().keys()) {
            mods.blocks().get(key).ifPresent(blocks::add);
        }
        return blocks;
    }

    /** Look id to note block state for every look of every native block, allocating states on first sight. */
    private java.util.Map<String, Integer> nativeLooks(List<BlockDefinition> blocks) {
        com.aitchn.prism.core.block.StateAllocator allocator = nativeStates;
        java.util.Map<String, Integer> looks = new java.util.LinkedHashMap<>();
        if (allocator == null) {
            return looks;
        }
        for (BlockDefinition block : blocks) {
            // Blocks that no longer get note block states for new placements (blocks.backend: display) keep the
            // looks they already own, so blocks placed earlier still draw correctly.
            boolean allocate = PrismBlockService.usesNoteBlocks(backend, block)
                    || PrismBlockService.bedrockLookForModel(backend, block);
            for (com.aitchn.prism.core.block.Appearances.Look look : com.aitchn.prism.core.block.Appearances.looks(block)) {
                (allocate ? allocator.allocate(look.id()) : allocator.indexOf(look.id()))
                        .ifPresent(index -> looks.put(look.id(), index));
            }
        }
        return looks;
    }

    private java.util.Map<Integer, com.aitchn.prism.pack.NativeBlockstates.StateModel> nativeModels(
            List<BlockDefinition> blocks) {
        java.util.Map<String, Integer> looks = nativeLooks(blocks);
        java.util.Map<Integer, com.aitchn.prism.pack.NativeBlockstates.StateModel> models = new java.util.LinkedHashMap<>();
        for (BlockDefinition block : blocks) {
            if (ModelAnchors.modelOf(block).isPresent()) {
                continue; // a Bedrock-only look: Java players never get this state, they see the model
            }
            for (com.aitchn.prism.core.block.Appearances.Look look : com.aitchn.prism.core.block.Appearances.looks(block)) {
                Integer index = looks.get(look.id());
                if (index != null) {
                    int[] rotation = com.aitchn.prism.core.block.Appearances.rotation(look.facing());
                    models.put(index, new com.aitchn.prism.pack.NativeBlockstates.StateModel(
                            com.aitchn.prism.core.block.Appearances.model(block, look.variant()), rotation[0], rotation[1]));
                }
            }
        }
        return models;
    }

    private JavaPackBuilder.Input input() {
        List<ItemDefinition> items = itemList();
        List<BlockDefinition> blocks = blockList();
        int format = config.getInt("pack.format", 0);
        if (format <= 0) {
            format = PackFormats.forVersion(platform.minecraftVersion()).number();
        }
        return new JavaPackBuilder.Input(mods.assets(), items, blocks, format, "Prism content pack",
                nativeModels(blocks));
    }
}
