package com.mavensearch.eclipse.model;

/** 某个具体版本。usage<0 未知；publishedMillis<=0 未知（epoch 毫秒）。 */
public record ArtifactVersion(String version, boolean prerelease, int usage, long publishedMillis) {

    public static final int UNKNOWN_USAGE = -1;
}
