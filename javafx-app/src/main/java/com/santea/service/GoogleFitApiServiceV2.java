package com.santea.service;

import com.santea.model.GoogleFitAccount;
import com.santea.model.SanteQuotidienne;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service simplifié pour Google Fit API - récupère pas, calories et minutes actives.
 */
public class GoogleFitApiServiceV2 {
    private static final String GOOGLE_FIT_API_BASE = "https://www.googleapis.com/fitness/v1";
    private static final String DATASET_ENDPOINT = GOOGLE_FIT_API_BASE + "/users/me/dataset:aggregate";
    private static final int HTTP_TIMEOUT_SECONDS = 15;

    public record GoogleFitData(Double steps, Double calories, Double activeMinutes, Double weightKg, Double heightMeters) {}
    public record FitResult(boolean success, String message, GoogleFitData data) {}

    /**
     * Récupère: pas, calories et minutes d'activité pour une date donnée
     */
    public FitResult fetchDailyData(GoogleFitAccount googleAccount, LocalDate date) {
        if (googleAccount == null) {
            return new FitResult(false, "Compte Google Fit non configuré.", null);
        }

        String accessToken = googleAccount.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            return new FitResult(false, "Pas de token d'accès Google Fit.", null);
        }

        try {
            LocalDateTime startOfDay = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endOfDay = LocalDateTime.of(date, LocalTime.MAX);
            long startTimeMs = startOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTimeMs = endOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            // Requête simple pour PAS uniquement
            Double steps = fetchSingleMetric(accessToken, "com.google.step_count.delta", startTimeMs, endTimeMs);
            
            // Requête simple pour CALORIES uniquement
            Double calories = fetchSingleMetric(accessToken, "com.google.calories.expended", startTimeMs, endTimeMs);
            // Requête simple pour ACTIVE MINUTES uniquement
            Double activeMinutes = fetchSingleMetric(accessToken, "com.google.active_minutes", startTimeMs, endTimeMs);
            // Poids/taille: récupérés via aggregate dédié sur une longue période.
            Double weightKg = fetchLatestMetricViaAggregate(accessToken, "com.google.weight", startOfDay.minusYears(10), endOfDay);
            Double heightMeters = fetchLatestMetricViaAggregate(accessToken, "com.google.height", startOfDay.minusYears(10), endOfDay);

            System.err.println("[GoogleFitApiServiceV2] RÉSULTAT FINAL - Steps: " + steps + ", Calories: " + calories + ", Active: " + activeMinutes + ", Weight: " + weightKg + ", Height: " + heightMeters);

            String message = "Google Fit: ";
            if (steps != null && steps > 0) {
                message += (long)steps.doubleValue() + " pas";
            } else {
                message += "pas de pas";
            }
            if (calories != null && calories > 0) {
                message += ", " + (long)calories.doubleValue() + " kcal";
            }
            if (activeMinutes != null && activeMinutes > 0) {
                message += ", " + (long)activeMinutes.doubleValue() + " min actives";
            }

            GoogleFitData data = new GoogleFitData(steps, calories, activeMinutes, weightKg, heightMeters);
            return new FitResult(true, message, data);
        } catch (Exception e) {
            System.err.println("[GoogleFitApiServiceV2] Erreur: " + e.getMessage());
            e.printStackTrace();
            return new FitResult(false, "Erreur Google Fit: " + e.getMessage(), null);
        }
    }

    private Double fetchSingleMetric(String accessToken, String dataTypeName, long startTimeMs, long endTimeMs) {
        try {
            String requestBody = String.format("""
                {
                  "aggregateBy": [{
                    "dataTypeName": "%s"
                  }],
                  "bucketByTime": { "durationMillis": 86400000 },
                  "startTimeMillis": %d,
                  "endTimeMillis": %d
                }
                """, dataTypeName, startTimeMs, endTimeMs);

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
                System.err.println("[GoogleFitApiServiceV2] Token invalide/expiré pour " + dataTypeName);
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("[GoogleFitApiServiceV2] HTTP " + response.statusCode() + " pour " + dataTypeName);
                return null;
            }

            String json = response.body();
            // Une requête = un seul dataType. On somme simplement toutes les valeurs numériques retournées.
            double sum = 0.0;
            int count = 0;

            Pattern intValPattern = Pattern.compile("\"intVal\"\\s*:\\s*\"?(\\d+(?:\\.\\d+)?)\"?");
            Matcher intValMatcher = intValPattern.matcher(json);
            while (intValMatcher.find()) {
                sum += Double.parseDouble(intValMatcher.group(1));
                count++;
            }

            Pattern fpValPattern = Pattern.compile("\"fpVal\"\\s*:\\s*\"?(\\d+(?:\\.\\d+)?)\"?");
            Matcher fpValMatcher = fpValPattern.matcher(json);
            while (fpValMatcher.find()) {
                sum += Double.parseDouble(fpValMatcher.group(1));
                count++;
            }

            if (count == 0) {
                System.err.println("[GoogleFitApiServiceV2] Aucune donnée trouvée pour " + dataTypeName);
                return null;
            }

            System.err.println("[GoogleFitApiServiceV2] RÉSULTAT pour " + dataTypeName + ": " + sum + " (" + count + " valeurs)");
            return sum;
        } catch (Exception e) {
            System.err.println("[GoogleFitApiServiceV2] Erreur parsing " + dataTypeName + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Applique les données simples à un objet SanteQuotidienne
     */
    public void applyFitDataToHealth(GoogleFitData fitData, SanteQuotidienne health) {
        if (fitData == null || health == null) {
            return;
        }

        if (fitData.steps() != null && fitData.steps() >= 0) {
            health.setPas((int) Math.round(fitData.steps()));
            System.err.println("[GoogleFitApiServiceV2] Pas appliqués: " + health.getPas());
        }

        if (fitData.calories() != null && fitData.calories() >= 0) {
            health.setCalories((int) Math.round(fitData.calories()));
            System.err.println("[GoogleFitApiServiceV2] Calories appliquées: " + health.getCalories());
        }

        if (fitData.activeMinutes() != null && fitData.activeMinutes() >= 0) {
            health.setDureeActiviteMinutes((int) Math.round(fitData.activeMinutes()));
            System.err.println("[GoogleFitApiServiceV2] Minutes actives appliquées: " + health.getDureeActiviteMinutes());
        }

        if (fitData.weightKg() != null && fitData.weightKg() > 0) {
            health.setPoids(fitData.weightKg());
        }
        if (fitData.heightMeters() != null && fitData.heightMeters() > 0) {
            Double h = fitData.heightMeters();
            if (h > 3.0) {
                h = h / 100.0;
            }
            health.setTaille(h);
        }
    }

    private Double fetchLatestMetricViaAggregate(String accessToken, String dataTypeName, LocalDateTime start, LocalDateTime end) {
        try {
            long startMs = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endMs = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            String requestBody = String.format("""
                {
                  "aggregateBy": [{
                    "dataTypeName": "%s"
                  }],
                  "bucketByTime": { "durationMillis": 86400000 },
                  "startTimeMillis": %d,
                  "endTimeMillis": %d
                }
                """, dataTypeName, startMs, endMs);

            HttpRequest hReq = HttpRequest.newBuilder()
                    .uri(URI.create(DATASET_ENDPOINT))
                    .timeout(java.time.Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> hResp = HttpClient.newHttpClient().send(hReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (hResp.statusCode() < 200 || hResp.statusCode() >= 300) {
                return null;
            }

            return extractLatestPointValue(hResp.body());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double extractLatestPointValue(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Pattern pointPattern = Pattern.compile(
                    "\"endTimeNanos\"\\s*:\\s*\"?(\\d+)\"?.*?\"value\"\\s*:\\s*\\[(.*?)\\]",
                    Pattern.DOTALL
            );
            Matcher m = pointPattern.matcher(json);
            long bestEnd = Long.MIN_VALUE;
            Double bestValue = null;
            while (m.find()) {
                long end = Long.parseLong(m.group(1));
                String valueBlock = m.group(2);
                Double v = extractLastNumber(valueBlock, "fpVal");
                if (v == null) {
                    v = extractLastNumber(valueBlock, "intVal");
                }
                if (v != null && end >= bestEnd) {
                    bestEnd = end;
                    bestValue = v;
                }
            }
            return bestValue;
        } catch (Exception ignored) {
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
}
