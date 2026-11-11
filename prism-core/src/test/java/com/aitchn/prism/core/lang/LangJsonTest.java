package com.aitchn.prism.core.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LangJsonTest {

    @Test
    void flatAndNested() {
        Map<String, String> parsed = LangJson.parse("""
                ﻿{
                  "item.demo.ruby": "<red>Ruby",
                  "block": {"demo": {"ore": "Ruby Ore"}},
                  "escaped": "a \\"b\\" \\\\ \\u7d05 \\n"
                }
                """);
        assertEquals(Map.of("item.demo.ruby", "<red>Ruby", "block.demo.ore", "Ruby Ore", "escaped", "a \"b\" \\ 紅 \n"),
                parsed);
        assertEquals(List.of("item.demo.ruby", "block.demo.ore", "escaped"), List.copyOf(parsed.keySet()));
        assertTrue(LangJson.parse("{}").isEmpty());
    }

    @Test
    void mistakesAreReportedWithTheLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LangJson.parse("{\n \"a\": \"x\",\n \"b\": 3\n}"));
        assertTrue(e.getMessage().contains("line 3"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> LangJson.parse("{\"a\": \"x\", \"a\": \"y\"}"));
        assertThrows(IllegalArgumentException.class, () -> LangJson.parse("{\"a\": \"x\"} extra"));
        assertThrows(IllegalArgumentException.class, () -> LangJson.parse("{\"a\": [\"x\"]}"));
        assertThrows(IllegalArgumentException.class, () -> LangJson.parse("{\"a\": \"x\""));
        assertThrows(IllegalArgumentException.class, () -> LangJson.parse("{\"a\" \"x\"}"));
        assertThrows(IllegalArgumentException.class, () -> LangJson.parse("[]"));
    }
}
