package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.client.VersionComparator;

class VersionComparatorTest {

    private void assertOrdered(String... versions) {
        List<String> shuffled = new java.util.ArrayList<>(List.of(versions));
        java.util.Collections.reverse(shuffled); // List.reversed() 为 Java 21 API，BREE 17 下 ECJ 拒编译（已实测）
        List<String> sorted = shuffled.stream().sorted(VersionComparator.INSTANCE.reversed()).toList();
        assertEquals(List.of(versions), sorted);
    }

    @Test
    void numericCompareNotLexical() {
        assertOrdered("2.0.57", "2.0.10", "2.0.9", "2.0.5");
    }

    @Test
    void qualifierNumericCompare() {
        assertOrdered("2.0.0", "2.0.0-RC2", "2.0.0-RC1", "2.0.0-M10", "2.0.0-M2");
    }

    @Test
    void releaseBeatsQualifierAndMoreSegmentsWin() {
        assertOrdered("1.0.1", "1.0.0");
        assertOrdered("1.0.0", "1.0.0-rc1");
        assertOrdered("1.0", "1.0-alpha1");
    }

    @Test
    void snapshotIsLowest() {
        assertOrdered("1.0.0-alpha1", "1.0.0-SNAPSHOT");
        assertOrdered("1.0-SNAPSHOT", "0.9"); // 跨前缀：数字段先决定（1.0-SNAPSHOT > 0.9，与 Maven 语义一致；SNAPSHOT「最低」仅限同前缀限定词间）
    }

    @Test
    void alphaBetaRcOrder() {
        assertOrdered("1.0.0", "1.0.0-beta2", "1.0.0-beta1", "1.0.0-alpha2", "1.0.0-alpha1");
    }

    @Test
    void prereleaseFlag() {
        assertTrue(VersionComparator.isPrerelease("1.0.0-SNAPSHOT"));
        assertTrue(VersionComparator.isPrerelease("2.0.0-M3"));
        assertTrue(VersionComparator.isPrerelease("2.0.0-RC1"));
        assertTrue(VersionComparator.isPrerelease("1.0.0-alpha1"));
        assertTrue(VersionComparator.isPrerelease("1.0.0-beta2"));
        assertFalse(VersionComparator.isPrerelease("1.0.0"));
        assertFalse(VersionComparator.isPrerelease("2.0.57"));
        assertFalse(VersionComparator.isPrerelease("1.0.0.Final"));
    }
}
