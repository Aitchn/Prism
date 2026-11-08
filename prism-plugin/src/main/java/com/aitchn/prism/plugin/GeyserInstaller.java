package com.aitchn.prism.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Copies Prism's Bedrock pack and Geyser custom mappings into a Geyser running on this server, so Bedrock players see
 * custom items and blocks without manual file copying. Geyser reads both only at startup, so a changed file needs a
 * restart; Prism says so in the log. Files are only rewritten when their bytes changed.
 */
final class GeyserInstaller {

    static final String ITEMS_FILE = "prism_items.json";
    static final String BLOCKS_FILE = "prism_blocks.json";
    static final String PACK_FILE = "prism.mcpack";
    /** The synthetic block export for a Prism Geyser extension, written to Prism's own folder (ADR-0024). */
    static final String SYNTHETIC_FILE = "prism_synthetic_blocks.json";

    private final Logger log;
    private final Path fallbackFolder;
    private boolean warned;

    /** {@code fallbackFolder} receives the files when no Geyser plugin runs here (for a proxy-hosted Geyser). */
    GeyserInstaller(Logger log, Path fallbackFolder) {
        this.log = log;
        this.fallbackFolder = fallbackFolder;
    }

    /**
     * @param apiRoute the blocks and the pack reach Geyser through its API (ADR-0024): its folder then gets only the
     *                 item mappings, and the legacy block mappings and pack are removed from it, since Geyser would
     *                 otherwise load the same textures twice
     */
    synchronized void install(byte[] mcpack, String itemMappings, String blockMappings, boolean apiRoute) {
        Plugin geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        Path root = geyser != null ? geyser.getDataFolder().toPath() : fallbackFolder;
        List<String> changed = new ArrayList<>();
        try {
            write(root.resolve("custom_mappings").resolve(ITEMS_FILE),
                    itemMappings.getBytes(StandardCharsets.UTF_8), changed);
            if (apiRoute && geyser != null) {
                remove(root.resolve("custom_mappings").resolve(BLOCKS_FILE), changed);
                remove(root.resolve("packs").resolve(PACK_FILE), changed);
            } else {
                write(root.resolve("custom_mappings").resolve(BLOCKS_FILE),
                        blockMappings.getBytes(StandardCharsets.UTF_8), changed);
                write(root.resolve("packs").resolve(PACK_FILE), mcpack, changed);
            }
        } catch (IOException e) {
            log.warning("PRISM_GEYSER_INSTALL_FAIL " + e);
            return;
        }
        if (geyser == null) {
            if (!warned) {
                log.info("Geyser is not installed on this server. If Geyser runs on your proxy, copy the folders in "
                        + root + " (custom_mappings and packs) into the proxy's Geyser folder and restart it.");
                warned = true;
            }
            log.info("PRISM_GEYSER_EXPORTED dir=" + root + " changed=" + changed);
            return;
        }
        log.info("PRISM_GEYSER_INSTALLED dir=" + root + " changed=" + changed
                + (changed.isEmpty() ? "" : " (restart the server so Geyser loads the new content)"));
        if (!customContentEnabled(root.resolve("config.yml")) && !warned) {
            log.warning("Geyser's custom content is off, so Bedrock players will not see Prism blocks and items. "
                    + "Set gameplay.enable-custom-content (older Geyser: enable-custom-content) to true in "
                    + root.resolve("config.yml") + " and restart.");
            warned = true;
        }
    }

    /** Writes the synthetic block export next to the other exported files (always Prism's own folder). */
    synchronized void exportSynthetic(String json) {
        try {
            List<String> changed = new ArrayList<>();
            write(fallbackFolder.resolve(SYNTHETIC_FILE), json.getBytes(StandardCharsets.UTF_8), changed);
            log.info("PRISM_BEDROCK_SYNTHETIC_EXPORTED file=" + fallbackFolder.resolve(SYNTHETIC_FILE));
        } catch (IOException e) {
            log.warning("PRISM_GEYSER_INSTALL_FAIL " + e);
        }
    }

    private static void remove(Path file, List<String> changed) throws IOException {
        if (Files.deleteIfExists(file)) {
            changed.add("-" + file.getFileName());
        }
    }

    private static void write(Path file, byte[] bytes, List<String> changed) throws IOException {
        if (Files.isRegularFile(file) && Arrays.equals(Files.readAllBytes(file), bytes)) {
            return;
        }
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, bytes);
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        changed.add(file.getFileName().toString());
    }

    /** True unless the config explicitly turns custom content off; missing config counts as on (Geyser default). */
    static boolean customContentEnabled(Path config) {
        if (!Files.isRegularFile(config)) {
            return true;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config.toFile());
        if (yaml.contains("gameplay.enable-custom-content")) {
            return yaml.getBoolean("gameplay.enable-custom-content");
        }
        return yaml.getBoolean("enable-custom-content", true);
    }
}
