package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.client.DepsDevClient;
import com.mavensearch.eclipse.client.MavenMetadataClient;
import com.mavensearch.eclipse.model.ArtifactVersion;
import com.mavensearch.eclipse.service.VersionService.VersionPage;
import com.mavensearch.eclipse.service.VersionService;

class VersionServiceTest {

    /** 记录调用次数的假 metadata client——通过继承覆写（两类非 final 方法）。 */
    static class FakeMetadata extends MavenMetadataClient {
        int versionsCalls;
        int dateCalls;
        final Map<String, Long> dates = new ConcurrentHashMap<>();

        @Override
        public List<String> versions(String groupId, String artifactId) throws IOException {
            versionsCalls++;
            return List.of("1.0.0", "2.0.0-RC1", "1.5.0", "2.0.0");
        }

        @Override
        public long publishedMillis(String groupId, String artifactId, String version) {
            dateCalls++;
            return dates.getOrDefault(version, 1789996800000L);
        }
    }

    static class FakeDeps extends DepsDevClient {
        int calls;

        @Override
        public int dependentCount(String groupId, String artifactId, String version) {
            calls++;
            return switch (version) {
                case "2.0.0" -> 500;
                case "2.0.0-RC1" -> 0;
                default -> 10;
            };
        }
    }

    private VersionService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void sortsDescendingMarksPrereleaseAndTruncates() throws Exception {
        FakeMetadata meta = new FakeMetadata();
        service = new VersionService(meta, new FakeDeps());
        VersionPage p = service.load("g", "a", false);
        assertEquals(4, p.totalVersions());
        assertFalse(p.truncated());
        List<String> names = p.versions().stream().map(ArtifactVersion::version).toList();
        assertEquals(List.of("2.0.0", "2.0.0-RC1", "1.5.0", "1.0.0"), names);
        assertTrue(p.versions().get(1).prerelease());
        assertFalse(p.versions().get(0).prerelease());
    }

    @Test
    void enrichesUsageAndDate() throws Exception {
        FakeMetadata meta = new FakeMetadata();
        FakeDeps deps = new FakeDeps();
        service = new VersionService(meta, deps);
        VersionPage p = service.load("g", "a", false);
        ArtifactVersion top = p.versions().get(0); // 2.0.0
        assertEquals(500, top.usage());
        assertTrue(top.publishedMillis() > 0);
        assertEquals(4, deps.calls, "每个版本一次 deps.dev");
    }

    @Test
    void cachesVersionListWithin10Minutes() throws Exception {
        FakeMetadata meta = new FakeMetadata();
        service = new VersionService(meta, new FakeDeps());
        service.load("g", "a", false);
        service.load("g", "a", false);
        assertEquals(1, meta.versionsCalls, "第二次应命中 g:a 缓存");
    }

    @Test
    void cachesUsagePerGav() throws Exception {
        FakeMetadata meta = new FakeMetadata();
        FakeDeps deps = new FakeDeps();
        service = new VersionService(meta, deps);
        service.load("g", "a", false);
        service.load("g", "a", true); // all=true 绕过列表缓存但 usage 已缓存
        // 版本列表缓存 10 分钟内复用 → deps 调用仍是 4 次
        assertEquals(4, deps.calls);
    }

    @Test
    void metadataFailurePropagates() {
        FakeMetadata meta = new FakeMetadata() {
            @Override
            public List<String> versions(String groupId, String artifactId) throws IOException {
                throw new IOException("HTTP 404");
            }
        };
        service = new VersionService(meta, new FakeDeps());
        assertThrows(IOException.class, () -> service.load("g", "a", false));
    }

    @Test
    void allFlagReturnsEverything() throws Exception {
        FakeMetadata meta = new FakeMetadata();
        service = new VersionService(meta, new FakeDeps());
        VersionPage p = service.load("g", "a", true);
        assertEquals(4, p.versions().size());
        assertFalse(p.truncated());
    }
}
