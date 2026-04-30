package com.santea.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Ollama client for local NLP chatbot (open-source).
 *
 * Default base URL: http://localhost:11434
 * Default model: llama3.2:3b
 *
 * Requirements (once per machine):
 * - Install Ollama
 * - Run: ollama serve
 * - Pull a model: ollama pull llama3.2:3b
 */
public class OllamaChatbotService {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(35);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String baseUrl;
    private final String model;
    private final String rasaBaseUrl;
    private final String senderId = "santea-user-" + UUID.randomUUID();
    private volatile boolean startupAttempted = false;

    public OllamaChatbotService() {
        String envUrl = System.getenv("OLLAMA_BASE_URL");
        this.baseUrl = (envUrl == null || envUrl.isBlank()) ? "http://localhost:11434" : envUrl.trim();
        String envModel = System.getenv("OLLAMA_MODEL");
        this.model = (envModel == null || envModel.isBlank()) ? "llama3.2:3b" : envModel.trim();
        String envRasaUrl = System.getenv("RASA_BASE_URL");
        this.rasaBaseUrl = (envRasaUrl == null || envRasaUrl.isBlank()) ? "http://localhost:5005" : envRasaUrl.trim();
    }

    public ChatResult ask(String message) {
        if (message == null || message.isBlank()) {
            return new ChatResult(false, List.of("Message vide."));
        }

        String trimmed = message.trim();

        // Prefer Ollama when available; fallback to Rasa local healthbot if it is running.
        if (ensureServerRunning()) {
            return askOllama(trimmed);
        }
        if (isRasaUp()) {
            return askRasa(trimmed);
        }
        // Offline fallback: rule-based general guidance (no diagnosis).
        return new ChatResult(true, LocalMedicalResponder.respond(trimmed));
    }

    private ChatResult askOllama(String trimmedMessage) {
        try {
            String payload = buildChatPayload(trimmedMessage);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ChatResult(false, List.of("Ollama indisponible (HTTP " + response.statusCode() + ")."));
            }

            List<String> lines = extractMessageContents(response.body());
            if (lines.isEmpty()) {
                lines = List.of("Je n'ai pas de reponse pour le moment.");
            }
            return new ChatResult(true, lines);
        } catch (Exception e) {
            return new ChatResult(false, List.of("Connexion Ollama echouee: " + safeMessage(e)));
        }
    }

    private ChatResult askRasa(String trimmedMessage) {
        try {
            String payload = "{"
                    + "\"sender\":\"" + escapeJson(senderId) + "\","
                    + "\"message\":\"" + escapeJson(trimmedMessage) + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rasaBaseUrl + "/webhooks/rest/webhook"))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ChatResult(false, List.of("Rasa indisponible (HTTP " + response.statusCode() + ")."));
            }

            List<String> lines = extractRasaTexts(response.body());
            if (lines.isEmpty()) {
                lines = List.of("Je n'ai pas de reponse pour le moment.");
            }
            return new ChatResult(true, lines);
        } catch (Exception e) {
            return new ChatResult(false, List.of("Connexion Rasa echouee: " + safeMessage(e)));
        }
    }

    public boolean isServerUpNow() {
        return isServerUp();
    }

    public synchronized boolean ensureServerRunning() {
        if (isServerUp()) {
            return true;
        }
        if (startupAttempted) {
            return waitUntilUp(10, 400);
        }
        startupAttempted = true;

        String startCmd = System.getenv("OLLAMA_START_CMD");
        if (startCmd == null || startCmd.isBlank()) {
            startCmd = "ollama serve";
        }

        try {
            new ProcessBuilder("bash", "-lc", startCmd)
                    .redirectErrorStream(true)
                    .start();
        } catch (Exception ignored) {
            // If we cannot start it, we'll just report "not available".
        }

        return waitUntilUp(20, 500);
    }

    private boolean isServerUp() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/version"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isRasaUp() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rasaBaseUrl + "/status"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean waitUntilUp(int attempts, long sleepMs) {
        for (int i = 0; i < attempts; i++) {
            if (isServerUp()) {
                return true;
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private String buildChatPayload(String userMessage) {
        // stream=false so we get a single JSON response.
        // We keep a small health disclaimer in the system prompt.
        String system = "Tu es un assistant sante pour une app de suivi. "
                + "Tu donnes des conseils generaux et tu rappelles que ca ne remplace pas un medecin. "
                + "Reponds en francais, clairement, sans dramatiser.";
        return "{"
                + "\"model\":\"" + escapeJson(model) + "\","
                + "\"stream\":false,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + escapeJson(system) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + escapeJson(userMessage) + "\"}"
                + "],"
                + "\"options\":{"
                + "\"temperature\":0.4"
                + "},"
                + "\"metadata\":{"
                + "\"sender\":\"" + escapeJson(senderId) + "\""
                + "}"
                + "}";
    }

    private List<String> extractMessageContents(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        // Ollama chat response: { message: { role: "...", content: "..." }, ... }
        Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher m = p.matcher(json);
        while (m.find()) {
            out.add(unescapeJson(m.group(1)));
        }
        return out;
    }

    private List<String> extractRasaTexts(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        // Rasa REST webhook response: [ { "recipient_id":"...", "text":"..." }, ... ]
        Pattern p = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher m = p.matcher(json);
        while (m.find()) {
            out.add(unescapeJson(m.group(1)));
        }
        return out;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private String safeMessage(Exception e) {
        if (e == null) {
            return "erreur inconnue";
        }
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return msg;
    }

    public record ChatResult(boolean success, List<String> messages) {
    }
}
