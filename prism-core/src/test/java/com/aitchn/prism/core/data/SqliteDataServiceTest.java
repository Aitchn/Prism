package com.aitchn.prism.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteDataServiceTest {

    @Test
    void putGetOverwriteRemove() throws Exception {
        try (var service = new SqliteDataService(null)) {
            var store = service.store("mod");
            assertTrue(store.get("a").isEmpty());
            store.put("a", "1");
            store.put("a", "2");
            assertEquals("2", store.get("a").orElseThrow());
            assertTrue(store.remove("a"));
            assertFalse(store.remove("a"));
        }
    }

    @Test
    void namespacesAreIsolatedAndPrefixIsLiteral() throws Exception {
        try (var service = new SqliteDataService(null)) {
            var one = service.store("one");
            var two = service.store("two");
            one.put("net/1", "x");
            one.put("net/2", "y");
            one.put("neta", "z");
            one.put("50%_x", "p");
            one.put("50abc", "q");
            two.put("net/1", "other");
            assertEquals(List.of("net/1", "net/2"), one.keys("net/"));
            assertEquals(List.of("50%_x"), one.keys("50%_"));
            assertEquals(5, one.keys("").size());
            assertEquals("other", two.get("net/1").orElseThrow());
        }
    }

    @Test
    void prefixWithSupplementaryCharacters() throws Exception {
        try (var service = new SqliteDataService(null)) {
            var store = service.store("mod");
            store.put("\uD83D\uDE00/one", "1");
            store.put("\uD83D\uDE01/two", "2");
            assertEquals(List.of("\uD83D\uDE00/one"), store.keys("\uD83D\uDE00/"));
        }
    }

    @Test
    void survivesReopen(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("data.db");
        try (var service = new SqliteDataService(file)) {
            service.store("mod").put("k", "value with ünïcode");
        }
        try (var service = new SqliteDataService(file)) {
            assertEquals("value with ünïcode", service.store("mod").get("k").orElseThrow());
        }
    }

    @Test
    void rejectsBadNamespace() throws Exception {
        try (var service = new SqliteDataService(null)) {
            assertThrows(IllegalArgumentException.class, () -> service.store("Bad Name"));
        }
    }
}
