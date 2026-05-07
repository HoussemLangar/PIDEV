package com.santea.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modération automatique : modèle toxicité distant + fallback mots interdits
 * (symfony-app CommentModerationService).
 */
public class CommentModerationService {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(4);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    private final ForbiddenWordsFilterService fallbackFilter;
    private final Map<String, ModerationOutcome> cache = new ConcurrentHashMap<>();

    public CommentModerationService(ForbiddenWordsFilterService fallbackFilter) {
        this.fallbackFilter = fallbackFilter;
    }

    public ModerationOutcome moderate(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return new ModerationOutcome(true, 1.0, "EMPTY", "empty");
        }

        String cacheKey = sha1Hex(normalized.toLowerCase(Locale.ROOT));
        ModerationOutcome cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ModerationOutcome remote = moderateWithModel(normalized);
        if (remote != null) {
            cache.put(cacheKey, remote);
            return remote;
        }

        String cleaned = fallbackFilter.cleanText(normalized);
        boolean blocked = cleaned.isEmpty() || fallbackFilter.hasBadWord(cleaned);
        ModerationOutcome out = new ModerationOutcome(
                blocked,
                blocked ? 0.9 : 0.1,
                blocked ? "TOXIC_FALLBACK" : "CLEAN_FALLBACK",
                "fallback_words"
        );
        cache.put(cacheKey, out);
        return out;
    }

    public boolean isInappropriate(String text) {
        return moderate(text).blocked();
    }

    private ModerationOutcome moderateWithModel(String text) {
        String endpoint = getenv("TOXICITY_MODEL_ENDPOINT");
        if (endpoint.isEmpty()) {
            return null;
        }

        double threshold = parseThreshold(getenv("TOXICITY_THRESHOLD"), 0.60);
        String modelName = getenv("TOXICITY_MODEL_NAME");
        if (modelName.isEmpty()) {
            modelName = "unitary/toxic-bert";
        }

        String body = CommunityHttpJson.buildTextModelBody(text, modelName);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            LabelScore parsed = normalizeModelResponse(response.body());
            if (parsed == null) {
                return null;
            }
            boolean blocked = isToxicLabel(parsed.label()) && parsed.score() >= threshold;
            return new ModerationOutcome(blocked, parsed.score(), parsed.label(), "toxicity_model");
        } catch (Exception e) {
            return null;
        }
    }

    private LabelScore normalizeModelResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String slice = CommunityHttpJson.unwrapNestedResponse(raw);
        String labelStr = CommunityHttpJson.findStringField(slice, "label");
        Double score = CommunityHttpJson.findNumericField(slice, "score");
        if (score == null) {
            score = CommunityHttpJson.findNumericField(slice, "confidence");
        }
        if (labelStr == null || score == null) {
            return null;
        }
        String label = labelStr.toUpperCase(Locale.ROOT).trim();
        double s = score;
        if (s > 1.0) {
            s = s / 100.0;
        }
        s = Math.max(0.0, Math.min(1.0, s));
        return new LabelScore(label, s);
    }

    private boolean isToxicLabel(String label) {
        String u = label.toUpperCase(Locale.ROOT);
        if (u.contains("NON_TOXIC") || u.contains("NOT_TOXIC") || u.contains("CLEAN")) {
            return false;
        }
        String[] hints = {"TOXIC", "INSULT", "OBSCENE", "PROFAN", "HATE", "ABUSE", "OFFENSIVE", "LABEL_1"};
        for (String hint : hints) {
            if (u.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static double parseThreshold(String raw, double defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            double t = Double.parseDouble(raw.trim());
            return Math.max(0.0, Math.min(1.0, t));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String getenv(String key) {
        String v = System.getenv(key);
        return v == null ? "" : v.trim();
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private record LabelScore(String label, double score) {
    }

    public record ModerationOutcome(boolean blocked, double score, String label, String source) {
    }
}
