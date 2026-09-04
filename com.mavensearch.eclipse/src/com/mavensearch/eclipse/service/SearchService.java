package com.mavensearch.eclipse.service;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import com.mavensearch.eclipse.client.MavenCentralClient;
import com.mavensearch.eclipse.index.IndexSearcher;
import com.mavensearch.eclipse.model.Artifact;

/**
 * 搜索编排。索引就绪 → 完全本地；未就绪 → solrsearch 在线兜底。
 * 完整 GA 输入是特殊路径：索引查得到给真实 usage；查不到也返回单条 UNKNOWN
 * （版本展开走 maven-metadata.xml，对任意新包 100% 有效）。
 */
public final class SearchService {

    /** "groupId:artifactId"（Maven 坐标字符集）。 */
    private static final Pattern GA = Pattern.compile("^[\\w.]+:[\\w.\\-]+$");

    public record SearchResult(List<Artifact> artifacts, boolean fromIndex) {
    }

    private final java.util.function.Supplier<IndexSearcher> indexSearcher;
    private final MavenCentralClient onlineFallback;

    public SearchService(java.util.function.Supplier<IndexSearcher> indexSearcher,
                         MavenCentralClient onlineFallback) {
        this.indexSearcher = indexSearcher;
        this.onlineFallback = onlineFallback;
    }

    public SearchResult search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return new SearchResult(List.of(), false);
        }
        if (GA.matcher(q).matches()) {
            return exactArtifact(q);
        }
        IndexSearcher s = indexSearcher.get();
        if (s != null) {
            return new SearchResult(s.search(q, limit), true);
        }
        try {
            return new SearchResult(onlineFallback.search(q, limit), false);
        } catch (IOException e) {
            return new SearchResult(List.of(), false);
        }
    }

    private SearchResult exactArtifact(String ga) {
        int colon = ga.indexOf(':');
        String g = ga.substring(0, colon);
        String a = ga.substring(colon + 1);
        IndexSearcher s = indexSearcher.get();
        if (s != null) {
            // 借用一般搜索路径：GA 全串本身是精确子串，若索引有它必然命中
            List<Artifact> hits = s.search(ga, 1);
            for (Artifact hit : hits) {
                if (hit.name().equals(ga)) {
                    return new SearchResult(List.of(hit), true);
                }
            }
        }
        return new SearchResult(List.of(new Artifact(g, a, Artifact.UNKNOWN_USAGE)), false);
    }
}
