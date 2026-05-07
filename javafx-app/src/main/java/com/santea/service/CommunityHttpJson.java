package com.santea.service;

/**
 * Encodage / parsing JSON minimal pour les appels modération / sentiment (sans dépendance JSON).
 */
final class CommunityHttpJson {
    private CommunityHttpJson() {
    }

    static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    static String buildTextModelBody(String text, String model) {
        return "{\"text\":\"" + escapeJson(text) + "\",\"model\":\"" + escapeJson(model) + "\"}";
    }

    static String unwrapNestedResponse(String raw) {
        if (raw == null) {
            return "";
        }
        String slice = raw.trim();
        for (int depth = 0; depth < 6; depth++) {
            String next = unwrapOnce(slice);
            if (next == null || next.equals(slice)) {
                break;
            }
            slice = next;
        }
        return slice;
    }

    private static String unwrapOnce(String slice) {
        String fromResult = extractFirstObjectAfterKey(slice, "result");
        if (fromResult != null) {
            return fromResult;
        }
        return extractFirstArrayElementObject(slice);
    }

    private static String extractFirstObjectAfterKey(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIdx = indexOfIgnoreCase(json, pattern);
        if (keyIdx < 0) {
            return null;
        }
        int brace = json.indexOf('{', keyIdx + pattern.length());
        if (brace < 0) {
            return null;
        }
        return extractBalancedObject(json, brace);
    }

    private static String extractFirstArrayElementObject(String json) {
        int arr = json.indexOf('[');
        if (arr < 0) {
            return null;
        }
        int brace = json.indexOf('{', arr);
        if (brace < 0) {
            return null;
        }
        return extractBalancedObject(json, brace);
    }

    private static String extractBalancedObject(String s, int openBraceIdx) {
        int depth = 0;
        for (int i = openBraceIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(openBraceIdx, i + 1);
                }
            }
        }
        return null;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) {
            return -1;
        }
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }

    static String findStringField(String json, String field) {
        if (json == null || field == null) {
            return null;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1).trim() : null;
    }

    static Double findNumericField(String json, String field) {
        if (json == null || field == null) {
            return null;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(json);
        if (!m.find()) {
            return null;
        }
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
