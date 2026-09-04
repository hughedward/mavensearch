package com.mavensearch.eclipse.index;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * 全库索引的紧凑存储：names 全部名字连续（'\n' 分隔）+ offsets[count+1] + usages[count]。
 * 约 27MB 堆（620k 条）。任何格式错误抛 IOException——绝不以半份索引提供服务。
 */
public final class IndexStore {

    private final byte[] names;
    private final int[] offsets;
    private final int[] usages;

    private IndexStore(byte[] names, int[] offsets, int[] usages) {
        this.names = names;
        this.offsets = offsets;
        this.usages = usages;
    }

    public static IndexStore load(Path gzipFile) throws IOException {
        byte[] data = gunzip(Files.readAllBytes(gzipFile));
        // 第一遍：数行数与名字总字节数
        int count = 0;
        int namesTotal = 0;
        int lineStart = 0;
        int tabAt = -1;
        for (int i = 0; i <= data.length; i++) {
            boolean end = i == data.length;
            if (!end && data[i] != '\n') {
                continue;
            }
            if (end && lineStart == i) {
                break; // 末尾无换行的空尾
            }
            tabAt = findByte(data, lineStart, i, (byte) '\t');
            if (tabAt < 0) {
                throw new IOException("Index corrupt at line " + (count + 1) + ": no tab");
            }
            if (tabAt == lineStart) {
                throw new IOException("Index corrupt at line " + (count + 1) + ": empty name");
            }
            count++;
            namesTotal += tabAt - lineStart + 1; // 名字字节 + '\n'
            lineStart = i + 1;
        }
        byte[] names = new byte[namesTotal];
        int[] offsets = new int[count + 1];
        int[] usages = new int[count];
        // 第二遍：填充
        int row = 0;
        int w = 0;
        lineStart = 0;
        for (int i = 0; i <= data.length; i++) {
            boolean end = i == data.length;
            if (!end && data[i] != '\n') {
                continue;
            }
            if (end && lineStart == i) {
                break;
            }
            int tabAt2 = findByte(data, lineStart, i, (byte) '\t');
            offsets[row] = w;
            System.arraycopy(data, lineStart, names, w, tabAt2 - lineStart);
            w += tabAt2 - lineStart;
            names[w++] = '\n';
            usages[row] = parseUsage(data, tabAt2 + 1, i, row);
            row++;
            lineStart = i + 1;
        }
        offsets[count] = w;
        return new IndexStore(names, offsets, usages);
    }

    private static int findByte(byte[] b, int from, int to, byte target) {
        for (int i = from; i < to; i++) {
            if (b[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private static int parseUsage(byte[] b, int from, int to, int row) throws IOException {
        if (from >= to) {
            throw new IOException("Index corrupt at line " + (row + 1) + ": empty usage");
        }
        int v = 0;
        for (int i = from; i < to; i++) {
            byte c = b[i];
            if (c < '0' || c > '9') {
                throw new IOException("Index corrupt at line " + (row + 1) + ": bad usage");
            }
            v = v * 10 + (c - '0');
            if (v < 0) {
                throw new IOException("Index corrupt at line " + (row + 1) + ": usage overflow");
            }
        }
        return v;
    }

    private static byte[] gunzip(byte[] gz) throws IOException {
        try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz));
             ByteArrayOutputStream out = new ByteArrayOutputStream(gz.length * 4)) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    public int count() {
        return usages.length;
    }

    public String name(int i) {
        return new String(names, offsets[i], offsets[i + 1] - offsets[i] - 1, StandardCharsets.UTF_8);
    }

    public int usage(int i) {
        return usages[i];
    }

    public byte[] names() {
        return names;
    }

    public int[] offsets() {
        return offsets;
    }
}
