package com.santea.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiToolsService {
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b\\d{1,2}[\\/\\-]\\d{1,2}[\\/\\-]\\d{2,4}\\b");
    private static final Pattern VALUE_PATTERN = Pattern.compile("([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ \\-_]{1,55})\\s*(?:[:\\-]|=)?\\s*([<>]?[0-9]+(?:[.,][0-9]+)?)\\s*([A-Za-z%/µ²³.]+)?");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:[.,]\\d+)?");

    public DocumentScanResponse scanDocument(String pastedContent, File file) {
        String content = safe(pastedContent);
        String warning = "";

        if (content.isBlank() && file != null) {
            ExtractedFile extracted = extractContentFromUpload(file);
            content = extracted.content();
            warning = extracted.warning();
        }

        String normalized = normalizeExtractedText(content);
        if (normalized.isBlank()) {
            return new DocumentScanResponse(
                    "Aucun contenu fourni.",
                    "Aucune prediction possible sans contenu exploitable.",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    warning
            );
        }

        String analysisText = removeAdministrativeNoise(normalized);
        String source = analysisText.isBlank() ? normalized : analysisText;

        String summary = buildSmartSummary(source);
        Prediction prediction = buildHeuristicPrediction(source);
        List<String> keyPoints = buildKeyPoints(source);
        List<DocumentValue> values = extractValues(source);

        List<String> actions = List.of(
                "Verifier les valeurs anormales avec un professionnel de sante.",
                "Comparer avec vos resultats precedents si disponibles.",
                "Conserver ce document dans votre dossier medical personnel."
        );

        return new DocumentScanResponse(
                summary,
                prediction.analysisPrediction(),
                prediction.abnormalFindings(),
                keyPoints,
                values,
                actions,
                warning
        );
    }

    public NutritionPlanResponse generateNutritionPlan(String goal, String dietStyle, String allergies, int days) {
        int safeDays = Math.max(1, Math.min(14, days));
        String safeStyle = safe(dietStyle).isBlank() ? "standard" : safe(dietStyle).toLowerCase(Locale.ROOT);

        String baseBreakfast = "vegetarien".equals(safeStyle)
                ? "Porridge avoine + fruits + noix"
                : "Omelette legumes + pain complet";
        String baseLunch = "vegetarien".equals(safeStyle)
                ? "Bowl quinoa pois chiches legumes"
                : "Poulet grille + quinoa + salade";
        String baseDinner = "vegetarien".equals(safeStyle)
                ? "Soupe lentilles + legumes vapeur"
                : "Poisson au four + legumes + riz complet";

        List<NutritionDay> plan = new ArrayList<>();
        for (int i = 1; i <= safeDays; i++) {
            plan.add(new NutritionDay(
                    i,
                    baseBreakfast,
                    baseLunch,
                    baseDinner,
                    "Yaourt nature / fruit de saison"
            ));
        }

        List<String> tips = new ArrayList<>();
        tips.add("Hydratation: 1.5 a 2L d eau par jour.");
        tips.add("Prioriser les aliments peu transformes.");
        if (!safe(allergies).isBlank()) {
            tips.add("Eviter: " + safe(allergies));
        }

        String overview = String.format(
                "Plan nutrition %d jours orienté \"%s\" (%s).",
                safeDays,
                safe(goal).isBlank() ? "equilibre" : safe(goal),
                safeStyle
        );

        return new NutritionPlanResponse(overview, plan, tips);
    }

    public WorkoutPlanResponse generateWorkoutPlan(String goal, String level, int daysPerWeek, int minutes, String constraints) {
        int safeDays = Math.max(1, Math.min(7, daysPerWeek));
        int safeMinutes = Math.max(10, Math.min(120, minutes));

        String[] weekDays = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};
        List<WorkoutSession> sessions = new ArrayList<>();

        for (int i = 0; i < safeDays; i++) {
            String focus = i % 2 == 0 ? "Cardio + mobilite" : "Renforcement global";
            List<String> steps = new ArrayList<>();
            steps.add("Echauffement 8 min");
            steps.add("Cardio + mobilite".equals(focus) ? "Cardio modere 20 min" : "Circuit force 20 min");
            steps.add("Retour au calme 5 min");
            sessions.add(new WorkoutSession(weekDays[i], focus, safeMinutes, steps));
        }

        List<String> safety = new ArrayList<>();
        safety.add("Respecter la technique avant l intensite.");
        safety.add("Arreter en cas de douleur aigue.");
        if (!safe(constraints).isBlank()) {
            safety.add("Adapter selon contraintes: " + safe(constraints));
        }

        String overview = String.format(
                "Programme %s (%s), %d sessions/semaine.",
                safe(goal).isBlank() ? "remise en forme" : safe(goal),
                safe(level).isBlank() ? "intermediaire" : safe(level),
                safeDays
        );

        return new WorkoutPlanResponse(overview, sessions, safety);
    }

    public ResultExplanationResponse explainResult(String testName, String value, String unit, String referenceRange) {
        String label = safe(testName).isBlank() ? "ce resultat" : safe(testName);
        String unitPart = safe(unit).isBlank() ? "" : " " + safe(unit);

        String statusLine = buildStatusLine(value, referenceRange);
        List<String> possibleMeaning = new ArrayList<>();
        if (!safe(statusLine).isBlank()) {
            possibleMeaning.add(statusLine);
        }
        if (!safe(referenceRange).isBlank()) {
            possibleMeaning.add("Comparer a l intervalle de reference: " + safe(referenceRange));
        }
        possibleMeaning.add("Une valeur isolee doit etre interpretee avec le contexte clinique et les symptomes.");

        List<String> nextSteps = new ArrayList<>();
        nextSteps.add("Montrer ce resultat a votre medecin traitant.");
        String statusLower = safe(statusLine).toLowerCase(Locale.ROOT);
        if (statusLower.contains("au-dessus") || statusLower.contains("en dessous")) {
            nextSteps.add("Verifier rapidement avec un professionnel si des symptomes sont presents.");
        }
        nextSteps.add("Refaire le test si recommande par un professionnel.");

        String plainExplanation = String.format(
                "%s est mesure a %s%s. Cette interpretation reste informative et ne remplace pas un avis medical.",
                label,
                safe(value).isBlank() ? "valeur non fournie" : safe(value),
                unitPart
        );

        return new ResultExplanationResponse(
                plainExplanation,
                possibleMeaning,
                nextSteps,
                "En cas de symptomes importants, consulter rapidement un professionnel de sante."
        );
    }

    private ExtractedFile extractContentFromUpload(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return new ExtractedFile("", "Fichier invalide.");
        }

        String name = safe(file.getName()).toLowerCase(Locale.ROOT);
        String ext = extensionOf(name);

        if (List.of("txt", "csv", "log", "md").contains(ext)) {
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String content = normalizeExtractedText(text);
                if (content.isBlank()) {
                    return new ExtractedFile("", "Le fichier texte est vide ou illisible.");
                }
                return new ExtractedFile(content, "");
            } catch (IOException exception) {
                return new ExtractedFile("", "Impossible de lire le fichier texte.");
            }
        }

        if ("pdf".equals(ext)) {
            String text = extractTextFromPdf(file);
            if (text.isBlank()) {
                return new ExtractedFile(
                        "",
                        "PDF detecte mais texte non extractible automatiquement. Importez un PDF texte ou collez le contenu manuellement."
                );
            }
            return new ExtractedFile(text, "");
        }

        return new ExtractedFile("", "Format non supporte. Utilisez .pdf, .txt, .csv, .log ou .md.");
    }

    private String extractTextFromPdf(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String raw = new String(bytes, StandardCharsets.ISO_8859_1);
            Matcher matcher = Pattern.compile("\\(([^\\)]{2,})\\)\\s*Tj", Pattern.DOTALL).matcher(raw);
            List<String> chunks = new ArrayList<>();
            while (matcher.find()) {
                String chunk = matcher.group(1);
                if (chunk != null && chunk.length() > 1) {
                    chunks.add(chunk.replace("\\\\n", "\n").replace("\\\\r", ""));
                }
            }
            return normalizeExtractedText(String.join("\n", chunks));
        } catch (IOException exception) {
            return "";
        }
    }

    private List<String> buildKeyPoints(String content) {
        Matcher dateMatcher = DATE_PATTERN.matcher(content);
        Set<String> dates = new LinkedHashSet<>();
        while (dateMatcher.find() && dates.size() < 5) {
            dates.add(dateMatcher.group());
        }

        List<DocumentValue> values = extractValues(content);
        List<String> keyPoints = new ArrayList<>();
        if (!dates.isEmpty()) {
            keyPoints.add("Dates detectees: " + String.join(", ", dates));
        }
        if (!values.isEmpty()) {
            keyPoints.add(values.size() + " mesures numeriques detectees.");
        }
        return keyPoints;
    }

    private List<DocumentValue> extractValues(String content) {
        Matcher matcher = VALUE_PATTERN.matcher(content);
        List<DocumentValue> values = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        while (matcher.find() && values.size() < 24) {
            String label = safe(matcher.group(1));
            String value = safe(matcher.group(2));
            String unit = safe(matcher.group(3));

            if (label.isBlank() || value.isBlank()) {
                continue;
            }

            String lower = label.toLowerCase(Locale.ROOT);
            if (label.length() < 2 || lower.matches("^(date|patient|nom|prenom|age|sexe)$")) {
                continue;
            }

            String key = (label + "|" + value + "|" + unit).toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }

            values.add(new DocumentValue(label, value, unit));
        }

        return values;
    }

    private Prediction buildHeuristicPrediction(String content) {
        String lower = safe(content).toLowerCase(Locale.ROOT);
        int riskScore = 0;

        if (lower.contains("eleve") || lower.contains("haut") || lower.contains("anormal")) {
            riskScore += 2;
        }
        if (lower.contains("critique") || lower.contains("urgent")) {
            riskScore += 3;
        }
        if (lower.contains("faible") || lower.contains("bas") || lower.contains("insuffisant")) {
            riskScore += 1;
        }

        List<String> abnormal = new ArrayList<>();
        if (lower.contains("glycem")) {
            abnormal.add("Verifier l equilibre glycemique selon les valeurs detectees.");
        }
        if (lower.contains("cholesterol") || lower.contains("ldl") || lower.contains("hdl")) {
            abnormal.add("Controle lipidique a surveiller si ecart des references.");
        }
        if (lower.contains("creat") || lower.contains("uree") || lower.contains("dfg")) {
            abnormal.add("Fonction renale a confirmer avec le medecin en cas d ecart.");
        }
        if (lower.contains("tsh") || lower.contains("thyro")) {
            abnormal.add("Bilan thyroidien a interpreter avec le contexte clinique.");
        }

        String prediction;
        if (riskScore >= 4) {
            prediction = "Profil global potentiellement perturbe. Une verification medicale rapide est recommandee.";
        } else if (riskScore >= 2) {
            prediction = "Quelques anomalies possibles. Interpretez ces resultats avec un professionnel de sante.";
        } else {
            prediction = "Profil global plutot rassurant selon les elements detectes, a confirmer avec vos references.";
        }

        return new Prediction(prediction, abnormal);
    }

    private String buildSmartSummary(String content) {
        String clean = safe(content).replaceAll("\\s+", " ").trim();
        if (clean.isBlank()) {
            return "Document biologique analyse.";
        }

        String first = clean.length() > 180 ? clean.substring(0, 180) : clean;
        String summary = "Resume medical automatique: " + first;
        if (!summary.endsWith(".")) {
            summary += ".";
        }

        return summary;
    }

    private String removeAdministrativeNoise(String content) {
        if (safe(content).isBlank()) {
            return "";
        }

        String[] lines = content.split("\\R");
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String trimmed = safe(line);
            if (trimmed.isBlank()) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("page ") || lower.startsWith("adresse") || lower.startsWith("nom:") || lower.startsWith("prenom:") || lower.startsWith("telephone")) {
                continue;
            }
            kept.add(trimmed);
        }
        return String.join("\n", kept);
    }

    private String normalizeExtractedText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\u0000', ' ')
                .replace('\r', '\n')
                .replaceAll("\\n{3,}", "\\n\\n")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
    }

    private String buildStatusLine(String value, String referenceRange) {
        Double resultValue = extractNumber(value);
        if (resultValue == null) {
            return "";
        }

        Range range = extractRangeBounds(referenceRange);
        if (range.min == null && range.max == null) {
            return "";
        }

        if (range.min != null && resultValue < range.min) {
            return String.format("La valeur est en dessous de la reference (%.2f < %.2f).", resultValue, range.min);
        }
        if (range.max != null && resultValue > range.max) {
            return String.format("La valeur est au-dessus de la reference (%.2f > %.2f).", resultValue, range.max);
        }
        if (range.min != null && range.max != null) {
            return String.format("La valeur semble dans l intervalle de reference (%.2f entre %.2f et %.2f).", resultValue, range.min, range.max);
        }

        return "";
    }

    private Double extractNumber(String text) {
        Matcher matcher = NUMBER_PATTERN.matcher(safe(text));
        if (!matcher.find()) {
            return null;
        }

        String normalized = matcher.group().replace(',', '.');
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Range extractRangeBounds(String referenceRange) {
        String source = safe(referenceRange);
        if (source.isBlank()) {
            return new Range(null, null);
        }

        Matcher matcher = NUMBER_PATTERN.matcher(source);
        List<Double> numbers = new ArrayList<>();
        while (matcher.find()) {
            String normalized = matcher.group().replace(',', '.');
            try {
                numbers.add(Double.parseDouble(normalized));
            } catch (NumberFormatException ignored) {
                // ignored intentionally
            }
        }

        if (numbers.isEmpty()) {
            return new Range(null, null);
        }

        if (numbers.size() == 1) {
            double one = numbers.get(0);
            String lower = source.toLowerCase(Locale.ROOT);
            if (lower.contains("<") || lower.contains("inferieur") || lower.contains("moins")) {
                return new Range(null, one);
            }
            if (lower.contains(">") || lower.contains("superieur") || lower.contains("plus")) {
                return new Range(one, null);
            }
            return new Range(null, null);
        }

        double a = numbers.get(0);
        double b = numbers.get(1);
        return new Range(Math.min(a, b), Math.max(a, b));
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record DocumentScanResponse(
            String summary,
            String analysisPrediction,
            List<String> abnormalFindings,
            List<String> keyPoints,
            List<DocumentValue> values,
            List<String> suggestedActions,
            String warning
    ) {
    }

    public record DocumentValue(String label, String value, String unit) {
    }

    public record NutritionPlanResponse(String overview, List<NutritionDay> days, List<String> tips) {
    }

    public record NutritionDay(int day, String breakfast, String lunch, String dinner, String snack) {
    }

    public record WorkoutPlanResponse(String overview, List<WorkoutSession> sessions, List<String> safety) {
    }

    public record WorkoutSession(String day, String focus, int durationMin, List<String> plan) {
    }

    public record ResultExplanationResponse(
            String plainExplanation,
            List<String> possibleMeaning,
            List<String> nextSteps,
            String warning
    ) {
    }

    private record ExtractedFile(String content, String warning) {
    }

    private record Prediction(String analysisPrediction, List<String> abnormalFindings) {
    }

    private static final class Range {
        private final Double min;
        private final Double max;

        private Range(Double min, Double max) {
            this.min = min;
            this.max = max;
        }
    }
}
