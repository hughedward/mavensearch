package com.mavensearch.eclipse.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/** GA → 版本列表（maven-metadata.xml）；GA:V → 发布日期（HEAD .pom 的 Last-Modified）。 */
public class MavenMetadataClient {

    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(5);

    private final HttpSupport http;
    private final String repoBase;

    public MavenMetadataClient(HttpSupport http, String repoBase) {
        this.http = http == null ? HttpSupport.create() : http;
        this.repoBase = repoBase == null ? "https://repo1.maven.org/maven2/"
                : repoBase.endsWith("/") ? repoBase : repoBase + "/";
    }

    public MavenMetadataClient() {
        this(null, null);
    }

    /** 返回文件内出现顺序（升序），空列表合法（包无版本）。 */
    public List<String> versions(String groupId, String artifactId) throws IOException {
        String url = repoBase + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        HttpSupport.ConditionalResponse r;
        try {
            r = http.get(url, METADATA_TIMEOUT, null, 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
        if (r.status() != 200) {
            throw new IOException("maven-metadata.xml HTTP " + r.status() + " for " + groupId + ":" + artifactId);
        }
        return parseVersions(r.body());
    }

    static List<String> parseVersions(byte[] xml) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            List<String> out = new ArrayList<>();
            NodeList nodes = doc.getElementsByTagName("version");
            for (int i = 0; i < nodes.getLength(); i++) {
                String v = nodes.item(i).getTextContent().trim();
                if (!v.isEmpty()) {
                    out.add(v);
                }
            }
            return out;
        } catch (Exception e) {
            throw new IOException("Bad maven-metadata.xml: " + e.getMessage(), e);
        }
    }

    /** HEAD {a}-{v}.pom 的 Last-Modified；失败 -1（UI 不显示日期）。 */
    public long publishedMillis(String groupId, String artifactId, String version) {
        String url = repoBase + groupId.replace('.', '/') + "/" + artifactId + "/"
                + version + "/" + artifactId + "-" + version + ".pom";
        return http.lastModified(url, METADATA_TIMEOUT);
    }
}
