package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.client.HttpSupport;
import com.mavensearch.eclipse.client.MavenCentralClient;
import com.mavensearch.eclipse.model.Artifact;
import com.sun.net.httpserver.HttpServer;

class MavenCentralClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private MavenCentralClient client(int status, String body, java.util.function.BiConsumer<com.sun.net.httpserver.HttpExchange, String> headerCheck) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/solrsearch/select", ex -> {
            if (headerCheck != null) {
                headerCheck.accept(ex, ex.getRequestURI().getRawQuery());
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            try (var os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();
        return new MavenCentralClient(HttpSupport.create(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/solrsearch/select");
    }

    @Test
    void readsOnlyGroupAndArtifact() throws Exception {
        // 真实响应样本（2026-09 实测），latestVersion/p 等字段一律不读
        MavenCentralClient c = client(200, """
                {"response":{"numFound":170,"start":0,"docs":[
                  {"id":"com.alibaba:fastjson","g":"com.alibaba","a":"fastjson",
                   "latestVersion":"2.0.57","p":"jar","versionCount":349},
                  {"id":"junit:junit","g":"junit","a":"junit","p":"jar"}]}}
                """, null);
        List<Artifact> r = c.search("fastjson", 20);
        assertEquals(2, r.size());
        assertEquals("com.alibaba", r.get(0).groupId());
        assertEquals("fastjson", r.get(0).artifactId());
        assertEquals(-1, r.get(0).usage());
    }

    @Test
    void emptyDocsIsFine() throws Exception {
        MavenCentralClient c = client(200,
                "{\"response\":{\"numFound\":0,\"docs\":[]}}", null);
        assertEquals(0, c.search("zzz", 20).size());
    }

    @Test
    void httpErrorThrowsIoException() throws Exception {
        MavenCentralClient c = client(500, "err", null);
        assertThrows(IOException.class, () -> c.search("x", 20));
    }

    @Test
    void queryIsUrlEncoded() throws Exception {
        StringBuilder captured = new StringBuilder();
        MavenCentralClient c = client(200,
                "{\"response\":{\"numFound\":0,\"docs\":[]}}",
                (ex, q) -> captured.append(q));
        c.search("spring boot", 5);
        org.junit.jupiter.api.Assertions.assertTrue(
                captured.toString().contains("rows=5"),
                "query=" + captured);
    }
}
