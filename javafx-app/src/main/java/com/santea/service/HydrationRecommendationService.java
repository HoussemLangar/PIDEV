package com.santea.service;

import java.util.ArrayList;
import java.util.List;

public final class HydrationRecommendationService {
    private HydrationRecommendationService() {
    }

    public static Recommendation recommend(Double temperatureC, Integer steps, Integer activityMinutes) {
        double baseLiters = 2.0; // generic baseline for adults
        double extraForHeat = 0.0;
        double extraForSteps = 0.0;
        double extraForActivity = 0.0;

        if (temperatureC != null) {
            if (temperatureC >= 30) {
                extraForHeat = 0.6;
            } else if (temperatureC >= 26) {
                extraForHeat = 0.4;
            } else if (temperatureC >= 22) {
                extraForHeat = 0.2;
            }
        }

        if (steps != null && steps > 0) {
            // Rough heuristic: +0.2L per 5000 steps (cap).
            extraForSteps = Math.min(0.6, (steps / 5000) * 0.2);
        }

        if (activityMinutes != null && activityMinutes > 0) {
            // Rough heuristic: +0.25L per 30 min (cap).
            extraForActivity = Math.min(0.8, (activityMinutes / 30) * 0.25);
        }

        double target = roundToNearest0_1(baseLiters + extraForHeat + extraForSteps + extraForActivity);

        List<String> reasons = new ArrayList<>();
        if (extraForHeat > 0) {
            reasons.add("chaleur");
        }
        if (extraForSteps > 0) {
            reasons.add("pas");
        }
        if (extraForActivity > 0) {
            reasons.add("activité");
        }

        return new Recommendation(target, reasons);
    }

    private static double roundToNearest0_1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record Recommendation(double targetLiters, List<String> reasons) {
    }
}

