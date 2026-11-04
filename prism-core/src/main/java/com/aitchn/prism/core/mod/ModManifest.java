package com.aitchn.prism.core.mod;

import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.core.content.MapConfigSection;
import com.aitchn.prism.core.content.YamlFiles;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Optional {@code prism-mod.json} inside a mod jar. Nothing requires it: the namespace defaults to the lower-cased
 * plugin name and the content path to {@code prism}. It only overrides those and may state the Prism API it needs.
 */
public record ModManifest(Optional<String> namespace, Optional<String> contentPath, Optional<String> prismApi) {

    public static final String FILE_NAME = "prism-mod.json";
    public static final ModManifest NONE = new ModManifest(Optional.empty(), Optional.empty(), Optional.empty());

    public static @NotNull ModManifest parse(@NotNull String text) throws ContentException {
        ConfigSection section = MapConfigSection.of(FILE_NAME, YamlFiles.parse(FILE_NAME, text));
        section.rejectUnknownKeys(Set.of("namespace", "content", "prismApi"));
        return new ModManifest(section.getString("namespace"), section.getString("content"), section.getString("prismApi"));
    }
}
