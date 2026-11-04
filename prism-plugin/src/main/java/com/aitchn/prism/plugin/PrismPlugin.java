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
        ModManager mods = new ModManager(VanillaItems::exists);
        runtime = new PrismRuntime(info, scheduler, mods);
        BukkitItemService items = new BukkitItemService(this, mods.items());
        ModLifecycle lifecycle = new ModLifecycle(this, mods);
        BehaviorDispatcher dispatcher = new BehaviorDispatcher(
                mods.items(), player -> scheduler.isOwnedByCurrentThread(player), getLogger());
        Prism.install(lifecycle, items);
        getServer().getPluginManager().registerEvents(lifecycle, this);
        getServer().getPluginManager().registerEvents(new ItemListener(items, dispatcher), this);
        getServer().getServicesManager().register(PlatformInfo.class, info, this, ServicePriority.Normal);

        StartupReport.render(info, getPluginMeta().getVersion()).forEach(getLogger()::info);
        logHardDependency("packetevents");
        logHardDependency("BetterModel");

        // The marker is printed once the scheduler self check has passed, so the smoke test proves scheduling works.
        SchedulerSelfCheck.run(this, runtime, () -> getLogger().info(StartupReport.readyMarker(info)));
    }

    @Override
    public void onDisable() {
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
