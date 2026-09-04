package com.mavensearch.eclipse.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只服务本项目固定几个上游响应的极简 JSON 解析器。失败抛 IllegalArgumentException。 */
public final class MiniJson {

    private MiniJson() {
    }

    public static Object parse(String json) {
        Parser p = new Parser(json);
        p.ws();
        Object v = p.value();
        p.ws();
        if (!p.eof()) {
            throw p.err("trailing content");
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(String json) {
        Object v = parse(json);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Bad JSON: expected object at 0");
        }
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(String json) {
        Object v = parse(json);
        if (!(v instanceof List)) {
            throw new IllegalArgumentException("Bad JSON: expected array at 0");
        }
        return (List<Object>) v;
    }

    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof String s ? s : null;
    }

    public static Double num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Double d ? d : null;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean eof() {
            return i >= s.length();
        }

        IllegalArgumentException err(String why) {
            return new IllegalArgumentException("Bad JSON at position " + i + ": " + why);
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object value() {
            if (eof()) {
                throw err("unexpected end");
            }
            return switch (s.charAt(i)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> lit("true", Boolean.TRUE);
                case 'f' -> lit("false", Boolean.FALSE);
                case 'n' -> lit("null", null);
                default -> number();
            };
        }

        Object lit(String word, Object v) {
            if (!s.startsWith(word, i)) {
                throw err("bad literal");
            }
            i += word.length();
            return v;
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // '{'
            ws();
            if (!eof() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                if (eof() || s.charAt(i) != '"') {
                    throw err("expected key");
                }
                String k = string();
                ws();
                if (eof() || s.charAt(i) != ':') {
                    throw err("expected ':'");
                }
                i++;
                ws();
                m.put(k, value());
                ws();
                if (eof()) {
                    throw err("unexpected end in object");
                }
                if (s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                throw err("expected ',' or '}'");
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; // '['
            ws();
            if (!eof() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (true) {
                ws();
                l.add(value());
                ws();
                if (eof()) {
                    throw err("unexpected end in array");
                }
                if (s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                throw err("expected ',' or ']'");
            }
        }

        String string() {
            StringBuilder b = new StringBuilder();
            i++; // '"'
            while (!eof()) {
                char c = s.charAt(i);
                if (c == '"') {
                    i++;
                    return b.toString();
                }
                if (c == '\\') {
                    i++;
                    if (eof()) {
                        throw err("bad escape");
                    }
                    char e = s.charAt(i);
                    b.append(switch (e) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case '/' -> '/';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> {
                            if (i + 4 >= s.length()) {
                                throw err("bad unicode escape");
                            }
                            String hex = s.substring(i + 1, i + 5);
                            i += 4;
                            yield (char) Integer.parseInt(hex, 16);
                        }
                        default -> throw err("bad escape '\\" + e + "'");
                    });
                    i++;
                } else {
                    b.append(c);
                    i++;
                }
            }
            throw err("unterminated string");
        }

        Double number() {
            int start = i;
            if (!eof() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                i++;
            }
            while (!eof() && "0123456789.eE-+".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            if (i == start) {
                throw err("expected value");
            }
            try {
                return Double.parseDouble(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw err("bad number");
            }
        }
    }
}
