package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mavensearch.eclipse.index.IndexSearcher;
import com.mavensearch.eclipse.index.IndexStore;
import com.mavensearch.eclipse.model.Artifact;

class IndexSearcherTest {

    @TempDir
    Path dir;

    private IndexSearcher searcher(String content) throws IOException {
        Path f = dir.resolve("i.gz");
        try (var out = new GZIPOutputStream(Files.newOutputStream(f));
             var w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write(content);
        }
        return new IndexSearcher(IndexStore.load(f));
    }

    @Test
    void substringMatchReturnsPopularityOrder() throws IOException {
        IndexSearcher s = searcher(
                "org.springframework.boot:spring-boot-starter-web\t817305\n"
                + "org.springframework.boot:spring-boot-starter-test\t819475\n"
                + "com.alibaba:fastjson\t5082\n"
                + "junit:junit\t900000\n");
        // 注意：fixture 故意打乱一处的使用量——顺序必须来自行序而非数值排序
        List<Artifact> r = s.search("spring boot", 20);
        assertEquals("org.springframework.boot:spring-boot-starter-web", r.get(0).name());
        assertEquals("org.springframework.boot:spring-boot-starter-test", r.get(1).name());
        assertEquals(817305, r.get(0).usage());
    }

    @Test
    void hyphenEqualsSpace() throws IOException {
        IndexSearcher s = searcher("org.springframework.boot:spring-boot\t5\n");
        assertEquals(1, s.search("spring boot", 20).size());
        assertEquals(1, s.search("spring-boot", 20).size());
    }

    @Test
    void andSemanticsExcludesPartialHits() throws IOException {
        IndexSearcher s = searcher(
                "org.springframework:spring-core\t10\n"
                + "com.example:boot-utils\t20\n"
                + "org.springframework.boot:boot\t30\n");
        List<Artifact> r = s.search("spring boot", 20);
        assertEquals(1, r.size());
        assertEquals("org.springframework.boot:boot", r.get(0).name());
    }

    @Test
    void limitStopsEarly() throws IOException {
        IndexSearcher s = searcher("a:b1\t3\na:b2\t2\na:b3\t1\n");
        List<Artifact> r = s.search("a:b", 2);
        assertEquals(2, r.size());
        assertEquals("a:b1", r.get(0).name());
    }

    @Test
    void caseInsensitiveAndSeparatorAgnostic() throws IOException {
        // 索引格式契约保证名字全小写（构建期 .lower()，见 Task 3/12）；大小写折叠只发生在查询侧
        IndexSearcher s = searcher("com.foo:my-artifact\t1\n");
        assertEquals(1, s.search("My Artifact", 20).size());
    }

    @Test
    void emptyQueryReturnsNothing() throws IOException {
        IndexSearcher s = searcher("a:b\t1\n");
        assertTrue(s.search("", 20).isEmpty());
        assertTrue(s.search("   ", 20).isEmpty());
    }

    @Test
    void noHitReturnsEmpty() throws IOException {
        IndexSearcher s = searcher("a:b\t1\n");
        assertTrue(s.search("zzzznotfound", 20).isEmpty());
    }
}
