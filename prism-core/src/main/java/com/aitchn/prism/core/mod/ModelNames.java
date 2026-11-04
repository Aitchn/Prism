package com.aitchn.prism.core.mod;

import com.aitchn.prism.api.PrismKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jetbrains.annotations.NotNull;

/** Names Prism model keys for engines that identify models by file name. */
public final class ModelNames {

    private ModelNames() {}

    /**
     * {@code prism_<readable>_<hash>}: the readable part is only for humans (non-alphanumerics become '_'), the hash
     * of the exact key text keeps distinct keys such as {@code a:guns/rifle} and {@code a:guns_rifle} apart.
     */
    public static @NotNull String betterModelName(@NotNull PrismKey key) {
        String readable = (key.namespace() + "_" + key.value()).replaceAll("[^a-z0-9]", "_");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.toString().getBytes(StandardCharsets.UTF_8));
            return "prism_" + readable + "_" + HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
