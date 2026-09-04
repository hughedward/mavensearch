package com.mavensearch.eclipse.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** TTL + LRU 双策略缓存（同步）。时钟可注入，便于测试。 */
public final class TtlLruCache<K, V> {

    private final long ttlMillis;
    private final LongSupplier clock;
    private final LinkedHashMap<K, Entry<V>> map;

    private record Entry<V>(V value, long expiresAt) {
    }

    public TtlLruCache(int capacity, long ttlMillis, LongSupplier clock) {
        this.ttlMillis = ttlMillis;
        this.clock = clock;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            // 勘误（Task 7 实现）：匿名子类继承了 Map.Entry 成员类型，遮蔽外层 Entry record，
            // 简名 Entry 无法以单参数使用（ECJ/JLS 6.4.1 继承成员类型遮蔽外围类成员）——需全限定名。
            protected boolean removeEldestEntry(Map.Entry<K, TtlLruCache.Entry<V>> eldest) {
                return size() > capacity;
            }
        };
    }

    public synchronized V get(K key) {
        Entry<V> e = map.get(key);
        if (e == null) {
            return null;
        }
        if (clock.getAsLong() > e.expiresAt()) { // 恰好等于 expiresAt 时仍可读（putRefreshesTtl 契约）
            map.remove(key);
            return null;
        }
        return e.value();
    }

    public synchronized void put(K key, V value) {
        map.put(key, new Entry<>(value, clock.getAsLong() + ttlMillis));
    }
}
