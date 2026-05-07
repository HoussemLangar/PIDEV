package com.santea.service;

import com.santea.model.GoogleFitAccount;
import com.santea.model.SanteQuotidienne;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service pour accéder à Google Fit API et récupérer les données de santé.
 * Documentation: https://developers.google.com/fit/rest/v1/get-started
 */
public class GoogleFitApiService {
    private static final String GOOGLE_FIT_API_BASE = "https://www.googleapis.com/fitness/v1";
    private static final String DATASET_ENDPOINT = GOOGLE_FIT_API_BASE + "/users/me/dataset:aggregate";
    private static final String GOOGLE_REFRESH_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final int HTTP_TIMEOUT_SECONDS = 15;

    public record GoogleFitData(
            Double steps,
            Double calories,
            Double heartRate,
            Double sleepMinutes,
            Double distanceMeters,
            Double activeMinutes,
            Double weightKg,
            Double heightMeters
    ) {}

    public record FitResult(boolean success, String message, GoogleFitData data) {}

    /**
     * Récupère les données de Google Fit pour une date donnée.
     *
     * @param googleAccount Les informations du compte Google Fit
     * @param date La date pour laquelle récupérer les données
     * @return Les données de fitness ou un message d'erreur
     */
    public FitResult fetchDailyData(GoogleFitAccount googleAccount, LocalDate date) {
        if (googleAccount == null) {
            return new FitResult(false, "Compte Google Fit non configuré.", null);
        }

        String accessToken = googleAccount.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            return new FitResult(false, "Pas de token d'accès Google Fit.", null);
        }

        // Vérifier si le token a expiré et le renouveler si nécessaire
        if (isTokenExpired(googleAccount)) {
            String refreshResult = refreshAccessToken(googleAccount);
            if (refreshResult != null && !refreshResult.isBlank()) {
                googleAccount.setAccessToken(refreshResult);
            } else {
                return new FitResult(false, "Impossible de renouveler le token Google Fit.", null);
            }
        }

