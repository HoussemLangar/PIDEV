package com.santea.service;

import com.santea.config.SupabaseStorageConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class SupabaseStorageClient {
    private final SupabaseStorageConfig config;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    public SupabaseStorageClient(SupabaseStorageConfig config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return config != null && config.enabled()
            && !config.url().isBlank()
            && !config.serviceRoleKey().isBlank()
            && !config.bucket().isBlank();
    }

    public boolean upload(String objectPath, byte[] fileContent, String mimeType) {
        if (!isEnabled() || objectPath == null || objectPath.isBlank() || fileContent == null) {
            return false;
        }
        try {
            String encodedPath = encodePath(objectPath);
            String url = config.url() + "/storage/v1/object/" + config.bucket() + "/" + encodedPath;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", config.serviceRoleKey())
                .header("Authorization", "Bearer " + config.serviceRoleKey())
                .header("x-upsert", "true")
                .header("Content-Type", mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(fileContent))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    public byte[] download(String objectPath) {
        if (!isEnabled() || objectPath == null || objectPath.isBlank()) {
            return null;
        }
        try {
            String encodedPath = encodePath(objectPath);
            String url = config.url() + "/storage/v1/object/" + config.bucket() + "/" + encodedPath;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", config.serviceRoleKey())
                .header("Authorization", "Bearer " + config.serviceRoleKey())
                .GET()
                .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public boolean delete(String objectPath) {
        if (!isEnabled() || objectPath == null || objectPath.isBlank()) {
            return false;
        }
        try {
            String encodedPath = encodePath(objectPath);
            String url = config.url() + "/storage/v1/object/" + config.bucket() + "/" + encodedPath;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("apikey", config.serviceRoleKey())
                .header("Authorization", "Bearer " + config.serviceRoleKey())
                .DELETE()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String encodePath(String objectPath) {
        String[] chunks = objectPath.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < chunks.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(URLEncoder.encode(chunks[i], StandardCharsets.UTF_8));
        }
        return out.toString();
    }
}