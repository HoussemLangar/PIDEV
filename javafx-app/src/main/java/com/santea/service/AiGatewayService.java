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
import java.time.LocalDate;
import java.time.LocalTime;
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

public class AiGatewayService {
    private static final int TIMEOUT_SECONDS = 6;
    private static final Map<String, String> DOTENV_VALUES = loadDotEnv();

    private final HttpClient httpClient;

    public AiGatewayService() {
        this.httpClient = buildHttpClient();
    }

    public Optional<AppointmentIntent> extractAppointmentIntent(String text) {
        String endpoint = resolveEndpoint();
        if (endpoint.isBlank()) {
            return Optional.empty();
        }

        try {
            String payload = "{\"text\":\"" + escapeJson(text) + "\"}";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

            String token = getenv("AI_APPOINTMENT_INTENT_TOKEN");
            if (!token.isBlank()) {
                builder.header("X-Voice-Token", token);
            }

            HttpRequest request = builder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            AppointmentIntent intent = parseIntent(response.body());
            if (intent.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(intent);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public boolean isIntentEndpointConfigured() {
        return !resolveEndpoint().isBlank();
    }

    public boolean isStrictIntentMode() {
        return parseBoolean(getenv("AI_APPOINTMENT_INTENT_STRICT"));
    }

    private String resolveEndpoint() {
        String direct = getenv("AI_APPOINTMENT_INTENT_URL");
        if (!direct.isBlank()) {
            return direct;
        }
        String base = getenv("AI_GATEWAY_URL");
        if (base.isBlank()) {
            return "";
        }
        if (base.endsWith("/")) {
            return base + "appointments/intent";
        }
        return base + "/appointments/intent";
    }

    private HttpClient buildHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));

        if (parseBoolean(getenv("AI_APPOINTMENT_INTENT_INSECURE"))) {
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

    private AppointmentIntent parseIntent(String json) {
        String doctor = extractField(json, "doctor");
        String specialty = extractField(json, "specialite");
        if (specialty == null || specialty.isBlank()) {
            specialty = extractField(json, "specialty");
        }
        String dateStr = extractField(json, "date");
        String timeStr = extractField(json, "time");

        LocalDate date = parseDate(dateStr);
        LocalTime time = parseTime(timeStr);

        return new AppointmentIntent(doctor, specialty, date, time);
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

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            if (raw.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                String[] parts = raw.split("-");
                return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
            if (raw.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                String[] parts = raw.split("/");
                return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace('h', ':').replace('H', ':');
        if (normalized.matches("\\d{1,2}:\\d{2}")) {
            String[] parts = normalized.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                return LocalTime.of(h, m);
            }
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

    public record AppointmentIntent(
        String doctor,
        String specialty,
        LocalDate date,
        LocalTime time
    ) {
        public boolean isEmpty() {
            return (doctor == null || doctor.isBlank())
                && (specialty == null || specialty.isBlank())
                && date == null
                && time == null;
        }
    }
}
