package com.mavensearch.eclipse.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.mavensearch.eclipse.model.Artifact;
import com.mavensearch.eclipse.model.ArtifactVersion;

/** 树节点模型与展示文案。UI 层唯一可单测的逻辑集中在此。 */
public final class SearchNodes {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    public record ArtifactNode(Artifact artifact) {
    }

    public record VersionNode(Artifact artifact, ArtifactVersion version) {
    }

    public record MoreNode(Artifact artifact) {
    }

    public record LoadingNode() {
        public static final LoadingNode INSTANCE = new LoadingNode();
    }

    public record ErrorNode(Artifact artifact, String message) {
    }

    private SearchNodes() {
    }

    public static String artifactText(Object node) {
        if (node instanceof ArtifactNode a) {
            return a.artifact().name();
        }
        if (node instanceof VersionNode v) {
            return v.version().version() + (v.version().prerelease() ? "  (pre-release)" : "")
                    + (v.version().publishedMillis() > 0
                            ? "   " + DATE.format(Instant.ofEpochMilli(v.version().publishedMillis()))
                            : "");
        }
        if (node instanceof MoreNode) {
            return "Show all versions…";
        }
        if (node instanceof LoadingNode) {
            return "Loading…";
        }
        if (node instanceof ErrorNode e) {
            return e.message();
        }
        return String.valueOf(node);
    }

    public static String usageText(Object node) {
        if (node instanceof ArtifactNode a) {
            return usage(a.artifact().usage());
        }
        if (node instanceof VersionNode v) {
            return usage(v.version().usage());
        }
        return "";
    }

    /** -1 → "—"（未知，BOM 恒 0 的口径说明见 README）；其余千分位。 */
    public static String usage(int usage) {
        return usage < 0 ? "—" : String.format(Locale.ROOT, "%,d", usage);
    }

    public static List<Object> artifactNodes(List<Artifact> artifacts) {
        return artifacts.stream().<Object>map(ArtifactNode::new).toList();
    }
}
