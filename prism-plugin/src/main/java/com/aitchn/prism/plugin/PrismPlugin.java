package com.aitchn.prism.plugin;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.platform.MinecraftVersion;
import com.aitchn.prism.api.platform.PlatformInfo;
import com.aitchn.prism.api.platform.PlatformKind;
import com.aitchn.prism.core.client.Profiles;
import com.aitchn.prism.core.data.SqliteDataService;
import com.aitchn.prism.core.PrismRuntime;
import com.aitchn.prism.core.StartupReport;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.platform.folia.FoliaPlatformScheduler;
import com.aitchn.prism.platform.paper.PaperPlatformScheduler;
import com.aitchn.prism.platform.spi.PlatformDetector;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.UnsupportedPlatformException;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Bootstrap: detect the platform, pick the matching scheduler, assemble the runtime. */
public final class PrismPlugin extends JavaPlugin {

    private PrismRuntime runtime;
    private PackManager packManager;
    private SqliteDataService data;

    @Override
    public void onEnable() {
        PlatformInfo info;
        try {
            PlatformKind kind = PlatformDetector.detect(PrismPlugin::classExists);
            info = PlatformInfo.of(kind, Bukkit.getName(), MinecraftVersion.parse(Bukkit.getMinecraftVersion()));
        } catch (UnsupportedPlatformException | IllegalArgumentException e) {
            getLogger().severe(e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Only the matching implementation class is ever loaded.
        PlatformScheduler scheduler = info.kind() == PlatformKind.FOLIA
                ? new FoliaPlatformScheduler(this)
                : new PaperPlatformScheduler(this);
        saveDefaultConfig();
        ModManager mods = new ModManager(VanillaItems::exists, VanillaItems::blockExists);
        Appearance appearance = new Appearance();
        runtime = new PrismRuntime(info, scheduler, mods);
        BukkitItemService items = new BukkitItemService(this, mods.items(), appearance);
        ModelAnchors anchors = new ModelAnchors(new NamespacedKey(this, "block"), mods.blocks(), scheduler, getLogger());
        BehaviorDispatcher dispatcher = new BehaviorDispatcher(
                mods.items(), mods.blocks(), player -> scheduler.isOwnedByCurrentThread(player),
                location -> scheduler.isOwnedByCurrentThread(location), getLogger());
        DisplayEntityBackend blocks =
                new DisplayEntityBackend(this, mods.blocks(), items, appearance, anchors, dispatcher);
        packManager = new PackManager(this, mods, scheduler, info, appearance, getConfig());
        ModelSync modelSync = new ModelSync(this, mods, scheduler, packManager, anchors);
        ModLifecycle lifecycle = new ModLifecycle(this, mods, () -> {
            packManager.contentChanged();
            modelSync.contentChanged();
        });
        try {
            getDataFolder().mkdirs();
            data = new SqliteDataService(getDataFolder().toPath().resolve("data.db"));
            data.store("prism").put("last-start", String.valueOf(System.currentTimeMillis()));
            getLogger().info("PRISM_DATA_OK");
        } catch (java.sql.SQLException | RuntimeException e) {
            throw new IllegalStateException("cannot open Prism's data store", e);
        }
        ChestMenus menus = new ChestMenus(items, scheduler);
        Prism.install(lifecycle, items, blocks, new Profiles(BedrockDetector.create(getLogger())), menus, data);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(lifecycle, this);
        getServer().getPluginManager().registerEvents(new ItemListener(items, dispatcher), this);
        getServer().getPluginManager().registerEvents(
                new BlockListener(items, blocks, mods.items(), mods.blocks(), dispatcher), this);
        getServer().getPluginManager().registerEvents(packManager, this);
        getServer().getPluginManager().registerEvents(anchors, this);
        getServer().getPluginManager().registerEvents(modelSync, this);
        String modelSelfTest = System.getProperty("prism.selftest.model");
        if (modelSelfTest != null) {
            modelSync.selfTest(blocks, com.aitchn.prism.api.PrismKey.parse(modelSelfTest));
        }
        String selfTest = System.getProperty("prism.selftest");
        if (selfTest != null) {
            getServer().getPluginManager().registerEvents(
                    new BlockSelfTest(selfTest, blocks, scheduler, getLogger()), this);
        }
        getServer().getServicesManager().register(PlatformInfo.class, info, this, ServicePriority.Normal);

        StartupReport.render(info, getPluginMeta().getVersion()).forEach(getLogger()::info);
        logHardDependency("packetevents");
        logHardDependency("BetterModel");

        // The marker is printed once the scheduler self check has passed, so the smoke test proves scheduling works.
        SchedulerSelfCheck.run(this, runtime, () -> getLogger().info(StartupReport.readyMarker(info)));
    }

    @Override
    public void onDisable() {
        if (data != null) {
            data.close();
        }
        if (packManager != null) {
            packManager.shutdown();
        }
        Prism.uninstall();
        getServer().getServicesManager().unregisterAll(this);
        runtime = null;
    }

    /** The running runtime, or null if Prism disabled itself. */
    public PrismRuntime runtime() {
        return runtime;
    }

    private void logHardDependency(String name) {
        var dependency = getServer().getPluginManager().getPlugin(name);
        getLogger().info("Dependency " + name + ": "
                + (dependency == null ? "MISSING" : dependency.getPluginMeta().getVersion()));
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, PrismPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
