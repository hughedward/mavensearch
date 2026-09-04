package com.mavensearch.eclipse.ui;

import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;

import com.mavensearch.eclipse.model.Artifact;

/** 依赖坐标的三种复制格式。 */
public final class CopyActions {

    private CopyActions() {
    }

    public static String maven(Artifact a, String version) {
        return "<dependency>\n"
                + "    <groupId>" + a.groupId() + "</groupId>\n"
                + "    <artifactId>" + a.artifactId() + "</artifactId>\n"
                + "    <version>" + version + "</version>\n"
                + "</dependency>";
    }

    public static String gradle(Artifact a, String version) {
        return "implementation '" + a.groupId() + ":" + a.artifactId() + ":" + version + "'";
    }

    public static String gradleKts(Artifact a, String version) {
        return "implementation(\"" + a.groupId() + ":" + a.artifactId() + ":" + version + "\")";
    }

    public static void copyToClipboard(String text) {
        Clipboard cb = new Clipboard(Display.getCurrent());
        try {
            cb.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
        } finally {
            cb.dispose();
        }
    }
}
