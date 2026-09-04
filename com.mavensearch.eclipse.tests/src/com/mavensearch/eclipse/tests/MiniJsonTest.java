package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.json.MiniJson;

class MiniJsonTest {

    @Test
    void parsesObjectWithAllTypes() {
        Map<String, Object> m = MiniJson.obj(
                "{\"id\":\"com.alibaba:fastjson\",\"g\":\"com.alibaba\",\"vc\":349,\"n\":null,\"ok\":true}");
        assertEquals("com.alibaba", MiniJson.str(m, "g"));
        assertEquals(349.0, MiniJson.num(m, "vc"));
        assertEquals(null, MiniJson.str(m, "n"));
        assertEquals(null, MiniJson.str(m, "missing"));
        assertEquals(true, m.get("ok"));
    }

    @Test
    void parsesNestedArraysAndObjects() {
        Map<String, Object> m = MiniJson.obj("{\"docs\":[{\"g\":\"a\",\"a\":\"b\"},{\"g\":\"c\"}],\"numFound\":170}");
        List<Object> docs = (List<Object>) m.get("docs");
        assertEquals(2, docs.size());
        assertEquals(170.0, MiniJson.num(m, "numFound"));
        assertEquals("b", MiniJson.str((Map<String, Object>) docs.get(0), "a"));
    }

    @Test
    void handlesEscapesAndUnicode() {
        Map<String, Object> m = MiniJson.obj("{\"s\":\"a\\\"b\\\\c\\u0041\",\"t\":\"\\n\"}");
        assertEquals("a\"b\\cA", MiniJson.str(m, "s"));
        assertEquals("\n", MiniJson.str(m, "t"));
    }

    @Test
    void topLevelArrayAndScalar() {
        assertEquals(3, MiniJson.arr("[1,2,3]").size());
        assertEquals("x", MiniJson.parse("\"x\""));
    }

    @Test
    void rejectsTruncatedInput() {
        assertThrows(IllegalArgumentException.class, () -> MiniJson.obj("{\"g\":\"a\""));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.obj("{\"g\"}"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.obj("not json"));
    }

    @Test
    void toleratesWhitespace() {
        Map<String, Object> m = MiniJson.obj("  { \"g\" : \"a\" , \"n\" : 1 } ");
        assertEquals("a", MiniJson.str(m, "g"));
    }
}
