package com.aitchn.prism.plugin;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.platform.MinecraftVersion;
import com.aitchn.prism.api.platform.PlatformInfo;
import com.aitchn.prism.api.platform.PlatformKind;
import com.aitchn.prism.client.ClientCanon;
import com.aitchn.prism.client.PacketRewriting;
import com.aitchn.prism.client.LookSource;
import com.aitchn.prism.core.block.ChunkLooks;
import com.aitchn.prism.core.client.Profiles;
import com.aitchn.prism.core.data.SqliteDataService;
import com.aitchn.prism.core.look.RenderSettings;
import com.aitchn.prism.core.PrismRuntime;
import com.aitchn.prism.core.StartupReport;
import com.aitchn.prism.core.recipe.RegistryRecipes;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import com.aitchn.prism.core.mod.ModManager;
import com.aitchn.prism.platform.folia.FoliaPlatformScheduler;
import com.aitchn.prism.platform.paper.PaperPlatformScheduler;
import com.aitchn.prism.platform.spi.PlatformDetector;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import com.aitchn.prism.platform.spi.UnsupportedPlatformException;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Bootstrap: detect the platform, pick the matching scheduler, assemble the runtime. */
public final class PrismPlugin extends JavaPlugin {

    private PrismRuntime runtime;
    private PackManager packManager;
    private SqliteDataService data;
    private Runnable uninstallRewriter;
    private Runnable uninstallItemText;

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
        // blocks.backend is the setting of earlier versions: read only when blocks.render is not set (ADR-0025). Only
        // values the server's config.yml really holds count (a null default skips the defaults of the bundled file),
        // so an old file with backend and without render keeps meaning what it says.
        RenderSettings render = RenderSettings.read(getConfig().getString("blocks.backend", null),
                getConfig().getString("blocks.render", null),
                getConfig().getString("blocks.display.lighting", null));
        render.notes().forEach(note -> getLogger().info("PRISM_RENDER_NOTE " + note));
        getLogger().info("PRISM_RENDER default=" + render.defaultRender().name().toLowerCase(java.util.Locale.ROOT)
                + " lighting=" + render.lighting().id());
        packManager.configure(render, getConfig().getBoolean("blocks.display.solid-shells", false));
        packManager.bedrockBlocks().attach();
        packManager.installInto(new GeyserInstaller(getLogger(), getDataFolder().toPath().resolve("geyser")));
        PrismBlockService blocks = new PrismBlockService(mods.blocks(), items, new BlockIndex(this), display,
                packManager::looks, dispatcher, scheduler, getLogger());
        RegistryRecipes recipes = new RegistryRecipes(mods.recipeTypes(), mods.recipes());
        VanillaRecipes vanillaRecipes = new VanillaRecipes(recipes, items, scheduler, getLogger());
        Runnable contentChanged = () -> {
            vanillaRecipes.sync();
            packManager.contentChanged();
            modelSync.contentChanged();
            blocks.contentChanged();
        };
        ModLifecycle lifecycle = new ModLifecycle(this, mods, contentChanged);
        PrismTranslations translations = new PrismTranslations(this,
                new com.aitchn.prism.core.lang.ItemTextRenderer(mods::translations, mods.items()), scheduler);
        ChestMenus menus = new ChestMenus(items, translations, scheduler, getLogger());
        BedrockDetector bedrockPlayers = BedrockDetector.create(getLogger());
        BedrockModelLooks modelLooks = new BedrockModelLooks(blocks, packManager::looks, bedrockPlayers, scheduler);
        packManager.onRelook(() -> scheduler.runGlobal(blocks::contentChanged));
        blocks.onLookChanged(modelLooks::changed);
        blocks.onChunkRelooked(modelLooks::relooked);
        getServer().getPluginManager().registerEvents(modelLooks, this);
        DisplayLayer displayLayer = new DisplayLayer(blocks, packManager::looks, scheduler, bedrockPlayers,
                Math.max(0, getConfig().getInt("blocks.display.view-distance", 8)),
                (float) getConfig().getDouble("blocks.display.view-range", 1.0),
                Math.max(0, getConfig().getInt("blocks.display.max-per-player", 12000)), render.lighting());
        blocks.onLookChanged(displayLayer::changed);
        blocks.onChunkRelooked(displayLayer::relooked);
        getServer().getPluginManager().registerEvents(displayLayer, this);
        getServer().getPluginManager().registerEvents(new BreakFeel(this, blocks, packManager::looks,
                id -> packManager.bedrockBlocks().bedrockStates() && bedrockPlayers.test(id)), this);
        Prism.install(lifecycle, items, blocks, new Profiles(bedrockPlayers), menus, data,
                new SchedulerAdapter(scheduler), recipes, translations);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(menus.containers(), this);
        getServer().getPluginManager().registerEvents(menus.prompts(), this);
        getServer().getPluginManager().registerEvents(vanillaRecipes, this);
        getServer().getPluginManager().registerEvents(lifecycle, this);
        getServer().getPluginManager().registerEvents(new ItemListener(items, dispatcher), this);
        getServer().getPluginManager().registerEvents(new ItemTextListener(items, translations, scheduler), this);
        uninstallItemText = ItemText.install(this, items, translations, scheduler);
        getServer().getPluginManager().registerEvents(blocks, this);
        getServer().getPluginManager().registerEvents(new BlockListener(items, blocks, mods.items(), dispatcher), this);
        getServer().getPluginManager().registerEvents(new DigListener(blocks, scheduler), this);
        getServer().getPluginManager().registerEvents(new NativeStateListener(blocks), this);
        getServer().getPluginManager().registerEvents(new LookReconciler(blocks, scheduler), this);
        uninstallRewriter = PacketRewriting.install(new LookSource() {
            @Override
            public ChunkLooks chunk(UUID world, int chunkX, int chunkZ) {
                return blocks.looks(world, chunkX, chunkZ);
            }

            @Override
            public boolean anyIn(UUID world) {
                return blocks.anyLooks(world);
            }

            @Override
            public ClientCanon canon() {
                return packManager.looks().canon();
            }

            @Override
            public boolean bedrockStates() {
                return packManager.bedrockBlocks().bedrockStates();
            }

            @Override
            public boolean isBedrock(UUID player) {
                return bedrockPlayers.test(player);
            }

            @Override
            public int bedrockGlobalBits() {
                return packManager.bedrockBlocks().globalBits();
            }
        }, blocks::foreignChange);
        var command = getCommand("prism");
        if (command != null) {
            PrismCommand executor = new PrismCommand(mods.items(), items, menus, scheduler,
                    new DisplayBench(this, mods, contentChanged, blocks, displayLayer, scheduler));
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
        String consistencySelfTest = System.getProperty("prism.selftest.consistency");
        if (consistencySelfTest != null) {
            getServer().getPluginManager().registerEvents(new ConsistencySelfTest(this, consistencySelfTest, blocks,
                    scheduler, getLogger()), this);
        }
        String syntheticSelfTest = System.getProperty("prism.selftest.synthetic");
        if (syntheticSelfTest != null) {
            getServer().getPluginManager().registerEvents(new SyntheticSelfTest(Integer.parseInt(syntheticSelfTest),
                    mods, contentChanged, packManager, blocks, displayLayer, scheduler, getLogger()), this);
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
        if (uninstallRewriter != null) {
            uninstallRewriter.run();
        }
        if (uninstallItemText != null) {
            uninstallItemText.run();
        }
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
