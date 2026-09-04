package com.mavensearch.eclipse.tests.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.client.DepsDevClient;
import com.mavensearch.eclipse.client.MavenCentralClient;
import com.mavensearch.eclipse.client.MavenMetadataClient;
import com.mavensearch.eclipse.model.Artifact;

/**
 * 真实调用上游，验证所需字段仍然存在。上游会静默腐烂（solrsearch 停更一年无人公告），
 * 此测试每周 CI 定时运行，以红灯暴露变更。本机手动跑：
 * mvn test -Dtest=UpstreamContractTest -Dgroups=contract -DfailIfNoTests=false
 */
@Tag("contract")
class UpstreamContractTest {

    @Test
    void repo1MavenMetadataHasVersions() throws Exception {
        List<String> v = new MavenMetadataClient().versions("com.alibaba", "fastjson");
        assertFalse(v.isEmpty());
        assertTrue(v.contains("1.2.83"));
    }

    @Test
    void repo1HeadReturnsDate() {
        long ms = new MavenMetadataClient().publishedMillis("com.alibaba", "fastjson", "1.2.83");
        assertTrue(ms > 0);
    }

    @Test
    void depsDevHasTopLevelDependentCount() {
        int n = new DepsDevClient().dependentCount("com.alibaba", "fastjson", "1.2.83");
        assertTrue(n > 0, "dependentCount=" + n);
    }

    @Test
    void solrsearchStillReturnsDocs() throws Exception {
        List<Artifact> r = new MavenCentralClient().search("fastjson", 5);
        assertFalse(r.isEmpty());
        assertNotNull(r.get(0).groupId());
    }
}
