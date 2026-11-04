package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.platform.PlatformInfo;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.pack.JavaPackBuilder;
import com.aitchn.prism.pack.PackFormats;
import com.aitchn.prism.pack.PackServer;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private volatile Published current;

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
            PackServer running = ensureServer();
            running.publish(result.sha1(), result.zip());
            String url = "http://" + publicHost() + ":" + config.getInt("pack.port", 8765) + "/" + result.sha1() + ".zip";
            current = new Published(url, HexFormat.of().parseHex(result.sha1()), result.sha1());
            plugin.getLogger().info("PRISM_PACK_READY sha1=" + result.sha1() + " files=" + result.fileCount()
                    + " bytes=" + result.zip().length + " url=" + url);
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.runOn(player, () -> send(player), null);
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("PRISM_PACK_FAIL " + e);
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
        Published pack = current;
        if (pack == null) {
            return;
        }
        Component prompt = MiniMessage.miniMessage().deserialize(config.getString("pack.prompt", ""));
        player.setResourcePack(PACK_ID, pack.url(), pack.hash(), prompt, config.getBoolean("pack.required", false));
    }

    private String publicHost() {
        String configured = config.getString("pack.public-host", "");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String serverIp = Bukkit.getIp();
        return serverIp.isBlank() ? "127.0.0.1" : serverIp;
    }

    private JavaPackBuilder.Input input() {
        List<ItemDefinition> items = new ArrayList<>();
        for (PrismKey key : mods.items().keys()) {
            mods.items().get(key).ifPresent(items::add);
        }
        List<BlockDefinition> blocks = new ArrayList<>();
        for (PrismKey key : mods.blocks().keys()) {
            mods.blocks().get(key).ifPresent(blocks::add);
        }
        int format = config.getInt("pack.format", 0);
        if (format <= 0) {
            format = PackFormats.forVersion(platform.minecraftVersion()).number();
        }
        return new JavaPackBuilder.Input(mods.assets(), items, blocks, format, "Prism content pack");
    }
}
