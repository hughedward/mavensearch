package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.mavensearch.eclipse.client.HttpSupport;
import com.mavensearch.eclipse.client.MavenMetadataClient;
import com.sun.net.httpserver.HttpServer;

class MavenMetadataClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private MavenMetadataClient client(String path, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, ex -> {
            byte[] b = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/xml");
            ex.sendResponseHeaders(200, b.length);
            try (var os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new MavenMetadataClient(HttpSupport.create(), base);
    }

    @Test
    void parsesVersionsInFileOrder() throws Exception {
        MavenMetadataClient c = client("/com/alibaba/fastjson/maven-metadata.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>com.alibaba</groupId>
                  <artifactId>fastjson</artifactId>
                  <versioning>
                    <latest>2.0.57</latest>
                    <versions>
                      <version>1.2.47</version>
                      <version>1.2.83</version>
                      <version>2.0.57</version>
                    </versions>
                    <lastUpdated>20250406000000</lastUpdated>
                  </versioning>
                </metadata>
                """);
        List<String> v = c.versions("com.alibaba", "fastjson");
        assertEquals(List.of("1.2.47", "1.2.83", "2.0.57"), v);
    }

    @Test
    void malformedXmlThrows() throws Exception {
        MavenMetadataClient c = client("/com/example/a/maven-metadata.xml", "<metadata><versions>");
        assertThrows(IOException.class, () -> c.versions("com.example", "a"));
    }

    @Test
    void rejectsDoctype() throws Exception {
        MavenMetadataClient c = client("/com/example/b/maven-metadata.xml", """
                <?xml version="1.0"?>
                <!DOCTYPE metadata [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <metadata>&xxe;</metadata>
                """);
        assertThrows(IOException.class, () -> c.versions("com.example", "b"));
    }

    @Test
    void emptyVersionListIsAllowed() throws Exception {
        MavenMetadataClient c = client("/com/example/c/maven-metadata.xml",
                "<metadata><versioning><versions/></versioning></metadata>");
        assertTrue(c.versions("com.example", "c").isEmpty());
    }
}
