package com.santea.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ML Document Classifier Client
 * Calls Python ML service to classify document types.
 * Falls back gracefully if service unavailable.
 */
public class DocumentClassificationClient {
    private static final String ML_SERVICE_URL = System.getenv("ML_CLASSIFIER_URL") != null 
        ? System.getenv("ML_CLASSIFIER_URL") 
        : "http://localhost:5001";
    
    private static final int TIMEOUT_SECONDS = 5;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build();

    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ML_SERVICE_URL + "/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }

    public ClassificationResult classify(String filename, String description) {
        return classify(filename, description, "");
    }

    public ClassificationResult classify(String filename, String description, String content) {
        if (!isAvailable()) {
            return new ClassificationResult(null, 0.0, null, "Service unavailable", false);
        }

        try {
            String payload = buildJsonPayload(filename, description, content);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ML_SERVICE_URL + "/classify"))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseResponse(response.body());
            } else {
                System.err.println("ML classification failed: " + response.statusCode());
                return new ClassificationResult(null, 0.0, null, "HTTP " + response.statusCode(), false);
            }
        } catch (Exception e) {
            System.err.println("ML classification error: " + e.getMessage());
            return new ClassificationResult(null, 0.0, null, e.getMessage(), false);
        }
    }

    private String buildJsonPayload(String filename, String description, String content) {
        String fn = escapeJson(filename);
        String desc = escapeJson(description);
        String body = escapeJson(content);
        return "{\"filename\":\"" + fn
            + "\",\"description\":\"" + desc
            + "\",\"content\":\"" + body + "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ");
    }

    private ClassificationResult parseResponse(String json) {
        try {
            String predictedType = extractField(json, "predicted_type");
            String confidenceStr = extractField(json, "confidence");
            double confidence = 0.0;
            String candidateSummary = extractCandidateSummary(json);
            
            if (confidenceStr != null && !confidenceStr.isBlank()) {
                try {
                    confidence = Double.parseDouble(confidenceStr);
                } catch (NumberFormatException ignored) {
                }
            }
            
            return new ClassificationResult(predictedType, confidence, candidateSummary, null, true);
        } catch (Exception e) {
            return new ClassificationResult(null, 0.0, null, "Parse error: " + e.getMessage(), false);
        }
    }

    private String extractField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"?([^,}\"]+)\"?[,}]");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String extractCandidateSummary(String json) {
        Pattern candidatesPattern = Pattern.compile("\\\"candidates\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher candidatesMatcher = candidatesPattern.matcher(json);
        if (!candidatesMatcher.find()) {
            return null;
        }

        String candidatesBlock = candidatesMatcher.group(1);
        Pattern candidatePattern = Pattern.compile("\\\"type\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"score\\\"\\s*:\\s*([0-9.]+)");
        Matcher candidateMatcher = candidatePattern.matcher(candidatesBlock);
        List<String> items = new ArrayList<>();
        while (candidateMatcher.find() && items.size() < 3) {
            String type = candidateMatcher.group(1);
            String score = candidateMatcher.group(2);
            try {
                items.add(type + " (" + String.format("%.0f%%", Double.parseDouble(score) * 100) + ")");
            } catch (NumberFormatException ignored) {
                items.add(type);
            }
        }
        return items.isEmpty() ? null : String.join(", ", items);
    }

    public static class ClassificationResult {
        public final String predictedType;
        public final double confidence;
        public final String candidateSummary;
        public final String error;
        public final boolean success;

        public ClassificationResult(String predictedType, double confidence, String candidateSummary, String error, boolean success) {
            this.predictedType = predictedType;
            this.confidence = confidence;
            this.candidateSummary = candidateSummary;
            this.error = error;
            this.success = success;
        }

        @Override
        public String toString() {
            return success 
                ? predictedType + " (" + String.format("%.0f%%", confidence * 100) + ")"
                : "Error: " + error;
        }
    }
}