        try {
            // Préparer les timestamps Unix en millisecondes pour la date
            LocalDateTime startOfDay = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endOfDay = LocalDateTime.of(date, LocalTime.MAX);
            long startTimeMs = startOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTimeMs = endOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            // Construire le JSON pour la requête d'agrégation
            String requestBody = buildAggregateRequest(startTimeMs, endTimeMs);

            // Faire la requête HTTP vers Google Fit API
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DATASET_ENDPOINT))
                    .timeout(java.time.Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 401) {
                return new FitResult(false, "Token Google Fit invalide ou expiré. Reconnectez-vous.", null);
            } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorMsg = "Erreur Google Fit API (HTTP " + response.statusCode() + "): "
                        + sanitize(truncate(response.body(), 240));
                return new FitResult(false, errorMsg, null);
            }

            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return new FitResult(true, "Données Google Fit: aucune donnée récupérée.", null);
            }

            // Parser la réponse aggregate pour activite/sommeil/cardio
            GoogleFitData data = parseAggregateResponse(responseBody);
            // Poids/taille: lecture directe des data sources (plus fiable que l'aggregate)
            Double weightKg = fetchLatestBodyMetric(accessToken, startOfDay, endOfDay, "com.google.weight");
            Double heightMeters = fetchLatestBodyMetric(accessToken, startOfDay, endOfDay, "com.google.height");
            data = new GoogleFitData(
                    data.steps(),
                    data.calories(),
                    data.heartRate(),
                    data.sleepMinutes(),
                    data.distanceMeters(),
                    data.activeMinutes(),
                    weightKg,
                    heightMeters
            );
            String message = "Données importées depuis Google Fit: "
                    + (data.steps() != null ? (long) data.steps().doubleValue() + " pas, " : "")
                    + (data.calories() != null ? (long) data.calories().doubleValue() + " kcal, " : "")
                    + (data.sleepMinutes() != null ? (long) data.sleepMinutes().doubleValue() + " min sommeil" : "");

            return new FitResult(true, message.replaceAll(", $", ""), data);
        } catch (Exception e) {
            return new FitResult(false, "Erreur lors de l'accès à Google Fit: " + sanitize(e.getMessage()), null);
        }
    }

    /**
     * Applique les données Google Fit à un objet SanteQuotidienne.
     */
    public void applyFitDataToHealth(GoogleFitData fitData, SanteQuotidienne health) {
        if (fitData == null || health == null) {
            return;
        }

        if (fitData.steps() != null && fitData.steps() > 0) {
            health.setPas((int) Math.round(fitData.steps()));
        }

        if (fitData.calories() != null && fitData.calories() > 0) {
            health.setCalories((int) Math.round(fitData.calories()));
        }

        if (fitData.sleepMinutes() != null && fitData.sleepMinutes() > 0) {
            health.setSommeil(fitData.sleepMinutes() / 60.0); // Convertir en heures
        }

        if (fitData.activeMinutes() != null && fitData.activeMinutes() > 0) {
            health.setDureeActiviteMinutes((int) Math.round(fitData.activeMinutes()));
        }

        if (fitData.heartRate() != null && fitData.heartRate() > 0) {
            // La tension artérielle est généralement stockée différemment,
            // mais on peut utiliser la fréquence cardiaque comme référence
            // heart_rate ≈ tension_diastolique (approximation simplifiée)
            if (fitData.heartRate() > 40 && fitData.heartRate() < 200) {
                health.setTensionArterielle(fitData.heartRate() / 10.0);
            }
        }

        if (fitData.distanceMeters() != null && fitData.distanceMeters() > 0) {
            // Distance en mètres, optionnel
        }

        if (fitData.weightKg() != null && fitData.weightKg() > 0) {
            Double weight = fitData.weightKg();
            // Correction d'unités: si le poids est anormalement faible, il est probablement en autre unité
            if (weight < 10) {
                // Probablement 0.01 kg (centigrammes) ou hectogrammes
                weight = weight * 100;
                System.err.println("[GoogleFitApiService] Poids corrigé: " + weight + " kg");
            }
            health.setPoids(weight);
        }

        if (fitData.heightMeters() != null && fitData.heightMeters() > 0) {
            Double height = fitData.heightMeters();
            // Correction d'unités: si la taille est entre 10 et 300, c'est probablement en cm
            if (height > 10 && height < 300) {
                height = height / 100.0;
                System.err.println("[GoogleFitApiService] Taille corrigée: " + height + " m");
            }
            health.setTaille(height);
        }
    }

    // ===== Méthodes privées =====

    private String buildAggregateRequest(long startTimeMs, long endTimeMs) {
        return String.format("""
            {
              "aggregateBy": [
                {
                  "dataTypeName": "com.google.step_count.delta"
                },
                {
                  "dataTypeName": "com.google.calories.expended"
                },
                {
                  "dataTypeName": "com.google.active_minutes"
                }
              ],
              "bucketByTime": {
                "durationMillis": 86400000
              },
              "startTimeMillis": %d,
              "endTimeMillis": %d
            }
            """, startTimeMs, endTimeMs);
    }

    private GoogleFitData parseAggregateResponse(String json) {
        double steps = 0;
        double calories = 0.0;
        double activeMinutes = 0.0;
        Double sleepMinutes = null;

        try {
            System.err.println("[GoogleFitApiService] Parsing aggregate response...");
            
            // On cherche les buckets (un par jour)
            Pattern bucketPattern = Pattern.compile(
                    "\"bucket\"\\s*:\\s*\\[(.*?)\\]\\s*(?=,\\s*\"bucket\"|\\])",
                    Pattern.DOTALL
            );
            Matcher bucketMatcher = bucketPattern.matcher(json == null ? "" : json);

            int bucketCount = 0;
            while (bucketMatcher.find()) {
                String bucketContent = bucketMatcher.group(1);
                bucketCount++;
                System.err.println("[GoogleFitApiService] Bucket #" + bucketCount);
                
                // Dans chaque bucket, on cherche les datasets
                Pattern datasetPattern = Pattern.compile(
                        "\"dataset\"\\s*:\\s*\\[(.*?)\\]",
                        Pattern.DOTALL
                );
                Matcher datasetMatcher = datasetPattern.matcher(bucketContent);
                
                while (datasetMatcher.find()) {
                    String datasetContent = datasetMatcher.group(1);
                    
                    // Identifier le type de données
                    String dataType = extractJsonString(datasetContent, "dataTypeName");
                    System.err.println("[GoogleFitApiService] Data type: " + dataType);
                    
                    // Extraire les points pour ce dataset
                    Pattern pointPattern = Pattern.compile(
                            "\"value\"\\s*:\\s*\\[(.*?)\\]",
                            Pattern.DOTALL
                    );
                    Matcher pointMatcher = pointPattern.matcher(datasetContent);
                    
                    while (pointMatcher.find()) {
                        String valueBlock = pointMatcher.group(1);
                        Double fpVal = extractLastNumber(valueBlock, "fpVal");
                        Double intVal = extractLastNumber(valueBlock, "intVal");
                        Double value = (fpVal != null) ? fpVal : intVal;
                        
                        if (value != null) {
                            System.err.println("[GoogleFitApiService]  - " + dataType + " = " + value);
                            
                            if ("com.google.step_count.delta".equalsIgnoreCase(dataType)) {
                                steps += value;
                            } else if ("com.google.calories.expended".equalsIgnoreCase(dataType)) {
                                calories += value;
                            } else if ("com.google.active_minutes".equalsIgnoreCase(dataType)) {
                                activeMinutes += value;
                            }
                        }
                    }
                }
            }
            
            System.err.println("[GoogleFitApiService] Totaux - Steps: " + steps + ", Calories: " + calories + ", Active: " + activeMinutes);
        } catch (Exception e) {
            System.err.println("[GoogleFitApiService] Erreur parsing: " + e.getMessage());
            e.printStackTrace();
        }

        Double stepsVal = steps >= 0 ? steps : null;
        Double caloriesVal = calories >= 0 ? calories : null;
        Double activeMinutesVal = activeMinutes >= 0 ? activeMinutes : null;

        return new GoogleFitData(stepsVal, caloriesVal, null, sleepMinutes, null, activeMinutesVal, null, null);
    }

    private Double extractSleepMinutes(String json) {
        try {
            // Chercher les durées de sommeil (en nanosecondes)
            Pattern pattern = Pattern.compile("\"com\\.google\\.sleep\\.segment\".*?\"endTimeNanos\"\\s*:\\s*\"(\\d+)\".*?\"startTimeNanos\"\\s*:\\s*\"(\\d+)\"");
            Matcher matcher = pattern.matcher(json);
            double totalMinutes = 0;
            int count = 0;

            while (matcher.find()) {
                long endNanos = Long.parseLong(matcher.group(1));
                long startNanos = Long.parseLong(matcher.group(2));
                long durationMs = (endNanos - startNanos) / 1_000_000;
                totalMinutes += durationMs / 60000.0;
                count++;
            }

            return count > 0 ? totalMinutes : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Double extractLastNumber(String section, String key) {
        if (section == null || section.isBlank()) {
            return null;
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([\\d.]+)");
        Matcher m = p.matcher(section);
        Double last = null;
        while (m.find()) {
            try {
                last = Double.parseDouble(m.group(1));
            } catch (Exception ignored) {
            }
        }
        return last;
    }

    private String refreshAccessToken(GoogleFitAccount googleAccount) {
        try {
            String refreshToken = googleAccount.getRefreshToken();
            if (refreshToken == null || refreshToken.isBlank()) {
                return null;
            }

            String clientId = resolveConfig("GOOGLE_CLIENT_ID", "GOOGLE_OAUTH_CLIENT_ID");
            String clientSecret = resolveConfig("GOOGLE_CLIENT_SECRET", "GOOGLE_OAUTH_CLIENT_SECRET");

            if (clientId == null || clientSecret == null) {
                return null;
            }

            String body = "client_id=" + clientId
                    + "&client_secret=" + clientSecret
                    + "&refresh_token=" + refreshToken
                    + "&grant_type=refresh_token";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_REFRESH_TOKEN_ENDPOINT))
                    .timeout(java.time.Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                return null;
            }

            String accessToken = extractJsonString(response.body(), "access_token");
            if (accessToken != null && !accessToken.isBlank()) {
                // Mettre à jour la date d'expiration (généralement 3600 secondes)
                googleAccount.setTokenExpiration(
                        LocalDateTime.now().plusSeconds(3600)
                );
            }
            return accessToken;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveConfig(String... keys) {
        if (keys == null || keys.length == 0) {
            return "";
        }

        for (String key : keys) {
            String fromEnv = System.getenv(key);
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv.trim();
            }
        }

        Path[] envPaths = {
                Path.of("..", "symfony-app", ".env.local"),
                Path.of("..", "symfony-app", ".env.dev.local"),
                Path.of("..", "symfony-app", ".env")
        };

        for (Path envPath : envPaths) {
            String value = readValueFromEnvFile(envPath, keys);
            if (!value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String readValueFromEnvFile(Path envPath, String... keys) {
        if (envPath == null || !Files.exists(envPath)) {
            return "";
        }

        try {
            for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int i = trimmed.indexOf('=');
                String k = trimmed.substring(0, i).trim();
                String v = trimmed.substring(i + 1).trim();
                for (String wanted : keys) {
                    if (k.equals(wanted)) {
                        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                            return v.substring(1, v.length() - 1);
                        }
                        return v;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private Double fetchLatestBodyMetric(String accessToken, LocalDateTime start, LocalDateTime end, String dataTypeName) {
        try {
            String dataSourceId = resolveDataSourceId(accessToken, dataTypeName);
            if (dataSourceId == null || dataSourceId.isBlank()) {
                return null;
            }

            long startNanos = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000L;
            long endNanos = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000L;
            String datasetId = startNanos + "-" + endNanos;
            String encodedDataSource = URLEncoder.encode(dataSourceId, StandardCharsets.UTF_8);
            String url = GOOGLE_FIT_API_BASE + "/users/me/dataSources/" + encodedDataSource + "/datasets/" + datasetId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Double dayValue = extractLatestPointValue(response.body());
                if (dayValue != null) {
                    return dayValue;
                }
            }

            // Fallback: si rien ce jour-la, prendre la derniere mesure connue sur une longue periode.
            LocalDateTime historyStart = end.minusYears(10);
            long historyStartNanos = historyStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1_000_000L;
            String historyDatasetId = historyStartNanos + "-" + endNanos;
            String historyUrl = GOOGLE_FIT_API_BASE + "/users/me/dataSources/" + encodedDataSource + "/datasets/" + historyDatasetId;

            HttpRequest historyRequest = HttpRequest.newBuilder()
                    .uri(URI.create(historyUrl))
                    .timeout(java.time.Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> historyResponse = HttpClient.newHttpClient()
                    .send(historyRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (historyResponse.statusCode() < 200 || historyResponse.statusCode() >= 300) {
                return null;
            }

            return extractLatestPointValue(historyResponse.body());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveDataSourceId(String accessToken, String dataTypeName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_FIT_API_BASE + "/users/me/dataSources"))
                    .timeout(java.time.Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            String json = response.body();
            if (json == null || json.isBlank()) {
                return null;
            }

            Pattern p = Pattern.compile(
                    "\"dataType\"\\s*:\\s*\\{\\s*\"name\"\\s*:\\s*\"" + Pattern.quote(dataTypeName)
                            + "\".*?\"dataStreamId\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.DOTALL
            );
            Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double extractLatestPointValue(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            System.err.println("[GoogleFitApiService] JSON de réponse métrique brut: " + truncate(json, 500));
            
            Pattern pointPattern = Pattern.compile(
                    "\"endTimeNanos\"\\s*:\\s*\"?(\\d+)\"?.*?\"value\"\\s*:\\s*\\[(.*?)\\]",
                    Pattern.DOTALL
            );
            Matcher m = pointPattern.matcher(json);
            long bestEnd = Long.MIN_VALUE;
            Double bestValue = null;
            int pointCount = 0;
            while (m.find()) {
                pointCount++;
                long end = Long.parseLong(m.group(1));
                String valueBlock = m.group(2);
                System.err.println("[GoogleFitApiService] Point #" + pointCount + " - Value block: " + truncate(valueBlock, 100));
                Double v = extractLastNumber(valueBlock, "fpVal");
                if (v == null) {
                    v = extractLastNumber(valueBlock, "intVal");
                }
                System.err.println("[GoogleFitApiService] Valeur extraite: " + v);
                if (v != null && end >= bestEnd) {
                    bestEnd = end;
                    bestValue = v;
                }
            }
            System.err.println("[GoogleFitApiService] Total points trouvés: " + pointCount + ", meilleure valeur: " + bestValue);
            return bestValue;
        } catch (Exception e) {
            System.err.println("[GoogleFitApiService] Erreur parsing: " + e.getMessage());
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private boolean isTokenExpired(GoogleFitAccount googleAccount) {
        if (googleAccount.getTokenExpiration() == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(googleAccount.getTokenExpiration());
    }

    private String extractJsonString(String json, String key) {
        try {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // Ignorer
        }
        return null;
    }

    private String sanitize(String message) {
        return message == null ? "erreur inconnue" : message.replaceAll("[^\\p{L}\\p{N}\\s.:,-]", "");
    }
}
