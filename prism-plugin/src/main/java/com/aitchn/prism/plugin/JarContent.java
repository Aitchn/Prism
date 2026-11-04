package com.aitchn.prism.plugin;

import com.aitchn.prism.core.content.ContentSource;
import com.aitchn.prism.core.content.PathContentSource;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.plugin.Plugin;

/** Opens a mod jar read-only for the duration of one activation. Nothing is extracted anywhere. */
final class JarContent implements AutoCloseable {

    private final FileSystem fileSystem;
    private final ContentSource source;

    private JarContent(FileSystem fileSystem, ContentSource source) {
        this.fileSystem = fileSystem;
        this.source = source;
    }

    static JarContent open(Plugin plugin) throws IOException {
        Path location;
        try {
            location = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | RuntimeException e) {
            throw new IOException("Cannot locate the jar of " + plugin.getName(), e);
        }
        if (Files.isDirectory(location)) {
            // Development setups that run a mod from a classes directory.
            return new JarContent(null, new PathContentSource(location));
        }
        FileSystem fs = FileSystems.newFileSystem(location);
        return new JarContent(fs, new PathContentSource(fs.getPath("/")));
    }

    ContentSource source() {
        return source;
    }

    @Override
    public void close() throws IOException {
        if (fileSystem != null) {
            fileSystem.close();
        }
    }
}
