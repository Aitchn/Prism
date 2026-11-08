package com.aitchn.prism.plugin;

import com.aitchn.prism.bedrock.GeyserBlockRegistrar;
import com.aitchn.prism.client.ClientStates;
import com.aitchn.prism.core.bedrock.SyntheticBlock;
import com.aitchn.prism.core.bedrock.SyntheticIds;
import com.aitchn.prism.pack.BedrockPackBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Synthetic block states for Geyser viewers (ADR-0024): one Java state id above the vanilla range per look, registered
 * with a Geyser that runs inside this server so that every look is a real Bedrock custom block, whatever their number.
 *
 * <p>Two things are kept apart. The <em>plan</em> is the id of every look in this boot, always computed (the pack build
 * and the self test use it). The <em>active</em> ids are the ones viewers are actually sent, and exist only once Geyser
 * has accepted them (or, in {@code proxy} mode, once the server has loaded and the export is final). They never change
 * afterwards: ids are positional, so a look added later would shift every id after it, and Geyser has already read the
 * old ones. Such a look is shown to Bedrock players as its Java look until the next restart.
 */
final class BedrockBlocks {

    enum Mode {
        /** Register through the Geyser API when Geyser runs here, otherwise leave Bedrock as it was. */
        AUTO,
        /** Geyser runs on a proxy: freeze the ids at server load and export them for a Prism Geyser extension. */
        PROXY,
        /** Bedrock as before this stage: Geyser's JSON mappings and the note block and tripwire states. */
        OFF;

        static Mode parse(String value, Logger log) {
            String name = value == null ? "auto" : value.trim().toLowerCase(Locale.ROOT);
            return switch (name) {
                case "auto", "" -> AUTO;
                case "proxy" -> PROXY;
                case "off", "false" -> OFF;
                default -> {
                    log.warning("bedrock.synthetic-blocks: unknown value '" + value + "', using auto");
                    yield AUTO;
                }
            };
        }
    }

    /** The ids viewers are sent, and the global palette width Geyser reads once they are registered. */
    record Registered(Map<String, Integer> ids, int globalBits) {

        static final Registered NONE = new Registered(Map.of(), 0);
    }

    private final Logger log;
    private final Mode mode;
    private final int configuredBase;
    private final Path folder;
    private final Supplier<BedrockPackBuilder.Result> build;
    private final Runnable activated;
    private final Runnable fallback;
    private final Supplier<GeyserInstaller> installer;
    private volatile SyntheticIds.Plan planned = SyntheticIds.Plan.empty(0);
    private volatile Registered registered = Registered.NONE;
    private volatile boolean apiRoute;
    private volatile boolean requested;
    private GeyserBlockRegistrar.Content frozen;
    private String frozenExport = "";

    /**
     * @param folder    Prism's own folder for files meant for a Geyser elsewhere ({@code plugins/Prism/geyser})
     * @param build     the current Bedrock pack build (cached by its owner), made with {@link #plannedForBuild()}
     * @param activated called once ids became active, so the looks viewers get are rebuilt
     * @param fallback  called when registration failed, so the legacy Geyser files are written after all
     */
    BedrockBlocks(Logger log, Mode mode, int configuredBase, Path folder, Supplier<BedrockPackBuilder.Result> build,
            Runnable activated, Runnable fallback, Supplier<GeyserInstaller> installer) {
        this.log = log;
        this.mode = mode;
        this.configuredBase = configuredBase;
        this.folder = folder;
        this.build = build;
        this.activated = activated;
        this.fallback = fallback;
        this.installer = installer;
    }

    Mode mode() {
        return mode;
    }

    // ---------------------------------------------------------------- the plan

    /** Allocates an id to every look of every block (any backend) in look id order. */
    SyntheticIds.Plan plan(Collection<String> lookIds) {
        try {
            int base = configuredBase > 0 ? configuredBase : SyntheticIds.defaultBase(ClientStates.stateCount());
            planned = SyntheticIds.plan(lookIds, base);
        } catch (RuntimeException e) {
            planned = SyntheticIds.Plan.empty(0);
            log.severe("PRISM_BEDROCK_SYNTHETIC_FAIL cannot allocate synthetic block states: " + e);
        }
        return planned;
    }

    SyntheticIds.Plan planned() {
        return planned;
    }

    /** The plan the Bedrock pack build is given: none when synthetic blocks are off. */
    SyntheticIds.Plan plannedForBuild() {
        return mode == Mode.OFF ? SyntheticIds.Plan.empty(0) : planned;
    }

    // ---------------------------------------------------------------- what viewers are sent

    /** Whether Geyser viewers are sent synthetic ids at all. */
    boolean bedrockStates() {
        return !registered.ids().isEmpty();
    }

    /** Look id to the synthetic id registered for it. */
    Map<String, Integer> activeIds() {
        return registered.ids();
    }

    int globalBits() {
        return registered.globalBits();
    }

