package com.santea.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenMeteoService {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Duration TIMEOUT = Duration.ofSeconds(12);

    private OpenMeteoService() {
    }

    public static WeatherSnapshot getTodaySnapshot(double latitude, double longitude) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,wind_speed_10m"
                + "&timezone=auto";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Open-Meteo HTTP " + response.statusCode());
        }

        String json = response.body();
        Double temp = extractNumber(json, "\"temperature_2m\"\\s*:\\s*([-0-9.]+)");
        Double apparent = extractNumber(json, "\"apparent_temperature\"\\s*:\\s*([-0-9.]+)");
        Double humidity = extractNumber(json, "\"relative_humidity_2m\"\\s*:\\s*([-0-9.]+)");
        Double wind = extractNumber(json, "\"wind_speed_10m\"\\s*:\\s*([-0-9.]+)");

        if (temp == null) {
            throw new IllegalStateException("Open-Meteo: temperature absente");
        }
        return new WeatherSnapshot(temp, apparent, humidity, wind);
    }

    private static Double extractNumber(String json, String regex) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile(regex).matcher(json);
        if (!m.find()) {
            return null;
        }
        try {
            return Double.parseDouble(m.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    public record WeatherSnapshot(Double temperatureC, Double apparentTemperatureC, Double humidityPercent, Double windKmh) {
    }
}

