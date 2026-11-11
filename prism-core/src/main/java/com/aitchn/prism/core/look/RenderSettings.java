package com.aitchn.prism.core.look;

import com.aitchn.prism.api.content.PrismComponents.Render;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The server-wide rendering settings of block looks, read from {@code config.yml} (ADR-0025).
 *
 * @param defaultRender what {@code prism:render: auto} means: {@link Render#DISPLAY} (every look drawn by the display
 *                      layer, the default) or {@link Render#STATE} (a borrowed vanilla state while one is left, as before
 *                      ADR-0025)
 * @param lighting      how the display layer places its entities
 * @param notes         what the server should log about the settings it read (old keys mapped, bad values replaced)
 */
public record RenderSettings(@NotNull Render defaultRender, @NotNull DisplayLighting lighting,
        @NotNull List<String> notes) {

    /** The defaults: every look drawn by the display layer, one entity per block. */
    public static final RenderSettings DEFAULT = new RenderSettings(Render.DISPLAY, DisplayLighting.BLOCK, List.of());

    /** The behaviour before ADR-0025: borrowed states first, faces split. For tests and comparisons. */
    public static final RenderSettings STATES_FIRST = new RenderSettings(Render.STATE, DisplayLighting.FACES, List.of());

    public RenderSettings {
        if (defaultRender == Render.AUTO) {
            throw new IllegalArgumentException("the default render must be STATE or DISPLAY");
        }
        notes = List.copyOf(notes);
    }

    /**
     * Reads the settings.
     *
     * @param backend  {@code blocks.backend}, the key of earlier versions ({@code auto}, {@code native},
     *                 {@code display}); null if absent
     * @param render   {@code blocks.render} ({@code display} or {@code state}); null if absent. Wins over
     *                 {@code backend}
     * @param lighting {@code blocks.display.lighting} ({@code block} or {@code faces}); null if absent
     */
    public static @NotNull RenderSettings read(@Nullable String backend, @Nullable String render,
            @Nullable String lighting) {
        List<String> notes = new ArrayList<>();
        Render chosen = null;
        String renderValue = normalize(render);
        String backendValue = normalize(backend);
        if (renderValue != null) {
            chosen = switch (renderValue) {
                case "display", "auto" -> Render.DISPLAY;
                case "state" -> Render.STATE;
                default -> {
                    notes.add("blocks.render must be display or state, got '" + render + "'; using display");
                    yield Render.DISPLAY;
                }
            };
            if (backendValue != null && !backendValue.equals("auto")) {
                notes.add("blocks.backend (" + backendValue + ") is ignored because blocks.render is set");
            }
        } else if (backendValue != null) {
            chosen = switch (backendValue) {
                case "auto" -> {
                    notes.add("blocks.backend: auto now draws every block look with the display layer (ADR-0025);"
                            + " set blocks.render: state to give looks borrowed vanilla states first, as before");
                    yield Render.DISPLAY;
                }
                case "native" -> {
                    notes.add("blocks.backend: native is read as blocks.render: state (borrowed vanilla states first)");
                    yield Render.STATE;
                }
                case "display" -> {
                    notes.add("blocks.backend: display is read as blocks.render: display: new blocks are drawn by"
                            + " packet-only display entities; blocks placed earlier with server-side display entities"
                            + " keep them");
                    yield Render.DISPLAY;
                }
                default -> {
                    notes.add("blocks.backend '" + backend + "' is unknown; using blocks.render: display");
                    yield Render.DISPLAY;
                }
            };
        }
        DisplayLighting mode = DisplayLighting.BLOCK;
        String lightingValue = normalize(lighting);
        if (lightingValue != null) {
            DisplayLighting parsed = DisplayLighting.parse(lightingValue);
            if (parsed == null) {
                notes.add("blocks.display.lighting must be block or faces, got '" + lighting + "'; using block");
            } else {
                mode = parsed;
            }
        }
        return new RenderSettings(chosen == null ? Render.DISPLAY : chosen, mode, notes);
    }

    /** The render a block with preference {@code wanted} gets: its own, or the default for {@code auto}. */
    public @NotNull Render resolve(@NotNull Render wanted) {
        return wanted == Render.AUTO ? defaultRender : wanted;
    }

    private static @Nullable String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
