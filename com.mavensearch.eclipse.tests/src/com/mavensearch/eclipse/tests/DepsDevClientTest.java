package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.client.DepsDevClient;
import com.mavensearch.eclipse.client.HttpSupport;
import com.sun.net.httpserver.HttpServer;

class DepsDevClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private DepsDevClient client(String path, int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, ex -> {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            try (var os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();
        return new DepsDevClient(HttpSupport.create(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v3alpha");
    }

    @Test
    void readsTopLevelDependentCount() throws Exception {
        DepsDevClient c = client("/v3alpha/systems/maven/packages/com.alibaba:fastjson/versions/1.2.83:dependents",
                200, "{\"dependentCount\":5082,\"directDependentCount\":900,\"indirectDependentCount\":4182}");
        assertEquals(5082, c.dependentCount("com.alibaba", "fastjson", "1.2.83"));
    }

    @Test
    void zeroIsAValidCount() throws Exception {
        DepsDevClient c = client("/v3alpha/systems/maven/packages/g:a/versions/1:dependents",
                200, "{\"dependentCount\":0,\"directDependentCount\":0,\"indirectDependentCount\":0}");
        assertEquals(0, c.dependentCount("g", "a", "1"));
    }

    @Test
    void missingFieldReturnsUnknown() throws Exception {
        DepsDevClient c = client("/v3alpha/systems/maven/packages/g:a/versions/1:dependents",
                200, "{\"unrelated\":true}");
        assertEquals(-1, c.dependentCount("g", "a", "1"));
    }

    @Test
    void httpErrorReturnsUnknown() throws Exception {
        DepsDevClient c = client("/v3alpha/systems/maven/packages/g:a/versions/1:dependents",
                500, "{\"error\":\"boom\"}");
        assertEquals(-1, c.dependentCount("g", "a", "1"));
    }

    @Test
    void unreachableReturnsUnknown() {
        DepsDevClient c = new DepsDevClient(HttpSupport.create(), "http://127.0.0.1:1");
        assertEquals(-1, c.dependentCount("g", "a", "1"));
    }
}