    /** Whether Geyser got the blocks through its API, so its own folder must not also hold the legacy files. */
    boolean apiRoute() {
        return apiRoute;
    }

    // ---------------------------------------------------------------- registration

    /** Subscribes to Geyser (mode {@code auto} and Geyser-Spigot present); says what will happen otherwise. */
    void attach() {
        switch (mode) {
            case OFF -> log.info("Bedrock synthetic blocks are off (bedrock.synthetic-blocks: off); Bedrock players "
                    + "see the note block and tripwire looks of Geyser's JSON mappings.");
            case PROXY -> log.info("Bedrock synthetic blocks: proxy mode. Prism freezes the ids when the server has "
                    + "loaded and writes " + folder.resolve(GeyserInstaller.SYNTHETIC_FILE)
                    + " for a Prism Geyser extension on the proxy (docs/en/bedrock.md).");
            case AUTO -> {
                if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") == null) {
                    log.info("Geyser is not installed on this server, so Bedrock synthetic blocks are not registered. "
                            + "If Geyser runs on your proxy, set bedrock.synthetic-blocks: proxy.");
                    return;
                }
                try {
                    GeyserBlockRegistrar.install(this, new Hook());
                    apiRoute = true;
                } catch (RuntimeException | LinkageError e) {
                    log.warning("PRISM_BEDROCK_SYNTHETIC_FAIL cannot subscribe to the Geyser API (" + e
                            + "); Bedrock players keep the legacy note block looks. Is this a Geyser build with "
                            + "custom block support?");
                }
            }
        }
    }

    /** The server finished loading: Geyser has started by now if it runs here, and a proxy export can be final. */
    void serverLoaded() {
        if (mode == Mode.PROXY) {
            try {
                register(content());
            } catch (RuntimeException e) {
                failed(e);
            }
        } else if (mode == Mode.AUTO && apiRoute) {
            // Geyser asks for custom blocks only with custom content enabled; say so if nobody asked.
            CompletableFuture.runAsync(() -> {
                if (!requested && !bedrockStates()) {
                    log.warning("PRISM_BEDROCK_SYNTHETIC_FAIL Geyser did not ask for custom blocks within 30 seconds. "
                            + "Is Geyser's custom content enabled (gameplay.enable-custom-content), and did Geyser "
                            + "start? Bedrock players keep the legacy note block looks.");
                }
            }, CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS));
        }
    }

    /** Builds, once per boot, what is registered and exported. */
    private synchronized GeyserBlockRegistrar.Content content() {
        if (frozen == null) {
            requested = true;
            BedrockPackBuilder.Result result = build.get();
            Path pack = null;
            if (mode == Mode.AUTO && !result.syntheticBlocks().isEmpty()) {
                pack = folder.resolve("packs").resolve(GeyserInstaller.PACK_FILE);
                try {
                    Files.createDirectories(pack.getParent());
                    Files.write(pack, result.mcpack());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            frozenExport = result.syntheticExport();
            frozen = new GeyserBlockRegistrar.Content(result.syntheticBlocks(), pack);
        }
        return frozen;
    }

    private void register(GeyserBlockRegistrar.Content content) {
        List<SyntheticBlock> blocks = content.blocks();
        if (blocks.isEmpty()) {
            return;
        }
        Map<String, Integer> ids = new LinkedHashMap<>();
        int highest = 0;
        for (SyntheticBlock block : blocks) {
            ids.put(block.lookId(), block.javaId());
            highest = Math.max(highest, block.javaId());
        }
        if (registered.ids().equals(ids)) {
            return;
        }
        registered = new Registered(Map.copyOf(ids), SyntheticIds.bitsFor(highest + 1));
        GeyserInstaller target = installer.get();
        if (target != null) {
            target.exportSynthetic(frozenExport);
        }
        log.info("PRISM_BEDROCK_SYNTHETIC_OK mode=" + mode.name().toLowerCase(Locale.ROOT) + " blocks=" + ids.size()
                + " base=" + planned.base() + " global_bits=" + registered.globalBits());
        try {
            activated.run();
        } catch (RuntimeException e) {
            log.severe("PRISM_BEDROCK_SYNTHETIC_FAIL the looks could not be refreshed: " + e);
        }
    }

    private void failed(Throwable cause) {
        log.warning("PRISM_BEDROCK_SYNTHETIC_FAIL " + cause + "; Bedrock players keep the legacy note block looks, "
                + "and the legacy Geyser files are written for the next Geyser start.");
        apiRoute = false;
        fallback.run();
    }

    /** What Geyser's events are answered with. Called on Geyser's start-up thread. */
    private final class Hook implements GeyserBlockRegistrar.Source {

        @Override
        public GeyserBlockRegistrar.Content content() {
            return BedrockBlocks.this.content();
        }

        @Override
        public void registered(GeyserBlockRegistrar.Content content) {
            BedrockBlocks.this.register(content);
        }

        @Override
        public void failed(Throwable cause) {
            BedrockBlocks.this.failed(cause);
        }
    }
}
