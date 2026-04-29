package com.santea.service;

import com.santea.model.PlanExercice;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight wger API client.
 */
public class WgerExerciseService {
    private static final String WGER_API_BASE_URL = "https://wger.de/api/v2";

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .build();

    public List<PlanExercice> suggestExercisePlans(String goal, String level, int daysPerWeek, int minutes) {
        String query = buildQuery(goal);
        String response = requestExercises(query, 20);
        if (response.isBlank()) {
            return List.of();
        }

        List<ExerciseItem> exercises = parseExercises(response);
        if (exercises.isEmpty()) {
            return List.of();
        }

        int target = Math.max(1, Math.min(7, daysPerWeek));
        List<PlanExercice> plans = new ArrayList<>();
        for (int i = 0; i < target; i++) {
            ExerciseItem exercise = exercises.get(i % exercises.size());

            PlanExercice plan = new PlanExercice();
            plan.setId(Math.abs(UUID.randomUUID().hashCode()));
            plan.setTitre("Session " + (i + 1) + " - " + trimTo(exercise.name(), 34));
            plan.setFrequence("Day " + (i + 1) + " / week");
            plan.setDureMinutes(Math.max(10, minutes));
            plan.setNiveau(level == null || level.isBlank() ? "intermediate" : level.trim());
            plan.setObjectifs(goal == null ? "" : goal.trim());
            plan.setDescription(exercise.description().isBlank()
                ? "Suggestion wger: exécution contrôlée, échauffement et retour au calme."
                : "Suggestion wger: " + trimTo(exercise.description(), 240));
            plans.add(plan);
        }
        return plans;
    }

    private String requestExercises(String query, int limit) {
        try {
            String endpoint = WGER_API_BASE_URL + "/exerciseinfo/?language=2&limit=" + Math.max(1, Math.min(50, limit))
                + "&offset=0";
            if (query != null && !query.isBlank()) {
                endpoint += "&search=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }
            return response.body() == null ? "" : response.body();
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<ExerciseItem> parseExercises(String json) {
        List<ExerciseItem> items = new ArrayList<>();
        String array = extractArrayByKey(json, "results");
        if (array.isBlank()) {
            return items;
        }

        for (String object : splitTopLevelObjects(array)) {
            String englishTranslation = extractEnglishTranslationObject(object);
            if (englishTranslation.isBlank()) {
                continue;
            }

            String name = jsonString(englishTranslation, "name");
            if (name.isBlank()) {
                continue;
            }
            String description = stripHtml(jsonString(englishTranslation, "description"));
            items.add(new ExerciseItem(clean(name), clean(description)));

            if (items.size() >= 20) {
                break;
            }
        }
        return items;
    }

    private String extractEnglishTranslationObject(String exerciseObject) {
        String translations = extractArrayByKey(exerciseObject, "translations");
        if (translations.isBlank()) {
            return "";
        }
        for (String translationObject : splitTopLevelObjects(translations)) {
            int language = jsonInt(translationObject, "language");
            if (language == 2) {
                return translationObject;
            }
        }
        return "";
    }

    private String extractArrayByKey(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        int keyPos = json.indexOf('"' + key + '"');
        if (keyPos < 0) {
            return "";
        }
        int arrayStart = json.indexOf('[', keyPos);
        if (arrayStart < 0) {
            return "";
        }
        int level = 0;
        for (int i = arrayStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '[') {
                level++;
            } else if (ch == ']') {
                level--;
                if (level == 0) {
                    return json.substring(arrayStart + 1, i);
                }
            }
        }
        return "";
    }

    private List<String> splitTopLevelObjects(String arrayContent) {
        List<String> objects = new ArrayList<>();
        if (arrayContent == null || arrayContent.isBlank()) {
            return objects;
        }

        int start = -1;
        int depth = 0;
        for (int i = 0; i < arrayContent.length(); i++) {
            char ch = arrayContent.charAt(i);
            if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(arrayContent.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private String jsonString(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1)
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\n", " ")
            .replace("\\t", " ");
    }

    private int jsonInt(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) {
            return -1;
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*([0-9]+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String stripHtml(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'");
        return normalized.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String trimTo(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)).trim() + "…";
    }

    private String buildQuery(String goal) {
        String cleanGoal = clean(goal).toLowerCase(Locale.ROOT);
        if (cleanGoal.isBlank()) {
            return "";
        }
        if (cleanGoal.contains("poids") || cleanGoal.contains("weight") || cleanGoal.contains("cardio")) {
            return "cardio";
        }
        if (cleanGoal.contains("muscle") || cleanGoal.contains("force") || cleanGoal.contains("strength")) {
            return "strength";
        }
        if (cleanGoal.contains("mobil") || cleanGoal.contains("souples") || cleanGoal.contains("flexibil")) {
            return "mobility";
        }
        if (cleanGoal.contains("endurance") || cleanGoal.contains("stamina")) {
            return "endurance";
        }
        return "fitness";
    }

    private record ExerciseItem(String name, String description) {
    }
}