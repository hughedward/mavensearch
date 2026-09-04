package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.model.Artifact;
import com.mavensearch.eclipse.model.ArtifactVersion;
import com.mavensearch.eclipse.ui.SearchNodes;

class SearchNodesTest {

    @Test
    void usageFormatting() {
        assertEquals("—", SearchNodes.usage(-1));
        assertEquals("0", SearchNodes.usage(0));
        assertEquals("817,305", SearchNodes.usage(817305));
    }

    @Test
    void versionTextContainsDatePrereleaseAndPlain() {
        var released = new ArtifactVersion("2.0.57", false, 272, 1789996800000L);
        var pre = new ArtifactVersion("2.0.0-RC1", true, 5, 0);
        String s1 = SearchNodes.artifactText(new SearchNodes.VersionNode(
                new Artifact("com.alibaba", "fastjson", 5082), released));
        org.junit.jupiter.api.Assertions.assertTrue(s1.startsWith("2.0.57 "));
        org.junit.jupiter.api.Assertions.assertTrue(s1.contains("(pre-release)") == false);
        String s2 = SearchNodes.artifactText(new SearchNodes.VersionNode(
                new Artifact("com.alibaba", "fastjson", 5082), pre));
        org.junit.jupiter.api.Assertions.assertTrue(s2.contains("(pre-release)"));
        org.junit.jupiter.api.Assertions.assertFalse(s2.contains("1970"), "未知日期不得显示");
    }

    @Test
    void artifactNodesWrapsList() {
        List<Object> nodes = SearchNodes.artifactNodes(
                List.of(new Artifact("g", "a", 1)));
        assertEquals(1, nodes.size());
        assertEquals("g:a", SearchNodes.artifactText(nodes.get(0)));
    }
}
