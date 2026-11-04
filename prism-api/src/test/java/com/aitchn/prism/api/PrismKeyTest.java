package com.aitchn.prism.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrismKeyTest {

    @Test
    void parsesNamespaceAndValue() {
        PrismKey key = PrismKey.parse("my-mod:tools/ruby_sword");
        assertEquals("my-mod", key.namespace());
        assertEquals("tools/ruby_sword", key.value());
        assertEquals("my-mod:tools/ruby_sword", key.toString());
    }

    @Test
    void bareValueUsesDefaultNamespace() {
        assertEquals(new PrismKey("mod", "ruby"), PrismKey.parse("ruby", "mod"));
    }

    @Test
    void rejectsMalformedKeys() {
        assertThrows(IllegalArgumentException.class, () -> PrismKey.parse("nocolon"));
        assertThrows(IllegalArgumentException.class, () -> PrismKey.parse("Upper:case"));
        assertThrows(IllegalArgumentException.class, () -> PrismKey.parse("ns:has space"));
        assertThrows(IllegalArgumentException.class, () -> PrismKey.parse(":empty"));
        assertThrows(IllegalArgumentException.class, () -> PrismKey.parse("a:b:c"));
    }

    @Test
    void reservedNamespaces() {
        assertTrue(PrismKey.parse("prism:x").isReserved());
        assertTrue(PrismKey.parse("minecraft:x").isReserved());
        assertFalse(PrismKey.parse("mod:x").isReserved());
    }

    @Test
    void ordersByNamespaceThenValue() {
        assertTrue(PrismKey.parse("a:z").compareTo(PrismKey.parse("b:a")) < 0);
        assertTrue(PrismKey.parse("a:a").compareTo(PrismKey.parse("a:b")) < 0);
    }
}
