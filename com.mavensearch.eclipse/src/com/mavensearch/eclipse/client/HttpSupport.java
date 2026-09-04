package com.mavensearch.eclipse.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPInputStream;

/** 统一 HTTP：UA、超时、gzip 解压、条件请求、HEAD 日期、失败重试 1 次。 */
public final class HttpSupport {

    public static final String USER_AGENT =
            "MavenSearchEclipse/1.0.0 (+https://github.com/hughedward/mavensearch)";

    private final HttpClient client;

    /** 包私有：统一由 create() 构造。 */
    HttpSupport(HttpClient client) {
        this.client = client;
    }

    public static HttpSupport create() {
        return create(null);
    }

    public static HttpSupport create(ProxySelector proxy) {
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxy != null) {
            b.proxy(proxy);
        }
        return new HttpSupport(b.build());
    }

    public record ConditionalResponse(int status, byte[] body, String etag, long lastModifiedMillis) {
        public static final int NOT_MODIFIED = 304;

        public boolean notModified() {
            return status == NOT_MODIFIED;
        }
    }

    public ConditionalResponse get(String url, Duration timeout, String etag, long lastModifiedMillis)
            throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return doGet(url, timeout, etag, lastModifiedMillis);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    private ConditionalResponse doGet(String url, Duration timeout, String etag, long lastModifiedMillis)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Encoding", "gzip")
                .GET();
        if (etag != null) {
            b.header("If-None-Match", etag);
        }
        if (lastModifiedMillis > 0) {
            b.header("If-Modified-Since", DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(ZonedDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(lastModifiedMillis),
                            java.time.ZoneOffset.UTC)));
        }
        HttpResponse<byte[]> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = "gzip".equalsIgnoreCase(
                resp.headers().firstValue("Content-Encoding").orElse(""))
                        ? gunzip(resp.body())
                        : resp.body();
        String et = resp.headers().firstValue("ETag").orElse(null);
        long lm = resp.headers().firstValue("Last-Modified")
                .map(HttpSupport::parseHttpDate).orElse(-1L);
        return new ConditionalResponse(resp.statusCode(), body, et, lm);
    }

    /** HEAD 取 Last-Modified（epoch ms）。任何失败返回 -1，不抛异常。 */
    public long lastModified(String url, Duration timeout) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", USER_AGENT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.headers().firstValue("Last-Modified")
                    .map(HttpSupport::parseHttpDate).orElse(-1L);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }
    }

    static long parseHttpDate(String s) {
        try {
            return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static byte[] gunzip(byte[] gz) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz));
             ByteArrayOutputStream out = new ByteArrayOutputStream(gz.length * 4)) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }
}
