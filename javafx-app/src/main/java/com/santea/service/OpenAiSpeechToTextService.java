package com.santea.service;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenAiSpeechToTextService {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private OpenAiSpeechToTextService() {
    }

    public static String transcribePcmToText(byte[] pcmData, AudioFormat format) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY manquant.");
        }
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com";
        }

        byte[] wavBytes = pcmToWav(pcmData, format);

        String boundary = "----santea-" + UUID.randomUUID();
        byte[] body = buildMultipart(boundary, wavBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/audio/transcriptions"))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + safeBody(response.body()));
        }

        String text = extractJsonField(response.body(), "text");
        return text == null ? "" : text.trim();
    }

    private static byte[] pcmToWav(byte[] pcmData, AudioFormat format) throws Exception {
        if (pcmData == null) {
            pcmData = new byte[0];
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
             AudioInputStream ais = new AudioInputStream(bais, format, pcmData.length / format.getFrameSize());
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            javax.sound.sampled.AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, baos);
            return baos.toByteArray();
        }
    }

    private static byte[] buildMultipart(String boundary, byte[] wavBytes) {
        String CRLF = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "--" + boundary + CRLF);
        write(out, "Content-Disposition: form-data; name=\"model\"" + CRLF);
        write(out, CRLF);
        write(out, "whisper-1" + CRLF);

        write(out, "--" + boundary + CRLF);
        write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"" + CRLF);
        write(out, "Content-Type: audio/wav" + CRLF);
        write(out, CRLF);
        out.writeBytes(wavBytes);
        write(out, CRLF);

        write(out, "--" + boundary + "--" + CRLF);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String extractJsonField(String json, String fieldName) {
        if (json == null || json.isBlank() || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return null;
        }
        return unescapeJson(m.group(1));
    }

    private static String unescapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String safeBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}

