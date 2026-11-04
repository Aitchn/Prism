package com.aitchn.prism.core.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.ContentException;
import org.junit.jupiter.api.Test;

class ModManifestTest {

    @Test
    void parsesOptionalOverrides() throws Exception {
        ModManifest manifest = ModManifest.parse("{\"namespace\": \"botany\", \"content\": \"data\", \"prismApi\": \"0.1\"}");
        assertEquals("botany", manifest.namespace().orElseThrow());
        assertEquals("data", manifest.contentPath().orElseThrow());
        assertEquals("0.1", manifest.prismApi().orElseThrow());
    }

    @Test
    void emptyManifestIsAllDefaults() throws Exception {
        assertTrue(ModManifest.parse("{}").namespace().isEmpty());
    }

    @Test
    void rejectsUnknownFields() {
        assertThrows(ContentException.class, () -> ModManifest.parse("{\"nmespace\": \"x\"}"));
    }

    @Test
    void apiVersionCompatibility() {
        ApiVersion provided = ApiVersion.parse("0.3.1");
        assertTrue(provided.satisfies(ApiVersion.parse("0.2")));
        assertTrue(provided.satisfies(ApiVersion.parse("0.3")));
        assertFalse(provided.satisfies(ApiVersion.parse("0.4")));
        assertFalse(provided.satisfies(ApiVersion.parse("1.0")));
    }
}
