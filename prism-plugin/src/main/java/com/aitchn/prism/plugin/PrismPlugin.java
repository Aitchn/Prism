package com.aitchn.prism.plugin;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.platform.MinecraftVersion;
import com.aitchn.prism.api.platform.PlatformInfo;
import com.aitchn.prism.api.platform.PlatformKind;
import com.aitchn.prism.core.client.Profiles;
import com.aitchn.prism.core.data.SqliteDataService;
import com.aitchn.prism.core.PrismRuntime;
import com.aitchn.prism.core.StartupReport;
import com.aitchn.prism.core.block.StateAllocator;
import com.aitchn.prism.core.recipe.RegistryRecipes;
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
        DisplayEntityBackend display = new DisplayEntityBackend(this, appearance, anchors);
        packManager = new PackManager(this, mods, scheduler, info, appearance, getConfig());
        ModelSync modelSync = new ModelSync(this, mods, scheduler, packManager, anchors);
        BehaviorDispatcher dispatcher = new BehaviorDispatcher(
                mods.items(), mods.blocks(), player -> scheduler.isOwnedByCurrentThread(player),
                location -> scheduler.isOwnedByCurrentThread(location), getLogger());
        try {
            getDataFolder().mkdirs();
            data = new SqliteDataService(getDataFolder().toPath().resolve("data.db"));
            data.store("prism").put("last-start", String.valueOf(System.currentTimeMillis()));
            getLogger().info("PRISM_DATA_OK");
        } catch (java.sql.SQLException | RuntimeException e) {
            throw new IllegalStateException("cannot open Prism's data store", e);
        }
        String backend = getConfig().getString("blocks.backend", "auto");
        StateAllocator allocator = new StateAllocator(data.store("prism"));
        NoteBlockAppearance noteBlocks = new NoteBlockAppearance(allocator, getLogger());
        StateAllocator tripwireAllocator = StateAllocator.tripwire(data.store("prism"));
        TripwireAppearance tripwires = new TripwireAppearance(tripwireAllocator, getLogger());
        packManager.useNativeStates(allocator, tripwireAllocator, backend);
        packManager.installInto(new GeyserInstaller(getLogger(), getDataFolder().toPath().resolve("geyser")));
        PrismBlockService blocks = new PrismBlockService(mods.blocks(), items, new BlockIndex(this), display,
                noteBlocks, tripwires, backend, dispatcher, scheduler, getLogger());
        RegistryRecipes recipes = new RegistryRecipes(mods.recipeTypes(), mods.recipes());
        VanillaRecipes vanillaRecipes = new VanillaRecipes(recipes, items, scheduler, getLogger());
        ModLifecycle lifecycle = new ModLifecycle(this, mods, () -> {
            vanillaRecipes.sync();
            packManager.contentChanged();
            modelSync.contentChanged();
            blocks.contentChanged();
        });
        ChestMenus menus = new ChestMenus(items, scheduler, getLogger());
        BedrockDetector bedrockPlayers = BedrockDetector.create(getLogger());
        BedrockModelLooks modelLooks = new BedrockModelLooks(blocks, noteBlocks, bedrockPlayers, scheduler);
        blocks.onLookChanged(modelLooks::changed);
        getServer().getPluginManager().registerEvents(modelLooks, this);
        Prism.install(lifecycle, items, blocks, new Profiles(bedrockPlayers), menus, data,
                new SchedulerAdapter(scheduler), recipes);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(menus.containers(), this);
        getServer().getPluginManager().registerEvents(menus.prompts(), this);
        getServer().getPluginManager().registerEvents(vanillaRecipes, this);
        getServer().getPluginManager().registerEvents(lifecycle, this);
        getServer().getPluginManager().registerEvents(new ItemListener(items, dispatcher), this);
        getServer().getPluginManager().registerEvents(blocks, this);
        getServer().getPluginManager().registerEvents(new BlockListener(items, blocks, mods.items(), dispatcher), this);
        getServer().getPluginManager().registerEvents(new DigListener(blocks, scheduler), this);
        getServer().getPluginManager().registerEvents(new NativeStateListener(blocks), this);
        var command = getCommand("prism");
        if (command != null) {
            PrismCommand executor = new PrismCommand(mods.items(), items, menus, scheduler);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getServer().getPluginManager().registerEvents(packManager, this);
        getServer().getPluginManager().registerEvents(anchors, this);
        getServer().getPluginManager().registerEvents(modelSync, this);
        String modelSelfTest = System.getProperty("prism.selftest.model");
        if (modelSelfTest != null) {
            modelSync.selfTest(blocks, com.aitchn.prism.api.PrismKey.parse(modelSelfTest),
                    block -> blocks.get(block).map(modelLooks::lookOf).isPresent());
        }
        String platformSelfTest = System.getProperty("prism.selftest.platform");
        if (platformSelfTest != null) {
            getServer().getPluginManager().registerEvents(new PlatformSelfTest(this,
                    com.aitchn.prism.api.PrismKey.parse(platformSelfTest),
                    com.aitchn.prism.api.PrismKey.parse(System.getProperty("prism.selftest", platformSelfTest)),
                    blocks, scheduler, getLogger()), this);
        }
        String connectionSelfTest = System.getProperty("prism.selftest.connections");
        if (connectionSelfTest != null) {
            getServer().getPluginManager().registerEvents(new ConnectionSelfTest(connectionSelfTest, blocks,
                    modelLooks::lookOf, scheduler, getLogger()), this);
        }
        String structureSelfTest = System.getProperty("prism.selftest.structure");
        if (structureSelfTest != null) {
            getServer().getPluginManager().registerEvents(new StructureSelfTest(structureSelfTest, blocks, scheduler,
                    getLogger()), this);
        }
        String lightSelfTest = System.getProperty("prism.selftest.light");
        if (lightSelfTest != null) {
            getServer().getPluginManager().registerEvents(new LightSelfTest(lightSelfTest, blocks, scheduler,
                    getLogger()), this);
        }
        String cuboidSelfTest = System.getProperty("prism.selftest.cuboid");
        if (cuboidSelfTest != null) {
            getServer().getPluginManager().registerEvents(new CuboidSelfTest(cuboidSelfTest, blocks, scheduler,
                    getLogger()), this);
        }
        String passableSelfTest = System.getProperty("prism.selftest.passable");
        if (passableSelfTest != null) {
            getServer().getPluginManager().registerEvents(new PassableSelfTest(
                    com.aitchn.prism.api.PrismKey.parse(passableSelfTest), blocks, scheduler, getLogger()), this);
        }
        String oreSelfTest = System.getProperty("prism.selftest.ore");
        if (oreSelfTest != null) {
            getServer().getPluginManager().registerEvents(new OreSelfTest(this,
                    com.aitchn.prism.api.PrismKey.parse(oreSelfTest), blocks, scheduler, getLogger()), this);
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
