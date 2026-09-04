package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.service.TtlLruCache;

class TtlLruCacheTest {

    @Test
    void putGetAndTtlExpiry() {
        AtomicLong now = new AtomicLong(1000);
        TtlLruCache<String, Integer> c = new TtlLruCache<>(10, 60_000, now::get);
        c.put("k", 1);
        assertEquals(1, c.get("k"));
        now.set(1000 + 60_001); // 过期
        assertNull(c.get("k"));
    }

    @Test
    void evictsLeastRecentlyUsed() {
        AtomicLong now = new AtomicLong(0);
        TtlLruCache<String, Integer> c = new TtlLruCache<>(2, 60_000, now::get);
        c.put("a", 1);
        c.put("b", 2);
        c.get("a");      // a 变为最近使用
        c.put("c", 3);   // 逐出 b
        assertNull(c.get("b"));
        assertEquals(1, c.get("a"));
        assertEquals(3, c.get("c"));
    }

    @Test
    void putRefreshesTtl() {
        AtomicLong now = new AtomicLong(0);
        TtlLruCache<String, Integer> c = new TtlLruCache<>(10, 1000, now::get);
        c.put("k", 1);
        now.set(900);
        c.put("k", 2);           // 重置过期时间
        now.set(900 + 1000);
        assertEquals(2, c.get("k"));
    }
}
