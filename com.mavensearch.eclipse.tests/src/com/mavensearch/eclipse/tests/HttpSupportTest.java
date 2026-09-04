package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.client.HttpSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class HttpSupportTest {

    private HttpServer server;
    private final Map<String, String> capturedHeaders = new ConcurrentHashMap<>();
    private final AtomicInteger hits = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void send(HttpExchange ex, int code, byte[] body, String... headers) throws IOException {
        ex.getResponseHeaders().add("Content-Type", "application/octet-stream");
        for (int i = 0; i + 1 < headers.length; i += 2) {
            ex.getResponseHeaders().add(headers[i], headers[i + 1]);
        }
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        } else {
            ex.close();
        }
    }

    @Test
    void plainGetReturnsBodyAndCapturesUserAgent() throws Exception {
        String base = start(ex -> {
            capturedHeaders.put("UA", ex.getRequestHeaders().getFirst("User-Agent"));
            hits.incrementAndGet();
            send(ex, 200, "hello".getBytes(StandardCharsets.UTF_8));
        });
        HttpSupport http = HttpSupport.create();
        var r = http.get(base + "/x", Duration.ofSeconds(5), null, 0);
        assertEquals(200, r.status());
        assertEquals("hello", new String(r.body(), StandardCharsets.UTF_8));
        assertEquals(HttpSupport.USER_AGENT, capturedHeaders.get("UA"));
    }

    @Test
    void gunzipsWhenContentEncodingGzip() throws Exception {
        String base = start(ex -> {
            ByteArrayOutputStream gz = new ByteArrayOutputStream();
            try (GZIPOutputStream g = new GZIPOutputStream(gz)) {
                g.write("{\"g\":\"a\"}".getBytes(StandardCharsets.UTF_8));
            }
            send(ex, 200, gz.toByteArray(), "Content-Encoding", "gzip");
        });
        var r = HttpSupport.create().get(base + "/gz", Duration.ofSeconds(5), null, 0);
        assertEquals("{\"g\":\"a\"}", new String(r.body(), StandardCharsets.UTF_8));
    }

    @Test
    void conditionalRequestEchoes304AndSendsIfNoneMatch() throws Exception {
        String base = start(ex -> {
            String inm = ex.getRequestHeaders().getFirst("If-None-Match");
            if (inm != null) {
                capturedHeaders.put("INM", inm); // CHM 不收 null；首请求无 INM 跳过
            }
            if ("\"abc\"".equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
                send(ex, 304, new byte[0], "ETag", "\"abc\"");
            } else {
                send(ex, 200, "v2".getBytes(StandardCharsets.UTF_8), "ETag", "\"abc\"");
            }
        });
        HttpSupport http = HttpSupport.create();
        var first = http.get(base + "/c", Duration.ofSeconds(5), null, 0);
        assertEquals("\"abc\"", first.etag());
        var second = http.get(base + "/c", Duration.ofSeconds(5), first.etag(), 0);
        assertTrue(second.notModified());
        assertEquals("\"abc\"", capturedHeaders.get("INM"));
    }

    @Test
    void retriesOnceOnIoError() throws Exception {
        String base = start(ex -> {
            if (hits.incrementAndGet() == 1) {
                // 第一次不写任何响应直接断开，制造 IOException
                ex.close();
                return;
            }
            send(ex, 200, "ok".getBytes(StandardCharsets.UTF_8));
        });
        var r = HttpSupport.create().get(base + "/r", Duration.ofSeconds(5), null, 0);
        assertEquals(200, r.status());
        assertEquals(2, hits.get());
    }

    @Test
    void timeoutThrows() throws Exception {
        String base = start(ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            send(ex, 200, "late".getBytes(StandardCharsets.UTF_8));
        });
        HttpSupport http = HttpSupport.create();
        assertThrows(IOException.class,
                () -> http.get(base + "/slow", Duration.ofMillis(200), null, 0));
    }

    @Test
    void headReturnsLastModifiedMillis() throws Exception {
        String base = start(ex -> {
            if (ex.getRequestMethod().equals("HEAD")) {
                send(ex, 200, new byte[0], "Last-Modified", "Tue, 01 Sep 2026 00:00:00 GMT");
            } else {
                send(ex, 405, new byte[0]);
            }
        });
        long ms = HttpSupport.create().lastModified(base + "/f.pom", Duration.ofSeconds(5));
        assertEquals(1788220800000L, ms);
    }

    @Test
    void headFailureReturnsMinusOne() throws Exception {
        long ms = HttpSupport.create().lastModified(
                "http://127.0.0.1:1/none.pom", Duration.ofSeconds(1));
        assertEquals(-1, ms);
    }
}
