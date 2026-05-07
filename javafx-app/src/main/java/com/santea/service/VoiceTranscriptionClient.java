package com.santea.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class VoiceTranscriptionClient {
    private static final int TIMEOUT_SECONDS = 30;
    private static final Map<String, String> DOTENV_VALUES = loadDotEnv();

    private final HttpClient httpClient = buildHttpClient();

    public boolean isConfigured() {
        return !resolveEndpoint().isBlank();
    }

    public Optional<String> transcribe(byte[] wavBytes) {
        String endpoint = resolveEndpoint();
        if (endpoint.isBlank() || wavBytes == null || wavBytes.length == 0) {
            return Optional.empty();
        }

        String language = getenv("VOICE_TRANSCRIBE_LANGUAGE");
        String payload = buildPayload(wavBytes, language);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

            String token = getenv("VOICE_TRANSCRIBE_TOKEN");
            if (!token.isBlank()) {
                builder.header("X-Voice-Token", token);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            String text = extractField(response.body(), "text");
            if (text == null || text.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(text);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String buildPayload(byte[] wavBytes, String language) {
        String audio = Base64.getEncoder().encodeToString(wavBytes);
        if (language == null || language.isBlank()) {
            return "{\"audio\":\"" + escapeJson(audio) + "\"}";
        }
        return "{\"audio\":\"" + escapeJson(audio) + "\",\"language\":\"" + escapeJson(language) + "\"}";
    }

    private String resolveEndpoint() {
        String direct = getenv("VOICE_TRANSCRIBE_URL");
        return direct == null ? "" : direct.trim();
    }

    private HttpClient buildHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));

        if (parseBoolean(getenv("VOICE_TRANSCRIBE_INSECURE"))) {
            try {
                TrustManager[] trustAll = new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }};
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, trustAll, new SecureRandom());
                SSLParameters params = new SSLParameters();
                params.setEndpointIdentificationAlgorithm("");
                builder.sslContext(context).sslParameters(params);
            } catch (Exception ignored) {
            }
        }

        return builder.build();
    }

    private String extractField(String json, String fieldName) {
        if (json == null || fieldName == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String getenv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = DOTENV_VALUES.getOrDefault(key, "");
        }
        return value == null ? "" : value.trim();
    }

    private boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<>();
        loadDotEnvFile(values, Path.of(".env"));
        loadDotEnvFile(values, Path.of(".env.local"));
        return values;
    }

    private static void loadDotEnvFile(Map<String, String> target, Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            List<String> lines = Files.readAllLines(path);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int idx = line.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String k = line.substring(0, idx).trim();
                String v = line.substring(idx + 1).trim();
                if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                    v = v.substring(1, v.length() - 1);
                }
                if (!k.isBlank()) {
                    target.put(k, v);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ");
    }
}
