package com.aitchn.prism.core.bedrock;

import com.aitchn.prism.core.block.ConnectedShapes;
import com.aitchn.prism.core.look.Collision;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Collision and selection boxes of the Bedrock custom blocks, from the physics class of the look. Geyser predicts the
 * movement of a Bedrock player from the collision boxes registered for a state, so they must be those the Java server
 * enforces: a full cube for a solid carrier, none for a passable one. Boxes are kept in Java block units (0 to 1 on each
 * axis, y may reach 1.5 for fences) and converted to Bedrock's frame (origin and size in pixels, x mirrored, x and z
 * centred) only where Geyser asks, the same way Geyser converts the shapes of vanilla blocks.
 */
public final class BedrockShapes {

    /** One box in Java block units: from and to corners. */
    public record Box(double x1, double y1, double z1, double x2, double y2, double z2) {

        public static final Box UNIT = new Box(0, 0, 0, 1, 1, 1);

        public double middleX() {
            return (x1 + x2) / 2;
        }

        public double middleY() {
            return (y1 + y2) / 2;
        }

        public double middleZ() {
            return (z1 + z2) / 2;
        }

        public double sizeX() {
            return x2 - x1;
        }

        public double sizeY() {
            return y2 - y1;
        }

        public double sizeZ() {
            return z2 - z1;
        }
    }

    /** A box in Bedrock's component frame: origin (x and z centred, x mirrored) and size, in pixels. */
    public record BedrockBox(float originX, float originY, float originZ, float sizeX, float sizeY, float sizeZ) {

        public static final BedrockBox EMPTY = new BedrockBox(0, 0, 0, 0, 0, 0);

        public boolean isEmpty() {
            return sizeX == 0 && sizeY == 0 && sizeZ == 0;
        }
    }

    private BedrockShapes() {}

    /** What the Java server enforces for a look of this collision class: one full cube, or nothing. */
    public static @NotNull List<Box> collision(@NotNull Collision collision) {
        return collision == Collision.FULL ? List.of(Box.UNIT) : List.of();
    }

    /** Converts pixel boxes (0 to 16, as in a Java model) to block units, dropping flat or inverted ones. */
    public static @NotNull List<Box> fromPixels(@NotNull List<ConnectedShapes.Box> boxes) {
        List<Box> result = new ArrayList<>();
        for (ConnectedShapes.Box box : boxes) {
            Box converted = new Box(box.x1() / 16, box.y1() / 16, box.z1() / 16, box.x2() / 16, box.y2() / 16,
                    box.z2() / 16);
            if (converted.sizeX() > 0 && converted.sizeY() > 0 && converted.sizeZ() > 0) {
                result.add(converted);
            }
        }
        return result;
    }

    /** The Bedrock frame of a box: x is mirrored against Java's, x and z are centred on the block. */
    public static @NotNull BedrockBox toBedrock(@NotNull Box box) {
        double minX = clamp(box.x1(), 0, 1);
        double minY = clamp(box.y1(), 0, 1);
        double minZ = clamp(box.z1(), 0, 1);
        double maxX = clamp(box.x2(), 0, 1);
        double maxY = clamp(box.y2(), 0, 1.5);
        double maxZ = clamp(box.z2(), 0, 1);
        return new BedrockBox((float) (16 * (1 - maxX) - 8), (float) (16 * minY), (float) (16 * minZ - 8),
                (float) (16 * (maxX - minX)), (float) (16 * (maxY - minY)), (float) (16 * (maxZ - minZ)));
    }

    /** Every collision box of a class in Bedrock's frame; the empty box for a passable one (Geyser needs one entry). */
    public static @NotNull List<BedrockBox> collisionForBedrock(@NotNull List<Box> boxes) {
        if (boxes.isEmpty()) {
            return List.of(BedrockBox.EMPTY);
        }
        List<BedrockBox> result = new ArrayList<>();
        for (Box box : boxes) {
            result.add(toBedrock(box));
        }
        return result;
    }

    /**
     * The outline a Bedrock player aims at: the bounding box of {@code boxes} (the model's elements), or the full cube
     * when there are none (a block without a model of its own, a cross).
     */
    public static @NotNull BedrockBox selection(@NotNull List<Box> boxes) {
        if (boxes.isEmpty()) {
            return toBedrock(Box.UNIT);
        }
        double x1 = 2;
        double y1 = 2;
        double z1 = 2;
        double x2 = -1;
        double y2 = -1;
        double z2 = -1;
        for (Box box : boxes) {
            x1 = Math.min(x1, box.x1());
            y1 = Math.min(y1, box.y1());
            z1 = Math.min(z1, box.z1());
            x2 = Math.max(x2, box.x2());
            y2 = Math.max(y2, box.y2());
            z2 = Math.max(z2, box.z2());
        }
        return toBedrock(new Box(x1, y1, z1, x2, y2, z2));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
