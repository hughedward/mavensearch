package com.mavensearch.eclipse.client;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.mavensearch.eclipse.json.MiniJson;
import com.mavensearch.eclipse.model.ArtifactVersion;

/** GAV → 版本级使用量（api.deps.dev，顶层 dependentCount）。失败一律返回 UNKNOWN，不抛异常。 */
public final class DepsDevClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpSupport http;
    private final String apiBase;

    public DepsDevClient(HttpSupport http, String apiBase) {
        this.http = http == null ? HttpSupport.create() : http;
        this.apiBase = apiBase == null ? "https://api.deps.dev/v3alpha" : apiBase;
    }

    public DepsDevClient() {
        this(null, null);
    }

    public int dependentCount(String groupId, String artifactId, String version) {
        String pkg = URLEncoder.encode(groupId + ":" + artifactId, StandardCharsets.UTF_8);
        String url = apiBase + "/systems/maven/packages/" + pkg + "/versions/"
                + URLEncoder.encode(version, StandardCharsets.UTF_8) + ":dependents";
        try {
            HttpSupport.ConditionalResponse r = http.get(url, TIMEOUT, null, 0);
            if (r.status() != 200) {
                return ArtifactVersion.UNKNOWN_USAGE;
            }
            Map<String, Object> m = MiniJson.obj(new String(r.body(), StandardCharsets.UTF_8));
            Double n = MiniJson.num(m, "dependentCount");
            return n == null || n < 0 || n > Integer.MAX_VALUE
                    ? ArtifactVersion.UNKNOWN_USAGE
                    : (int) (double) n;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ArtifactVersion.UNKNOWN_USAGE;
        }
    }
}
