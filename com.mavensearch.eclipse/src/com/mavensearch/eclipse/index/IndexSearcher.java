package com.mavensearch.eclipse.index;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mavensearch.eclipse.model.Artifact;

/**
 * 本地搜索：关键词按空白/连字符切分、小写化、AND 语义。
 * 索引行序即流行度降序，顺序扫描先命中者更流行——凑满 limit 即止，无需排序。
 */
public final class IndexSearcher {

    private static final int SEPARATOR = '\n';

    private final IndexStore store;

    public IndexSearcher(IndexStore store) {
        this.store = store;
    }

    public List<Artifact> search(String query, int limit) {
        List<Artifact> out = new ArrayList<>();
        byte[][] tokens = tokenize(query);
        if (tokens.length == 0 || limit <= 0) {
            return out;
        }
        byte[] names = store.names();
        int[] offsets = store.offsets();
        for (int i = 0; i < store.count() && out.size() < limit; i++) {
            int from = offsets[i];
            int to = offsets[i + 1] - 1; // 不含结尾 '\n'
            if (matchesAll(names, from, to, tokens)) {
                String name = store.name(i);
                int colon = name.indexOf(':');
                out.add(new Artifact(name.substring(0, colon), name.substring(colon + 1), store.usage(i)));
            }
        }
        return out;
    }

    private static byte[][] tokenize(String query) {
        String[] parts = query.toLowerCase(Locale.ROOT).trim().split("[\\s-]+");
        List<byte[]> tokens = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) {
                tokens.add(p.getBytes(StandardCharsets.UTF_8));
            }
        }
        return tokens.toArray(new byte[0][]);
    }

    private static boolean matchesAll(byte[] names, int from, int to, byte[][] tokens) {
        for (byte[] t : tokens) {
            if (indexOf(names, from, to, t) < 0) {
                return false;
            }
        }
        return true;
    }

    /** 在 [from,to) 内找 needle 首个出现位置，找不到返回 -1。朴素实现即可（名字很短）。 */
    static int indexOf(byte[] hay, int from, int to, byte[] needle) {
        int last = to - needle.length;
        for (int i = from; i <= last; i++) {
            int j = 0;
            while (j < needle.length && hay[i + j] == needle[j]) {
                j++;
            }
            if (j == needle.length) {
                return i;
            }
        }
        return -1;
    }
}
