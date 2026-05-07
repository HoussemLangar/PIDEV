package com.santea.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Scoring sentiment commentaires : endpoint Roberta XML + fallback lexical
 * (symfony-app CommentSentimentScoringService).
 */
public class CommentSentimentScoringService {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(4);
    private static final Pattern LABEL_2 = Pattern.compile("label[_\\s-]?2", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_0 = Pattern.compile("label[_\\s-]?0", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_1 = Pattern.compile("label[_\\s-]?1", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    public SentimentAnalysis analyze(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return new SentimentAnalysis(50, "NEUTRAL", Double.valueOf(0.0), "empty");
        }

        SentimentAnalysis remote = analyzeWithRobertaXml(normalized);
        if (remote != null) {
            return remote;
        }
        return fallbackAnalyze(normalized);
    }

    /**
     * Comme CommentSentimentScoringService::toSignedScore (PHP) : confidence prioritaire, sinon score.
     */
    public double toSignedScore(SentimentAnalysis analysis) {
        if (analysis == null) {
            return 0.0;
        }
        String label = analysis.label() == null ? "NEUTRAL" : analysis.label().toUpperCase(Locale.ROOT).trim();

        double confidence;
        if (analysis.confidence() != null) {
            confidence = analysis.confidence();
        } else {
            confidence = deriveConfidenceFromScore(analysis.score());
        }

        confidence = Math.max(0.0, Math.min(1.0, confidence));

        if ("POSITIVE".equals(label)) {
            return confidence;
        }
        if ("NEGATIVE".equals(label)) {
            return -confidence;
        }
        return 0.0;
    }

    private static double deriveConfidenceFromScore(double scoreRaw) {
        double scoreFloat = scoreRaw;
        return scoreFloat > 1.0 ? (scoreFloat / 100.0) : scoreFloat;
    }

    private SentimentAnalysis analyzeWithRobertaXml(String text) {
        String endpoint = getenv("ROBERTA_XML_ENDPOINT");
        if (endpoint.isEmpty()) {
            return null;
        }

        String model = getenv("ROBERTA_XML_MODEL");
        if (model.isEmpty()) {
            model = "roberta-xml";
        }

        String body = CommunityHttpJson.buildTextModelBody(text, model);
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
            return normalizeRobertaResponse(response.body());
        } catch (Exception e) {
            return null;
        }
    }

    private SentimentAnalysis normalizeRobertaResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String slice = CommunityHttpJson.unwrapNestedResponse(raw);

        Double score = CommunityHttpJson.findNumericField(slice, "score");
        String labelStr = CommunityHttpJson.findStringField(slice, "label");
        Double confidence = CommunityHttpJson.findNumericField(slice, "confidence");

        if (score == null && confidence != null) {
            score = confidence <= 1.0 ? confidence * 100.0 : confidence;
        }

        if (score == null || labelStr == null) {
            return null;
        }

        int scoreInt = (int) Math.max(0, Math.min(100, Math.round(score)));
        double confidenceValue = confidence != null ? confidence : (scoreInt / 100.0);
        confidenceValue = Math.max(0.0, Math.min(1.0, confidenceValue));

        return new SentimentAnalysis(scoreInt, normalizeLabel(labelStr), Double.valueOf(confidenceValue), "roberta_xml");
    }

    private SentimentAnalysis fallbackAnalyze(String text) {
        String[] positiveWords = {
                "excellent", "super", "merci", "utile", "clair", "parfait", "top", "bon", "bien", "aide",
                "bravo", "genial", "satisfait", "recommande", "helpful", "great", "love", "nice", "good",
        };
        String[] negativeWords = {
                "nul", "mauvais", "horrible", "arnaque", "faux", "inutile", "déçu", "decu", "lent",
                "bug", "erreur", "grave", "dangereux", "haine", "violence", "spam", "bad",
        };

        String lower = text.toLowerCase(Locale.ROOT);
        int positive = 0;
        int negative = 0;

        for (String word : positiveWords) {
            if (lower.contains(word)) {
                positive++;
            }
        }
        for (String word : negativeWords) {
            if (lower.contains(word)) {
                negative++;
            }
        }

        int raw = 50 + (positive * 12) - (negative * 12);
        int score = Math.max(0, Math.min(100, raw));
        String label = score >= 55 ? "POSITIVE" : (score <= 45 ? "NEGATIVE" : "NEUTRAL");
        double conf = Math.min(1.0, 0.45 + (Math.abs(positive - negative) * 0.1));

        return new SentimentAnalysis(score, label, Double.valueOf(conf), "fallback");
    }

    private String normalizeLabel(String label) {
        if (label == null) {
            return "NEUTRAL";
        }
        if (LABEL_2.matcher(label).find()) {
            return "POSITIVE";
        }
        if (LABEL_0.matcher(label).find()) {
            return "NEGATIVE";
        }
        if (LABEL_1.matcher(label).find()) {
            return "NEUTRAL";
        }
        String u = label.toUpperCase(Locale.ROOT);
        if (u.contains("POS")) {
            return "POSITIVE";
        }
        if (u.contains("NEG")) {
            return "NEGATIVE";
        }
        return "NEUTRAL";
    }

    private static String getenv(String key) {
        String v = System.getenv(key);
        return v == null ? "" : v.trim();
    }

    public record SentimentAnalysis(int score, String label, Double confidence, String source) {
    }
}
