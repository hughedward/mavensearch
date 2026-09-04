package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mavensearch.eclipse.client.HttpSupport;
import com.mavensearch.eclipse.client.MavenCentralClient;
import com.mavensearch.eclipse.index.IndexSearcher;
import com.mavensearch.eclipse.index.IndexStore;
import com.mavensearch.eclipse.model.Artifact;
import com.mavensearch.eclipse.service.SearchService;
import com.sun.net.httpserver.HttpServer;

class SearchServiceTest {

    @TempDir
    Path dir;

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private IndexStore store(String content) throws IOException {
        Path f = dir.resolve("i.gz");
        try (var out = new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(f));
             var w = new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write(content);
        }
        return IndexStore.load(f);
    }

    private HttpServer solrServer(String body) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress(0), 0);
        s.createContext("/solrsearch/select", ex -> {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            try (var os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        s.start();
        return s;
    }

    @Test
    void localSearchWhenIndexReady() throws IOException {
        IndexSearcher is = new IndexSearcher(store(
                "org.springframework.boot:spring-boot-starter-web\t817305\ncom.alibaba:fastjson\t5082\n"));
        SearchService svc = new SearchService(() -> is, new MavenCentralClient());
        var r = svc.search("spring boot", 20);
        assertTrue(r.fromIndex());
        assertEquals(1, r.artifacts().size());
        assertEquals(817305, r.artifacts().get(0).usage());
    }

    @Test
    void fallsBackOnlineWhenIndexNull() throws IOException {
        server = solrServer("{\"response\":{\"numFound\":1,\"docs\":[{\"g\":\"com.alibaba\",\"a\":\"fastjson\"}]}}");
        MavenCentralClient online = new MavenCentralClient(HttpSupport.create(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/solrsearch/select");
        SearchService svc = new SearchService(() -> null, online);
        var r = svc.search("fastjson", 20);
        assertFalse(r.fromIndex());
        assertEquals("com.alibaba:fastjson", r.artifacts().get(0).name());
    }

    @Test
    void onlineFailureDegradesToEmpty() {
        MavenCentralClient dead = new MavenCentralClient(HttpSupport.create(), "http://127.0.0.1:1/solrsearch/select");
        SearchService svc = new SearchService(() -> null, dead);
        var r = svc.search("anything", 20);
        assertFalse(r.fromIndex());
        assertTrue(r.artifacts().isEmpty());
    }

    @Test
    void fullGaQueryHitsIndexExactMatch() throws IOException {
        IndexSearcher is = new IndexSearcher(store(
                "com.alibaba:fastjson\t5082\nother:thing\t1\n"));
        SearchService svc = new SearchService(() -> is, new MavenCentralClient());
        var r = svc.search("com.alibaba:fastjson", 20);
        assertEquals(1, r.artifacts().size());
        assertEquals(5082, r.artifacts().get(0).usage());
    }

    @Test
    void fullGaQueryUnknownArtifactStillReturnsOneEntry() throws IOException {
        IndexSearcher is = new IndexSearcher(store("com.alibaba:fastjson\t5082\n"));
        SearchService svc = new SearchService(() -> is, new MavenCentralClient());
        var r = svc.search("brand.new:artifact", 20);
        assertFalse(r.fromIndex());
        assertEquals(1, r.artifacts().size());
        assertEquals(Artifact.UNKNOWN_USAGE, r.artifacts().get(0).usage());
    }

    @Test
    void fullGaQueryWithoutIndexReturnsUnknownEntry() throws IOException {
        server = solrServer("{\"response\":{\"numFound\":0,\"docs\":[]}}");
        MavenCentralClient online = new MavenCentralClient(HttpSupport.create(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/solrsearch/select");
        SearchService svc = new SearchService(() -> null, online);
        var r = svc.search("brand.new:artifact", 20);
        assertEquals(1, r.artifacts().size());
        assertEquals(Artifact.UNKNOWN_USAGE, r.artifacts().get(0).usage());
    }

    @Test
    void blankQueryIsEmpty() throws IOException {
        SearchService svc = new SearchService(() -> null, new MavenCentralClient());
        assertTrue(svc.search("  ", 20).artifacts().isEmpty());
    }
}
