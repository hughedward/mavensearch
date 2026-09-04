package com.mavensearch.eclipse.model;

/** 一个 Maven 构件（GA 级）。usage 为使用量，负数表示未知。 */
public record Artifact(String groupId, String artifactId, int usage) {

    public static final int UNKNOWN_USAGE = -1;

    /** "groupId:artifactId" */
    public String name() {
        return groupId + ":" + artifactId;
    }
}
