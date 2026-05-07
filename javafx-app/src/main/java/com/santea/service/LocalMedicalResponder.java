package com.santea.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline, rule-based medical guidance responder.
 *
 * This is NOT a diagnostic engine. It provides general, low-risk guidance and
 * clear "when to seek care" instructions.
 */
public final class LocalMedicalResponder {
    private LocalMedicalResponder() {
    }

    public static List<String> respond(String userMessage) {
        String message = userMessage == null ? "" : userMessage.trim();
        if (message.isBlank()) {
            return List.of("Décris tes symptômes (où, depuis quand, intensité, fièvre, vomissements, diarrhée…).");
        }

        String normalized = normalize(message);
        List<String> out = new ArrayList<>();

        // Global safety banner (keep short; UI already shows a disclaimer too).
        out.add("Je peux te donner des conseils généraux, mais je ne remplace pas un médecin.");

        if (containsAny(normalized, "mal au ventre", "douleur ventre", "douleur abdominal", "ventre", "crampe ventre", "crampes ventre", "maux de ventre")) {
            out.addAll(abdominalPainAdvice());
            return out;
        }
        if (containsAny(normalized, "fievre", "temperature", "frisson", "frissons")) {
            out.addAll(feverAdvice());
            return out;
        }
        if (containsAny(normalized, "mal a la gorge", "gorge", "angine", "toux")) {
            out.addAll(soreThroatAdvice());
            return out;
        }
        if (containsAny(normalized, "mal a la tete", "maux de tete", "migraine", "tete")) {
            out.addAll(headacheAdvice());
            return out;
        }
        if (containsAny(normalized, "diarrhee", "diarrhe", "selles liquides")) {
            out.addAll(diarrheaAdvice());
            return out;
        }

        out.add("Peux-tu préciser : où exactement, depuis quand, intensité (1–10), et les symptômes associés (fièvre, nausées, vomissements, diarrhée, toux, douleurs urinaires…) ?");
        out.add("Signaux d’alerte : douleur très intense, malaise, difficulté à respirer, sang (vomissements/selles), confusion, ou aggravation rapide → urgence.");
        return out;
    }

    private static List<String> abdominalPainAdvice() {
        return List.of(
                "Pour un mal de ventre léger : repose-toi, hydrate-toi, mange léger (riz, banane, soupe) et évite gras/épices/alcool.",
                "Tu peux essayer une bouillotte tiède sur le ventre et noter ce que tu as mangé/les selles/les vomissements.",
                "Consulte rapidement si : douleur très forte, ventre dur, fièvre élevée, vomissements persistants, sang dans les selles, douleur localisée qui s’aggrave (ex: bas droit), grossesse possible, ou déshydratation.",
                "Si ça persiste > 24h (ou s’aggrave), prends avis médical.",
                "Causes fréquentes possibles (non exhaustif) : indigestion/gaz, gastro-entérite, constipation, règles/crampes, infection urinaire."
        );
    }

    private static List<String> feverAdvice() {
        return List.of(
                "Fièvre : repose-toi, hydrate-toi (eau, bouillon) et surveille la température.",
                "Consulte rapidement si : difficulté à respirer, douleur thoracique, confusion, raideur de nuque, éruption importante, déshydratation, ou fièvre élevée qui ne baisse pas.",
                "Si la fièvre dure > 48h (adulte) ou s’accompagne d’une aggravation → avis médical.",
                "Causes fréquentes possibles : infection virale (rhume/grippe), gastro-entérite, infection ORL, infection urinaire."
        );
    }

    private static List<String> soreThroatAdvice() {
        return List.of(
                "Mal de gorge : bois chaud/tiède, gargarismes à l’eau tiède salée, miel/citron si tu le tolères, et repose ta voix.",
                "Consulte rapidement si : difficulté à respirer/avaler, salivation importante, douleur très intense d’un côté, fièvre élevée, ou ganglions très douloureux.",
                "Si ça persiste > 2–3 jours ou s’aggrave → avis médical (ça peut être viral ou parfois bactérien).",
                "Causes possibles : rhume/virus, irritation (air sec), reflux, angine."
        );
    }

    private static List<String> headacheAdvice() {
        return List.of(
                "Mal de tête : repose-toi dans le calme, hydrate-toi, évite les écrans et vérifie si tu as dormi/assez bu.",
                "Consulte en urgence si : mal de tête brutal et inhabituel, troubles neurologiques (faiblesse, trouble parole/vision), raideur de nuque + fièvre, confusion, ou après un choc à la tête.",
                "Si ça persiste > 24–48h ou revient souvent → avis médical.",
                "Causes possibles : tension/stress, déshydratation, manque de sommeil, sinusite, migraine."
        );
    }

    private static List<String> diarrheaAdvice() {
        return List.of(
                "Diarrhée : hydrate-toi beaucoup (eau + sels de réhydratation si possible), mange léger et évite lait/gras/alcool.",
                "Consulte rapidement si : sang dans les selles, forte fièvre, douleurs abdominales intenses, signes de déshydratation, ou si tu es immunodéprimé(e).",
                "Si ça dure > 24–48h ou s’aggrave → avis médical.",
                "Causes possibles : gastro-entérite virale, intoxication alimentaire, intolérance alimentaire."
        );
    }

    private static boolean containsAny(String normalizedHaystack, String... needles) {
        for (String needle : needles) {
            if (needle == null || needle.isBlank()) {
                continue;
            }
            if (normalizedHaystack.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return noAccents.replaceAll("\\s+", " ").trim();
    }
}

