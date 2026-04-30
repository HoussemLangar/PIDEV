package com.santea.service;

import com.santea.model.SanteQuotidienne;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Heuristic "prediction" and trend insights based on user-recorded data.
 *
 * This is NOT a medical diagnosis and should be framed as general guidance.
 */
public final class HealthSymptomPredictionService {
    private HealthSymptomPredictionService() {
    }

    public static List<String> buildInsights(List<SymptomesQuotidiensService.SymptomeDailyRow> symptoms,
                                             List<SanteQuotidienne> healthEntries,
                                             LocalDate startDate,
                                             LocalDate endDate) {
        List<String> out = new ArrayList<>();
        int symptomCount = symptoms == null ? 0 : symptoms.size();
        int healthCount = healthEntries == null ? 0 : healthEntries.size();

        if (symptomCount == 0 && healthCount == 0) {
            out.add("Aucune donnée à analyser pour cette période.");
            out.add("Ajoute quelques symptômes et/ou une entrée Santé quotidienne pour obtenir des tendances.");
            return out;
        }

        out.add("Période analysée: " + safeDate(startDate) + " → " + safeDate(endDate) + ".");

        Aggregates agg = aggregateHealth(healthEntries);
        out.add("Indicateurs: sommeil moyen " + fmt1(agg.avgSleepHours) + "h | eau moyenne " + fmt1(agg.avgWaterLiters) + "L | pas moyens " + fmt0(agg.avgSteps) + ".");

        Map<String, Integer> symptomFreq = countSymptoms(symptoms);
        if (!symptomFreq.isEmpty()) {
            String top = symptomFreq.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(3)
                    .map(e -> e.getKey() + " ×" + e.getValue())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            out.add("Symptômes les plus notés: " + top + ".");
        }

        // Risk signals (heuristics)
        List<String> riskLines = new ArrayList<>();
        if (agg.avgWaterLiters != null && agg.avgWaterLiters < 1.5) {
            riskLines.add("Hydratation faible: risque de fatigue/maux de tête/constipation (pense à augmenter l'eau progressivement).");
        }
        if (agg.avgSleepHours != null && agg.avgSleepHours < 6.0) {
            riskLines.add("Sommeil bas: risque de baisse d'énergie, irritabilité et maux de tête.");
        }
        if (agg.avgSteps != null && agg.avgSteps > 12000 && (agg.avgWaterLiters == null || agg.avgWaterLiters < 2.0)) {
            riskLines.add("Activité élevée + eau modérée: risque de déshydratation (bois davantage les jours très actifs).");
        }

        // Symptom-specific links (heuristics)
        boolean hasHeadache = hasAnySymptom(symptomFreq, "migraine", "mal tete", "maux tete", "tete", "cefalee");
        if (hasHeadache && agg.avgSleepHours != null && agg.avgSleepHours < 6.5) {
            riskLines.add("Tendance possible: maux de tête plus probables quand le sommeil est bas.");
        }
        boolean hasAbdPain = hasAnySymptom(symptomFreq, "ventre", "abdominal", "crampe", "crampes", "douleur ventre");
        if (hasAbdPain && agg.avgWaterLiters != null && agg.avgWaterLiters < 1.5) {
            riskLines.add("Tendance possible: douleurs abdominales/crampes peuvent être aggravées par une hydratation faible.");
        }

        int maxIntensity = maxIntensity(symptoms);
        int riskScore = computeRiskScore(symptomCount, maxIntensity, agg);

        out.add("Score d'alerte (non médical): " + riskScore + "/100.");
        if (!riskLines.isEmpty()) {
            out.add("Points à surveiller:");
            riskLines.forEach(line -> out.add("- " + line));
        } else {
            out.add("Aucun signal fort détecté sur cette période. Continue le suivi pour des tendances plus fiables.");
        }

        out.add("Si tu as une douleur intense, des symptômes qui s'aggravent, ou si ça dure > 24–48h, prends avis médical.");
        return out;
    }

    private static String safeDate(LocalDate date) {
        return date == null ? "?" : date.toString();
    }

    private static Map<String, Integer> countSymptoms(List<SymptomesQuotidiensService.SymptomeDailyRow> symptoms) {
        Map<String, Integer> freq = new HashMap<>();
        if (symptoms == null) {
            return freq;
        }
        for (SymptomesQuotidiensService.SymptomeDailyRow row : symptoms) {
            String key = row == null ? "" : normalize(row.symptomName());
            if (key.isBlank()) {
                continue;
            }
            freq.merge(key, 1, Integer::sum);
        }
        return freq;
    }

    private static boolean hasAnySymptom(Map<String, Integer> freq, String... needles) {
        if (freq == null || freq.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            String n = normalize(needle);
            if (n.isBlank()) {
                continue;
            }
            for (String key : freq.keySet()) {
                if (key.contains(n)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int maxIntensity(List<SymptomesQuotidiensService.SymptomeDailyRow> symptoms) {
        if (symptoms == null || symptoms.isEmpty()) {
            return 0;
        }
        return symptoms.stream()
                .filter(r -> r != null)
                .map(SymptomesQuotidiensService.SymptomeDailyRow::intensity)
                .max(Comparator.naturalOrder())
                .orElse(0);
    }

    private static int computeRiskScore(int symptomCount, int maxIntensity, Aggregates agg) {
        int score = 0;
        score += Math.min(25, symptomCount * 3);
        score += Math.min(35, Math.max(0, maxIntensity - 3) * 5);

        if (agg.avgSleepHours != null) {
            if (agg.avgSleepHours < 5.0) {
                score += 20;
            } else if (agg.avgSleepHours < 6.0) {
                score += 12;
            } else if (agg.avgSleepHours < 7.0) {
                score += 6;
            }
        }
        if (agg.avgWaterLiters != null) {
            if (agg.avgWaterLiters < 1.0) {
                score += 15;
            } else if (agg.avgWaterLiters < 1.5) {
                score += 10;
            } else if (agg.avgWaterLiters < 2.0) {
                score += 5;
            }
        }

        return Math.max(0, Math.min(100, score));
    }

    private static Aggregates aggregateHealth(List<SanteQuotidienne> entries) {
        if (entries == null || entries.isEmpty()) {
            return new Aggregates(null, null, null);
        }
        double sleepSum = 0;
        int sleepCount = 0;
        double waterSum = 0;
        int waterCount = 0;
        double stepsSum = 0;
        int stepsCount = 0;

        for (SanteQuotidienne e : entries) {
            if (e == null) {
                continue;
            }
            if (e.getSommeil() != null) {
                sleepSum += e.getSommeil();
                sleepCount++;
            }
            if (e.getEauBue() != null) {
                waterSum += e.getEauBue();
                waterCount++;
            }
            if (e.getPas() != null) {
                stepsSum += e.getPas();
                stepsCount++;
            }
        }

        Double avgSleep = sleepCount == 0 ? null : (sleepSum / sleepCount);
        Double avgWater = waterCount == 0 ? null : (waterSum / waterCount);
        Double avgSteps = stepsCount == 0 ? null : (stepsSum / stepsCount);
        return new Aggregates(avgSleep, avgWater, avgSteps);
    }

    private static String fmt1(Double value) {
        if (value == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String fmt0(Double value) {
        if (value == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String normalize(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noAccents.replaceAll("\\s+", " ").trim();
    }

    private record Aggregates(Double avgSleepHours, Double avgWaterLiters, Double avgSteps) {
    }
}

