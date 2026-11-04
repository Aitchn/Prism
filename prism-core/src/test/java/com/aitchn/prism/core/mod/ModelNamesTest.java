package com.aitchn.prism.core.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import org.junit.jupiter.api.Test;

class ModelNamesTest {

    @Test
    void distinctKeysNeverShareAName() {
        assertNotEquals(ModelNames.betterModelName(PrismKey.parse("a:guns/rifle")),
                ModelNames.betterModelName(PrismKey.parse("a:guns_rifle")));
        assertNotEquals(ModelNames.betterModelName(PrismKey.parse("a_b:c")),
                ModelNames.betterModelName(PrismKey.parse("a:b_c")));
    }

    @Test
    void nameIsStableAndFileSafe() {
        String name = ModelNames.betterModelName(PrismKey.parse("my-mod:tools/ruby.gun"));
        assertEquals(name, ModelNames.betterModelName(PrismKey.parse("my-mod:tools/ruby.gun")));
        assertTrue(name.matches("prism_[a-z0-9_]+_[0-9a-f]{8}"), name);
    }
}
