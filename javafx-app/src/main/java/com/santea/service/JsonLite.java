package com.santea.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonLite {
    private JsonLite() {
    }

    public static Object parse(String json) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            return new Parser(trimmed).parseValue();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    out.put(key, entry.getValue());
                }
            }
            return out;
        }
        return Map.of();
    }

    public static List<Object> asList(Object value) {
        if (value instanceof List<?> raw) {
            return new ArrayList<>(raw);
        }
        return List.of();
    }

    public static String asString(Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return null;
    }

    public static Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static final class Parser {
        private final String input;
        private int pos;

        private Parser(String input) {
            this.input = input;
        }

        private Object parseValue() {
            skipWhitespace();
            if (pos >= input.length()) {
                return null;
            }
            char c = input.charAt(pos);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't') {
                return parseLiteral("true", Boolean.TRUE);
            }
            if (c == 'f') {
                return parseLiteral("false", Boolean.FALSE);
            }
            if (c == 'n') {
                return parseLiteral("null", null);
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (pos < input.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                    continue;
                }
                if (peek('}')) {
                    pos++;
                    break;
                }
                throw new IllegalArgumentException("Invalid JSON object");
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (pos < input.length()) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                    continue;
                }
                if (peek(']')) {
                    pos++;
                    break;
                }
                throw new IllegalArgumentException("Invalid JSON array");
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < input.length()) {
                char c = input.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= input.length()) {
                        break;
                    }
                    char esc = input.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> sb.append(parseUnicode());
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private String parseUnicode() {
            if (pos + 4 > input.length()) {
                return "";
            }
            String hex = input.substring(pos, pos + 4);
            pos += 4;
            try {
                int code = Integer.parseInt(hex, 16);
                return String.valueOf((char) code);
            } catch (NumberFormatException ex) {
                return "";
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (input.startsWith(literal, pos)) {
                pos += literal.length();
                return value;
            }
            throw new IllegalArgumentException("Invalid JSON literal");
        }

        private Number parseNumber() {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            boolean hasDot = false;
            if (peek('.')) {
                hasDot = true;
                pos++;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            }
            if (peek('e') || peek('E')) {
                hasDot = true;
                pos++;
                if (peek('+') || peek('-')) {
                    pos++;
                }
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            }
            String raw = input.substring(start, pos);
            try {
                if (hasDot) {
                    return Double.parseDouble(raw);
                }
                return Long.parseLong(raw);
            } catch (NumberFormatException ex) {
                return 0;
            }
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private void expect(char expected) {
            if (pos >= input.length() || input.charAt(pos) != expected) {
                throw new IllegalArgumentException("Invalid JSON");
            }
            pos++;
        }

        private boolean peek(char expected) {
            return pos < input.length() && input.charAt(pos) == expected;
        }
    }
}
