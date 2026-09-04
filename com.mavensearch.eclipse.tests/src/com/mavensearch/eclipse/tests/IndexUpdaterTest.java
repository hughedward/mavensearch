package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mavensearch.eclipse.client.HttpSupport;
import com.mavensearch.eclipse.index.IndexStore;
import com.mavensearch.eclipse.index.IndexUpdater;
import com.sun.net.httpserver.HttpServer;

class IndexUpdaterTest {

    @TempDir
    Path dir;

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private byte[] gzip(String content) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        try (var gz = new java.util.zip.GZIPOutputStream(bo);
             var w = new OutputStreamWriter(gz, StandardCharsets.UTF_8)) {
            w.write(content);
        }
        return bo.toByteArray();
    }

    @Test
    void refreshDownloadsValidatesAndSwapsAtomically() throws Exception {
        AtomicInteger etagSeen = new AtomicInteger(0);
        byte[] body = gzip("com.alibaba:fastjson\t5082\n");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/index/maven-central-index.txt.gz", ex -> {
            if ("\"v1\"".equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
                ex.getResponseHeaders().add("ETag", "\"v1\"");
                ex.sendResponseHeaders(304, -1);
                ex.close();
                return;
            }
            etagSeen.incrementAndGet();
            ex.getResponseHeaders().add("ETag", "\"v1\"");
            ex.getResponseHeaders().add("Content-Type", "application/gzip");
            ex.sendResponseHeaders(200, body.length);
            try (var os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/index/maven-central-index.txt.gz";

        IndexUpdater updater = new IndexUpdater(url, dir, HttpSupport.create());
        assertEquals(Optional.empty(), updater.loadLocal(), "尚未下载过");
        assertEquals(true, updater.refresh(), "首次应下载");
        Optional<IndexStore> local = updater.loadLocal();
        org.junit.jupiter.api.Assertions.assertTrue(local.isPresent());
        assertEquals(1, local.get().count());
        assertEquals(5082, local.get().usage(0));

        // 第二次：sidecar ETag 命中 → 304 → false，且不再产生 200
        int downloadsBefore = etagSeen.get();
        assertEquals(false, updater.refresh());
        assertEquals(downloadsBefore, etagSeen.get());
    }

    @Test
    void corruptDownloadIsRejectedAndOldIndexSurvives() throws Exception {
        byte[] bad = "this is not a valid gzip index".getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger serveCount = new AtomicInteger();
        server.createContext("/index/maven-central-index.txt.gz", ex -> {
            byte[] b = serveCount.incrementAndGet() == 1
                    ? gzip("good:pkg\t7\n")
                    : bad; // 第二次给损坏内容
            ex.sendResponseHeaders(200, b.length);
            try (var os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/index/maven-central-index.txt.gz";

        IndexUpdater updater = new IndexUpdater(url, dir, HttpSupport.create());
        org.junit.jupiter.api.Assertions.assertTrue(updater.refresh());
        org.junit.jupiter.api.Assertions.assertFalse(updater.refresh(), "损坏下载不得替换");
        Optional<IndexStore> local = updater.loadLocal();
        org.junit.jupiter.api.Assertions.assertTrue(local.isPresent());
        assertEquals("good:pkg", local.get().name(0), "旧索引仍可用");
        org.junit.jupiter.api.Assertions.assertTrue(updater.lastError().isPresent());
        try (var files = Files.list(dir)) {
            assertEquals(0, files.filter(p -> p.getFileName().toString().startsWith("index-")).count(),
                    "校验失败的 tmp 不得残留");
        }
    }

    @Test
    void loadLocalDeletesCorruptFile() throws Exception {
        Path f = dir.resolve("maven-central-index.txt.gz");
        Files.write(f, "garbage".getBytes(StandardCharsets.UTF_8));
        IndexUpdater updater = new IndexUpdater("http://127.0.0.1:1/x", dir, HttpSupport.create());
        assertEquals(Optional.empty(), updater.loadLocal());
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(f), "损坏文件应删除");
    }
}
