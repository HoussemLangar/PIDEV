package com.santea.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RasaChatbotService {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String senderId = "santea-user-" + UUID.randomUUID();
    private final String webhookUrl;
    private volatile boolean startupAttempted = false;
    private final Path projectRoot;

    public RasaChatbotService() {
        String fromEnv = System.getenv("RASA_WEBHOOK_URL");
        if (fromEnv == null || fromEnv.isBlank()) {
            this.webhookUrl = "http://localhost:5005/webhooks/rest/webhook";
        } else {
            this.webhookUrl = fromEnv.trim();
        }
        this.projectRoot = resolveProjectRoot();
    }

    public ChatResult ask(String message) {
        return askInternal(message, true);
    }

    public ChatResult askWithoutEnsuring(String message) {
        return askInternal(message, false);
    }

    public boolean isServerUpNow() {
        return isServerUp();
    }

    private ChatResult askInternal(String message, boolean ensureRunning) {
        if (message == null || message.isBlank()) {
            return new ChatResult(false, List.of("Message vide."));
        }

        try {
            if (ensureRunning) {
                ensureServerRunning();
            }
            String payload = "{"
                    + "\"sender\":\"" + escapeJson(senderId) + "\","
                    + "\"message\":\"" + escapeJson(message.trim()) + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ChatResult(false, List.of("Rasa indisponible (HTTP " + response.statusCode() + ")."));
            }

            List<String> texts = extractTexts(response.body());
            if (texts.isEmpty()) {
                texts = List.of("Je n'ai pas de reponse pour le moment.");
            }
            return new ChatResult(true, texts);
        } catch (Exception e) {
            return new ChatResult(false, List.of("Connexion Rasa echouee: " + safeMessage(e)));
        }
    }

    public synchronized boolean ensureServerRunning() {
        if (isServerUp()) {
            return true;
        }
        if (startupAttempted) {
            return waitUntilUp(20, 700);
        }
        startupAttempted = true;

        String startCmd = System.getenv("RASA_START_CMD");
        if (startCmd == null || startCmd.isBlank()) {
            startCmd = "";
        }

        // If the webhook points to localhost and no explicit command is provided, try to start
        // a dockerized Rasa service shipped with the repo (works on machines without Python/Rasa installed).
        boolean isLocal = webhookUrl.startsWith("http://localhost:5005") || webhookUrl.startsWith("http://127.0.0.1:5005");
        if (isLocal && startCmd.isBlank()) {
            if (startDockerCompose()) {
                // Training can take a bit on first boot, so wait longer.
                return waitUntilUp(360, 1000);
            }
        }

        // Fallback: run a custom start command (or try raw "rasa run" if the user provided it).
        if (startCmd.isBlank()) {
            startCmd = "rasa run --enable-api -p 5005 --cors \"*\"";
        }
        try {
            new ProcessBuilder("bash", "-lc", startCmd)
                    .redirectErrorStream(true)
                    .start();
        } catch (Exception e) {
            System.err.println("[RasaChatbotService] Start failed: " + safeMessage(e));
        }
        return waitUntilUp(40, 800);
    }

    private boolean startDockerCompose() {
        try {
            Path composeFile = projectRoot.resolve("ml-services/rasa-healthbot/docker-compose.yml");
            if (!Files.exists(composeFile)) {
                System.err.println("[RasaChatbotService] docker-compose.yml not found at: " + composeFile);
                return false;
            }

            String cmd = "docker compose -f " + shellEscape(composeFile.toString())
                    + " up -d --remove-orphans --force-recreate";
            Process process = new ProcessBuilder("bash", "-lc", cmd)
                    .directory(projectRoot.toFile())
                    .redirectErrorStream(true)
                    .start();

            // Don't block forever: if docker is not available for this user, fail fast with a useful log.
            try {
                boolean finished = process.waitFor(25, java.util.concurrent.TimeUnit.SECONDS);
                if (finished && process.exitValue() != 0) {
                    System.err.println("[RasaChatbotService] Docker compose failed (exit " + process.exitValue() + ").");
                    return false;
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return false;
            }
            return true;
        } catch (Exception e) {
            System.err.println("[RasaChatbotService] Docker start failed: " + safeMessage(e));
            return false;
        }
    }

    private boolean isServerUp() {
        try {
            String base = webhookUrl.replace("/webhooks/rest/webhook", "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/status"))
                    .timeout(Duration.ofSeconds(3))
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

    private List<String> extractTexts(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
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

    private Path resolveProjectRoot() {
        try {
            // The JavaFX app is typically started from the repo root. If not, we walk up a few levels
            // to find a marker folder.
            Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path current = cwd;
            for (int i = 0; i < 6 && current != null; i++) {
                if (Files.exists(current.resolve("ml-services")) && Files.exists(current.resolve("javafx-app"))) {
                    return current;
                }
                current = current.getParent();
            }
            return cwd;
        } catch (Exception ignored) {
            return Path.of(".").toAbsolutePath().normalize();
        }
    }

    private String shellEscape(String value) {
        // Minimal safe shell escaping for a single argument.
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public record ChatResult(boolean success, List<String> messages) {
    }
}
