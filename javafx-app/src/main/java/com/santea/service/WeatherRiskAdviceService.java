package com.santea.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates general, non-diagnostic "risk / precaution" tips based on weather.
 */
public final class WeatherRiskAdviceService {
    private WeatherRiskAdviceService() {
    }

    public static List<String> buildRiskTips(Double temperatureC, Double humidityPercent) {
        List<String> tips = new ArrayList<>();
        if (temperatureC == null) {
            return tips;
        }

        if (temperatureC >= 30) {
            tips.add("⚠️ Chaleur: risque de déshydratation, fatigue, maux de tête (parfois migraines) et coup de chaleur.");
            tips.add("✅ Conseil: bois souvent en petites quantités, évite le soleil 11h–16h et privilégie un endroit frais.");
        } else if (temperatureC >= 26) {
            tips.add("⚠️ Temps chaud: fatigue et maux de tête possibles si tu bois peu.");
            tips.add("✅ Conseil: augmente l'eau et fais des pauses à l'ombre si tu marches/ fais du sport.");
        } else if (temperatureC >= 18) {
            tips.add("✅ Temps doux: garde une hydratation régulière, surtout si tu fais beaucoup de pas.");
        } else if (temperatureC >= 13) {
            tips.add("✅ Temps frais: hydrate-toi même si tu n'as pas soif; couvre-toi si tu sors longtemps.");
        } else if (temperatureC <= 8) {
            tips.add("⚠️ Froid: risque d'irritation des voies respiratoires (toux), raideurs musculaires et peau sèche.");
            tips.add("✅ Conseil: couvre-toi, échauffe-toi avant l'effort et hydrate-toi quand même.");
        } else { // 9–12
            tips.add("⚠️ Temps froid: gêne respiratoire et raideurs possibles, surtout si tu sors longtemps.");
            tips.add("✅ Conseil: protège-toi du froid et bois régulièrement (l'air froid déshydrate aussi).");
        }

        if (humidityPercent != null) {
            if (humidityPercent >= 75 && temperatureC >= 26) {
                tips.add("⚠️ Humidité élevée + chaleur: sensation de lourdeur, transpiration moins efficace → fatigue plus rapide.");
                tips.add("✅ Conseil: ralentis le rythme et bois plus souvent.");
            } else if (humidityPercent <= 30) {
                tips.add("⚠️ Air sec: irritation gorge/nez et maux de tête possibles.");
                tips.add("✅ Conseil: bois régulièrement (et humidifie l'air si possible).");
            }
        }

        return tips;
    }
}
