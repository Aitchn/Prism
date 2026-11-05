package com.aitchn.validation.botania;

import com.aitchn.prism.api.mod.ContentView;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Botania rebuilt as a plain plugin on Prism. This is a validation of the Prism API, not a product: see
 * docs/validation/botania.md for what it covers and which gaps it found.
 */
public final class BotaniaPlugin extends JavaPlugin implements PrismMod, Listener {

    private BotaniaRuntime runtime;

    @Override
    public void onEnable() {
        runtime = new BotaniaRuntime(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onInitialize(@NotNull ModContext context) {
        BotaniaContent.register(context, () -> runtime);
    }

    @Override
    public void onContentLoaded(@NotNull ContentView view) {
        getLogger().info("BOTANIA_ACTIVE items=" + view.items().size() + " blocks=" + view.blocks().size());
        // Chunks loaded before this mod activated: load their saved block entities on the owning region thread.
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                int x = chunk.getX();
                int z = chunk.getZ();
                Bukkit.getRegionScheduler().execute(this, world, x, z, () -> runtime.loadChunk(world, x, z));
            }
        }
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    /** Entities (and with them Prism's block markers) are loaded after the chunk itself. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        runtime.loadChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        runtime.unloadChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    /** Optional end-to-end check, enabled with {@code -Dbotania.selftest=true}; the smoke test uses it. */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (Boolean.getBoolean("botania.selftest") && !Bukkit.getWorlds().isEmpty()) {
            new SelfTest(this, runtime).start(Bukkit.getWorlds().get(0));
        }
    }
}
