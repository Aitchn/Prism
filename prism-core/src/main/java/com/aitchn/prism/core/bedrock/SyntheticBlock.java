package com.aitchn.prism.core.bedrock;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything Geyser needs to register one look as a real Bedrock custom block: the synthetic Java state id it is
 * reached by, the block's geometry and textures (named in the Bedrock pack), its collision, outline and hardness.
 * Plain data, built by the Bedrock pack builder and consumed either by the in-process Geyser registration or, as JSON,
 * by a Geyser extension on a proxy (ADR-0024).
 *
 * @param lookId           {@code Appearances.Look#id()}
 * @param javaId           the synthetic Java block state id
 * @param name             the Bedrock block name (the identifier is {@code geyser_custom:<name>}), unique per boot
 * @param displayName      the name Bedrock shows
 * @param geometry         a geometry identifier from the pack, or null for the default cube
 * @param materials        material instances by face key ({@code *}, {@code up}, ...)
 * @param collision        the boxes the Java server enforces (a full cube, or none)
 * @param selection        the boxes whose bounds the player aims at (empty: the full cube)
 * @param rotationX        rotation in degrees about x (a multiple of 90)
 * @param rotationY        rotation in degrees about y (a multiple of 90)
 * @param destroyTime      vanilla hardness (see {@link BedrockHardness})
 * @param canBreakWithHand whether no tool is needed to break it
 */
public record SyntheticBlock(
        @NotNull String lookId,
        int javaId,
        @NotNull String name,
        @NotNull String displayName,
        @Nullable String geometry,
        @NotNull Map<String, Material> materials,
        @NotNull List<BedrockShapes.Box> collision,
        @NotNull List<BedrockShapes.Box> selection,
        int rotationX,
        int rotationY,
        float destroyTime,
        boolean canBreakWithHand) {

    /** The Java piston behaviour of every custom block: they are not pushed (Prism cancels piston movement). */
    public static final String PISTON_BEHAVIOR = "IMMOVEABLE";

    /** One material instance: a texture name from the pack's terrain atlas and how it is drawn. */
    public record Material(@NotNull String texture, @NotNull String renderMethod) {}

    /** The Java state identifier Geyser registers for this block ({@code prism:<name>}, unique, no properties). */
    public @NotNull String javaIdentifier() {
        return "prism:" + name;
    }

    /** Whether Bedrock players walk through the block. */
    public boolean passable() {
        return collision.isEmpty();
    }

    /**
     * The Bedrock block name of a look: {@code p_<readable part>_<synthetic id>}. The id makes it unique, the readable
     * part is only for people looking at a block in a debug screen. Lower case letters, digits and underscores only, and
     * never a leading digit.
     */
    public static @NotNull String nameOf(@NotNull String lookId, int javaId) {
        StringBuilder readable = new StringBuilder();
        for (char c : lookId.toLowerCase(Locale.ROOT).toCharArray()) {
            readable.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' ? c : '_');
            if (readable.length() >= 32) {
                break;
            }
        }
        return "p_" + readable + "_" + javaId;
    }
}
