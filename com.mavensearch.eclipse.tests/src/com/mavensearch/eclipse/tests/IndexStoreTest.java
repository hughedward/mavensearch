package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mavensearch.eclipse.index.IndexStore;

class IndexStoreTest {

    @TempDir
    Path dir;

    private Path write(String content) throws IOException {
        Path f = dir.resolve("index.txt.gz");
        try (var out = new GZIPOutputStream(Files.newOutputStream(f));
             var w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write(content);
        }
        return f;
    }

    @Test
    void loadsNamesAndUsagesInOrder() throws IOException {
        Path f = write("org.springframework.boot:spring-boot-starter-web\t817305\n"
                + "com.alibaba:fastjson\t5082\n"
                + "junit:junit\t1000\n");
        IndexStore s = IndexStore.load(f);
        assertEquals(3, s.count());
        assertEquals("com.alibaba:fastjson", s.name(1));
        assertEquals(5082, s.usage(1));
        assertEquals("junit:junit", s.name(2));
        assertEquals(4, s.offsets().length);
        // 紧凑布局自洽：offsets 差 - 1 == 名字字节数
        byte[] names = s.names();
        for (int i = 0; i < s.count(); i++) {
            int len = s.offsets()[i + 1] - s.offsets()[i] - 1;
            String name = new String(names, s.offsets()[i], len, StandardCharsets.UTF_8);
            assertEquals(s.name(i), name);
        }
    }

    @Test
    void acceptsUtf8Names() throws IOException {
        Path f = write("com.example:artifact-with-µ\t5\n");
        IndexStore s = IndexStore.load(f);
        assertEquals("com.example:artifact-with-µ", s.name(0));
    }

    @Test
    void rejectsLineWithoutTab() throws IOException {
        assertThrows(IOException.class, () -> IndexStore.load(write("com.example:no-tab-here\n")));
    }

    @Test
    void rejectsNonNumericUsage() throws IOException {
        assertThrows(IOException.class, () -> IndexStore.load(write("com.example:a\txyz\n")));
    }

    @Test
    void rejectsEmptyName() throws IOException {
        assertThrows(IOException.class, () -> IndexStore.load(write("\t5\n")));
    }

    @Test
    void rejectsNotGzip() throws IOException {
        Path f = dir.resolve("plain.txt.gz");
        Files.writeString(f, "com.example:a\t5\n");
        assertThrows(IOException.class, () -> IndexStore.load(f));
    }
}
