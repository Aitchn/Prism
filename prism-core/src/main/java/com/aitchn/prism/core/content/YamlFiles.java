package com.aitchn.prism.core.content;

import com.aitchn.prism.api.content.ContentException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** The only place that touches SnakeYAML (provided by the server at runtime). JSON is valid input too. */
public final class YamlFiles {

    private YamlFiles() {}

    /** Parses {@code text} with a safe constructor (no arbitrary object instantiation). */
    public static @Nullable Object parse(@NotNull String path, @NotNull String text) throws ContentException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(10);
        try {
            return new Yaml(new SafeConstructor(options)).load(text);
        } catch (YAMLException e) {
            throw new ContentException(path, "invalid YAML: " + firstLine(e.getMessage()), e);
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
