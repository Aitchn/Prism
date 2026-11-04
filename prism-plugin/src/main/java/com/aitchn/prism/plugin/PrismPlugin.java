package com.aitchn.prism.plugin;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.platform.MinecraftVersion;
import com.aitchn.prism.api.platform.PlatformInfo;
import com.aitchn.prism.api.platform.PlatformKind;
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
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Bootstrap: detect the platform, pick the matching scheduler, assemble the runtime. */
public final class PrismPlugin extends JavaPlugin {

    private PrismRuntime runtime;
    private PackManager packManager;

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
        DisplayEntityBackend blocks = new DisplayEntityBackend(this, mods.blocks(), items, appearance);
        packManager = new PackManager(this, mods, scheduler, info, appearance, getConfig());
        ModLifecycle lifecycle = new ModLifecycle(this, mods, packManager::contentChanged);
        BehaviorDispatcher dispatcher = new BehaviorDispatcher(
                mods.items(), mods.blocks(), player -> scheduler.isOwnedByCurrentThread(player),
                location -> scheduler.isOwnedByCurrentThread(location), getLogger());
        Prism.install(lifecycle, items, blocks);
        getServer().getPluginManager().registerEvents(lifecycle, this);
        getServer().getPluginManager().registerEvents(new ItemListener(items, dispatcher), this);
        getServer().getPluginManager().registerEvents(
                new BlockListener(items, blocks, mods.items(), mods.blocks(), dispatcher), this);
        getServer().getPluginManager().registerEvents(packManager, this);
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
