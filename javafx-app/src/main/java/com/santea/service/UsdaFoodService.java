package com.santea.service;

import com.santea.model.PlanRegime;

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
 * Lightweight USDA FoodData Central client.
 *
 * API URL is configured in code (same style as Jitsi setup).
 *
 * Environment:
 * - USDA_API_KEY (required to enable remote calls)
 */
public class UsdaFoodService {
    private static final String USDA_API_URL = "https://api.nal.usda.gov";
    private static final String USDA_SEARCH_ENDPOINT = USDA_API_URL + "/fdc/v1/foods/search";

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .build();

    public boolean isEnabled() {
        return !apiKey().isBlank();
    }

    public List<PlanRegime> suggestDietPlans(String goal, String dietStyle, String allergies, int days) {
        if (!isEnabled()) {
            return List.of();
        }

        String query = buildQuery(goal, dietStyle, allergies);
        String response = searchFoods(query, 12);
        if (response.isBlank()) {
            return List.of();
        }

        List<FoodItem> foods = parseFoods(response);
        if (foods.isEmpty()) {
            return List.of();
        }

        String[] meals = {"Breakfast", "Lunch", "Dinner"};
        int target = Math.max(1, Math.min(3, days));
        List<PlanRegime> plans = new ArrayList<>();

        for (int i = 0; i < target; i++) {
            FoodItem food = foods.get(i % foods.size());
            PlanRegime plan = new PlanRegime();
            plan.setId(Math.abs(UUID.randomUUID().hashCode()));
            plan.setTitre(meals[i] + " - " + trimTo(food.name(), 34));
            plan.setTypeRegime(dietStyle == null || dietStyle.isBlank() ? "balanced" : dietStyle.trim());
            plan.setObjectif(goal == null ? "" : goal.trim());
            plan.setRestrictions(allergies == null ? "" : allergies.trim());
            plan.setCaloriesJour(food.kcal() > 0 ? food.kcal() : 1900 + (i * 120));
            plan.setDescription("Suggestion USDA: " + food.name()
                + (food.kcal() > 0 ? " (≈ " + food.kcal() + " kcal / 100g)." : ".")
                + " Combinez avec des légumes et une source protéique adaptée.");
            plans.add(plan);
        }

        return plans;
    }

    private String searchFoods(String query, int pageSize) {
        try {
            String url = USDA_SEARCH_ENDPOINT
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&pageSize=" + Math.max(1, Math.min(25, pageSize))
                + "&api_key=" + URLEncoder.encode(apiKey(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
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

    private List<FoodItem> parseFoods(String json) {
        List<FoodItem> items = new ArrayList<>();
        String foodsArray = extractArrayByKey(json, "foods");
        if (foodsArray.isBlank()) {
            return items;
        }

        for (String object : splitTopLevelObjects(foodsArray)) {
            String name = jsonString(object, "description");
            if (name.isBlank()) {
                continue;
            }

            int kcal = extractCalories(object);
            items.add(new FoodItem(clean(name), kcal));

            if (items.size() >= 12) {
                break;
            }
        }
        return items;
    }

    private int extractCalories(String foodObjectJson) {
        if (foodObjectJson == null || foodObjectJson.isBlank()) {
            return 0;
        }

        Pattern pattern = Pattern.compile(
            "\\\"nutrientName\\\"\\s*:\\s*\\\"Energy\\\".*?\\\"value\\\"\\s*:\\s*([0-9]+(?:\\\\.[0-9]+)?)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(foodObjectJson);
        if (matcher.find()) {
            return (int) Math.round(Double.parseDouble(matcher.group(1)));
        }
        return 0;
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
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replace("\\/", "/").replace("\\\"", "\"");
    }

    private String buildQuery(String goal, String dietStyle, String allergies) {
        String qGoal = goal == null ? "" : goal.trim();
        String qDiet = dietStyle == null ? "" : dietStyle.trim();
        String qAllergies = allergies == null ? "" : allergies.trim();

        String query = (qDiet + " " + qGoal).trim();
        if (query.isBlank()) {
            query = "healthy balanced meal";
        }
        if (!qAllergies.isBlank()) {
            query += " " + qAllergies;
        }
        return query;
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
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

    private String apiKey() {
        String key = System.getenv("USDA_API_KEY");
        return key == null ? "" : key.trim();
    }

    private record FoodItem(String name, int kcal) {
    }
}