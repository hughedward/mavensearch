package com.mavensearch.eclipse.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mavensearch.eclipse.client.DepsDevClient;
import com.mavensearch.eclipse.client.MavenMetadataClient;
import com.mavensearch.eclipse.client.VersionComparator;
import com.mavensearch.eclipse.model.ArtifactVersion;

/** 版本列表 + 版本级使用量/日期的编排层。默认展示最近 30 个版本，展开「显示全部」加载余下。 */
public final class VersionService {

    public static final int DEFAULT_PAGE_SIZE = 30;

    public record VersionPage(List<ArtifactVersion> versions, int totalVersions, boolean truncated) {
    }

    private final MavenMetadataClient metadata;
    private final DepsDevClient depsDev;
    private final ExecutorService pool =
            Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "mavensearch-enrich");
                t.setDaemon(true);
                return t;
            });

    private final TtlLruCache<String, Integer> usageCache =
            new TtlLruCache<>(5000, 60L * 60 * 1000, System::currentTimeMillis);
    private final TtlLruCache<String, VersionPage> pageCache =
            new TtlLruCache<>(200, 10L * 60 * 1000, System::currentTimeMillis);
    private final TtlLruCache<String, Long> dateCache =
            new TtlLruCache<>(5000, 60L * 60 * 1000, System::currentTimeMillis);

    public VersionService(MavenMetadataClient metadata, DepsDevClient depsDev) {
        this.metadata = metadata;
        this.depsDev = depsDev;
    }

    public VersionPage load(String groupId, String artifactId, boolean all) throws IOException {
        String key = groupId + ":" + artifactId;
        if (!all) {
            VersionPage cached = pageCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        List<String> raw = metadata.versions(groupId, artifactId);
        List<String> desc = new ArrayList<>(raw);
        desc.sort(VersionComparator.INSTANCE.reversed());
        List<String> visible = all ? desc : desc.subList(0, Math.min(DEFAULT_PAGE_SIZE, desc.size()));
        VersionPage page = new VersionPage(
                enrich(groupId, artifactId, visible),
                raw.size(),
                raw.size() > visible.size());
        if (!all) {
            pageCache.put(key, page);
        }
        return page;
    }

    private List<ArtifactVersion> enrich(String groupId, String artifactId, List<String> versions) {
        List<CompletableFuture<ArtifactVersion>> futures = new ArrayList<>(versions.size());
        for (String v : versions) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                String gav = groupId + ":" + artifactId + ":" + v;
                Integer usage = usageCache.get(gav);
                if (usage == null) {
                    usage = depsDev.dependentCount(groupId, artifactId, v);
                    usageCache.put(gav, usage);
                }
                Long date = dateCache.get(gav);
                if (date == null) {
                    date = metadata.publishedMillis(groupId, artifactId, v);
                    dateCache.put(gav, date);
                }
                return new ArtifactVersion(v, VersionComparator.isPrerelease(v), usage, date);
            }, pool));
        }
        List<ArtifactVersion> out = new ArrayList<>(versions.size());
        for (CompletableFuture<ArtifactVersion> f : futures) {
            out.add(f.join());
        }
        return out;
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
