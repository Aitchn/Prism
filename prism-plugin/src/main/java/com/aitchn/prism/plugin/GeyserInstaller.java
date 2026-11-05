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

    private final Logger log;
    private final Path fallbackFolder;
    private boolean warned;

    /** {@code fallbackFolder} receives the files when no Geyser plugin runs here (for a proxy-hosted Geyser). */
    GeyserInstaller(Logger log, Path fallbackFolder) {
        this.log = log;
        this.fallbackFolder = fallbackFolder;
    }

    synchronized void install(byte[] mcpack, String itemMappings, String blockMappings) {
        Plugin geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        Path root = geyser != null ? geyser.getDataFolder().toPath() : fallbackFolder;
        List<String> changed = new ArrayList<>();
        try {
            write(root.resolve("custom_mappings").resolve(ITEMS_FILE),
                    itemMappings.getBytes(StandardCharsets.UTF_8), changed);
            write(root.resolve("custom_mappings").resolve(BLOCKS_FILE),
                    blockMappings.getBytes(StandardCharsets.UTF_8), changed);
            write(root.resolve("packs").resolve(PACK_FILE), mcpack, changed);
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
