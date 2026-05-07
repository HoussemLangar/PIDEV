package com.santea.service;

import com.santea.model.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppointmentVoiceIntentServer {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 5088;

    private static HttpServer server;
    private static boolean started;

    private AppointmentVoiceIntentServer() {
    }

    public static synchronized void startIfNeeded(AppointmentService appointmentService) {
        if (started || appointmentService == null) {
            return;
        }

        String host = getenv("VOICE_INTENT_HOST", DEFAULT_HOST);
        int port = parsePort(getenv("VOICE_INTENT_PORT", String.valueOf(DEFAULT_PORT)));

        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
        } catch (IOException ignored) {
            return;
        }

        server.createContext("/api/appointments/voice-intent", exchange -> handle(exchange, appointmentService));
        server.start();
        started = true;
    }

    private static void handle(HttpExchange exchange, AppointmentService appointmentService) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Methode non autorisee"));
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String text = firstNonBlank(
            extractJsonField(body, "text"),
            extractJsonField(body, "utterance"),
            extractJsonField(body, "command")
        );

        if (text.isBlank()) {
            sendJson(exchange, 400, jsonError("Champ text requis"));
            return;
        }

        User user = AuthSession.getCurrentUser();
        if (user == null) {
            sendJson(exchange, 401, jsonError("Non authentifie"));
            return;
        }

        AppointmentService.VoiceBookingResult result = appointmentService.bookFromVoice(user, text);
        if (!result.success()) {
            sendJson(exchange, 422, jsonError(result.message()));
            return;
        }

        String json = buildSuccessJson(result);
        sendJson(exchange, 200, json);
    }

    private static String buildSuccessJson(AppointmentService.VoiceBookingResult result) {
        String doctorLabel = result.doctor() == null ? "" : result.doctor().displayLabel();
        String doctorSpecialty = result.doctor() == null ? "" : safe(result.doctor().specialite());
        String date = result.date() == null ? "" : result.date().toString();
        String time = result.requestedTime() == null ? "" : result.requestedTime().toString();
        String slot = result.slot() == null ? "" : result.slot().label();

        return "{"
            + "\"success\":true,"
            + "\"message\":\"" + escapeJson(result.message()) + "\","
            + "\"appointmentId\":" + result.appointmentId() + ","
            + "\"doctor\":{"
            + "\"label\":\"" + escapeJson(doctorLabel) + "\","
            + "\"specialite\":\"" + escapeJson(doctorSpecialty) + "\""
            + "},"
            + "\"date\":\"" + escapeJson(date) + "\","
            + "\"time\":\"" + escapeJson(time) + "\","
            + "\"slot\":\"" + escapeJson(slot) + "\""
            + "}";
    }

    private static String jsonError(String message) {
        return "{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}";
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String extractJsonField(String json, String field) {
        if (json == null || field == null) {
            return "";
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String escapeJson(String value) {
        return safe(value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String getenv(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return DEFAULT_PORT;
        }
    }
}
