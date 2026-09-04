package com.mavensearch.eclipse.client;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 版本号升序比较器：按 [-.] 切 token；降序展示处调用 reversed()（见 Task 7）。
 * 规则（简化 Maven 语义，够排序展示用）：
 *  - 数字 token 按数值比较（2.0.57 > 2.0.5，M10 > M2）
 *  - 限定词序：snapshot < alpha < beta < rc/milestone < release(数字/末尾)
 *  - 前缀相同：剩余是限定词 → 短者大（1.0.0 > 1.0.0-rc1）；剩余是数字 → 长者大（1.0.1 > 1.0.0）
 */
public final class VersionComparator implements Comparator<String> {

    public static final VersionComparator INSTANCE = new VersionComparator();

    private static final Pattern SPLIT = Pattern.compile("[-._]");
    private static final Pattern NUMERIC = Pattern.compile("\\d+");
    private static final Pattern PRERELEASE = Pattern.compile(
            "(?i)^.*(snapshot|-m\\d+|-rc\\d*|-alpha\\d*|-beta\\d*|-cr\\d*|-ea|-milestone\\d*).*$");

    private VersionComparator() {
    }

    @Override
    public int compare(String a, String b) {
        List<String> ta = List.of(SPLIT.split(a));
        List<String> tb = List.of(SPLIT.split(b));
        int n = Math.min(ta.size(), tb.size());
        for (int i = 0; i < n; i++) {
            int c = compareToken(ta.get(i), tb.get(i));
            if (c != 0) {
                return c;
            }
        }
        if (ta.size() == tb.size()) {
            return 0;
        }
        // 前缀相同，看剩余第一个 token：限定词 → 短者大；数字 → 长者大
        String extra = ta.size() > tb.size() ? ta.get(n) : tb.get(n);
        boolean extraIsQualifier = !NUMERIC.matcher(extra).matches();
        boolean longerIsA = ta.size() > tb.size();
        if (extraIsQualifier) {
            return longerIsA ? -1 : 1; // 1.0.0 > 1.0.0-rc1 → 带限定词者小
        }
        return longerIsA ? 1 : -1; // 1.0.1 > 1.0.0 → 更长者大
    }

    private static int compareToken(String x, String y) {
        boolean xn = NUMERIC.matcher(x).matches();
        boolean yn = NUMERIC.matcher(y).matches();
        if (xn && yn) {
            return Long.compare(Long.parseLong(x), Long.parseLong(y));
        }
        if (xn) {
            return 1; // 数字 > 限定词（含空）
        }
        if (yn) {
            return -1;
        }
        int c = Integer.compare(qualifierRank(x), qualifierRank(y));
        if (c != 0) {
            return c;
        }
        java.util.regex.Matcher mx = Pattern.compile("^(\\D*)(\\d+)$").matcher(x.toLowerCase(Locale.ROOT));
        java.util.regex.Matcher my = Pattern.compile("^(\\D*)(\\d+)$").matcher(y.toLowerCase(Locale.ROOT));
        if (mx.matches() && my.matches() && mx.group(1).equals(my.group(1))) {
            return Long.compare(Long.parseLong(mx.group(2)), Long.parseLong(my.group(2)));
        }
        return x.compareToIgnoreCase(y);
    }

    private static int qualifierRank(String q) {
        String s = q.toLowerCase(Locale.ROOT);
        if (s.startsWith("snapshot")) {
            return 0;
        }
        if (s.startsWith("alpha")) {
            return 1;
        }
        if (s.startsWith("beta")) {
            return 2;
        }
        if (s.startsWith("m") || s.startsWith("milestone")) {
            return 3; // M2/M10：同 rank 后由 compareToken 按尾随数字比较
        }
        if (s.startsWith("rc") || s.startsWith("cr")) {
            return 4;
        }
        if (s.equals("sp") || s.equals("ga") || s.equals("final") || s.equals("release")) {
            return 6;
        }
        return 5; // 未知限定词排在正式版之前、rc 之后
    }

    public static boolean isPrerelease(String version) {
        return version != null && PRERELEASE.matcher(version).matches();
    }
}
