package com.aitchn.prism.pack;

import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.core.block.Appearances;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the step icons of a {@code prism:gauge} item: 16x16 PNGs, the same bytes for the same input, so the packs stay
 * deterministic. A texture the mod already ships at a step's path wins.
 */
public final class GaugeIcons {

    static final int SIZE = 16;
    private static final int FRAME = 0xFF2B2B2B;
    private static final int EMPTY = 0xFF5A5A5A;

    private GaugeIcons() {}

    /** Adds the missing step textures of {@code item}, if it is a gauge; returns how many steps it has (0 if none). */
    public static int addTextures(@NotNull Map<String, byte[]> files, @NotNull ItemDefinition item) {
        var gauge = item.components().get(PrismComponents.GAUGE);
        if (gauge.isEmpty()) {
            return 0;
        }
        for (int step = 0; step <= gauge.get().steps(); step++) {
            var model = Appearances.gaugeModel(item.key(), step);
            String path = "assets/" + model.namespace() + "/textures/" + model.value() + ".png";
            if (!files.containsKey(path)) {
                files.put(path, png(gauge.get(), step));
            }
        }
        return gauge.get().steps();
    }

    /** The icon of {@code step} (0 empty to {@code steps} full). */
    public static byte @NotNull [] png(@NotNull PrismComponents.Gauge gauge, int step) {
        return encode(pixels(gauge, step));
    }

    /** ARGB pixels, row by row from the top. */
    static int[] pixels(PrismComponents.Gauge gauge, int step) {
        int[] argb = new int[SIZE * SIZE];
        int fill = 0xFF000000 | gauge.color();
        int light = 0xFF000000 | lighter(gauge.color());
        if (gauge.style().equals("arrow")) {
            // An arrow pointing right: a shaft (x 1-10) and a head (x 11-15) with a dark outline, filled from the left.
            int filled = scaled(step, gauge.steps(), 13) + 1; // inside columns 2..14
            for (int x = 1; x < SIZE; x++) {
                int half = x <= 10 ? 3 : 6 - (x - 11); // shaft rows 5-10; the head narrows from rows 2-13 to 6-9
                int top = 8 - half;
                int bottom = 7 + half;
                for (int y = top; y <= bottom; y++) {
                    boolean edge = y == top || y == bottom || x == 1 || x == SIZE - 1
                            || (x == 11 && (y < 5 || y > 10));
                    argb[y * SIZE + x] = edge ? FRAME : x <= filled ? (y == top + 1 ? light : fill) : EMPTY;
                }
            }
            return argb;
        }
        // A vertical bar (x 4-11): a frame around an inside 6 wide and 14 high, filled from the bottom.
        for (int y = 0; y < SIZE; y++) {
            for (int x = 4; x <= 11; x++) {
                argb[y * SIZE + x] = FRAME;
            }
        }
        int height = scaled(step, gauge.steps(), 14);
        for (int y = 1; y <= 14; y++) {
            boolean filledRow = y > 14 - height;
            for (int x = 5; x <= 10; x++) {
                argb[y * SIZE + x] = !filledRow ? EMPTY : (x == 5 || y == 15 - height) ? light : fill;
            }
        }
        return argb;
    }

    /** {@code step} of {@code steps} in pixels out of {@code max}; only the ends are empty or full. */
    static int scaled(int step, int steps, int max) {
        if (step <= 0) {
            return 0;
        }
        if (step >= steps) {
            return max;
        }
        return Math.max(1, Math.min(max - 1, Math.round((float) max * step / steps)));
    }

    private static int lighter(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return ((r + (255 - r) / 3) << 16) | ((g + (255 - g) / 3) << 8) | (b + (255 - b) / 3);
    }

    /** A minimal RGBA PNG encoder (no filtering), deterministic for the same pixels. */
    static byte[] encode(int[] argb) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < SIZE; y++) {
            raw.write(0); // filter: none
            for (int x = 0; x < SIZE; x++) {
                int p = argb[y * SIZE + x];
                raw.write((p >> 16) & 0xFF);
                raw.write((p >> 8) & 0xFF);
                raw.write(p & 0xFF);
                raw.write((p >>> 24) & 0xFF);
            }
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw.toByteArray());
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteBuffer header = ByteBuffer.allocate(13);
        header.putInt(SIZE).putInt(SIZE).put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0);
        chunk(png, "IHDR", header.array());
        chunk(png, "IDAT", compressed.toByteArray());
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(name);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
