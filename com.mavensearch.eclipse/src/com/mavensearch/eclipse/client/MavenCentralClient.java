package com.mavensearch.eclipse.client;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mavensearch.eclipse.json.MiniJson;
import com.mavensearch.eclipse.model.Artifact;

/**
 * 索引未就绪时的在线兜底（search.maven.org solrsearch，索引停更于 2025 中）。
 * 只读 g/a 字段——latestVersion 等滞后字段一律不用；usage 一律 UNKNOWN。
 */
public final class MavenCentralClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpSupport http;
    private final String searchBase;

    public MavenCentralClient(HttpSupport http, String searchBase) {
        this.http = http == null ? HttpSupport.create() : http;
        this.searchBase = searchBase == null ? "https://search.maven.org/solrsearch/select" : searchBase;
    }

    public MavenCentralClient() {
        this(null, null);
    }

    public List<Artifact> search(String query, int limit) throws IOException {
        String url = searchBase + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&rows=" + limit + "&wt=json";
        HttpSupport.ConditionalResponse r;
        try {
            r = http.get(url, TIMEOUT, null, 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
        if (r.status() != 200) {
            throw new IOException("solrsearch HTTP " + r.status());
        }
        Map<String, Object> root = MiniJson.obj(new String(r.body(), StandardCharsets.UTF_8));
        Map<String, Object> response = cast(root.get("response"));
        List<Object> docs = response == null ? List.of() : toList(response.get("docs"));
        List<Artifact> out = new ArrayList<>();
        for (Object o : docs) {
            Map<String, Object> doc = cast(o);
            if (doc == null) {
                continue;
            }
            String g = MiniJson.str(doc, "g");
            String a = MiniJson.str(doc, "a");
            if (g != null && a != null) {
                out.add(new Artifact(g, a, Artifact.UNKNOWN_USAGE));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toList(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }
}
