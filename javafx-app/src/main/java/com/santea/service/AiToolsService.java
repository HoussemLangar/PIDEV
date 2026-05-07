package com.santea.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiToolsService {
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b\\d{1,2}[\\/\\-]\\d{1,2}[\\/\\-]\\d{2,4}\\b");
    private static final Pattern VALUE_PATTERN = Pattern.compile("([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ \\-_]{1,55})\\s*(?:[:\\-]|=)?\\s*([<>]?[0-9]+(?:[.,][0-9]+)?)\\s*([A-Za-z%/µ²³.]+)?");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:[.,]\\d+)?");
    private static final Pattern PDF_TEXT_PATTERN = Pattern.compile("\\(([^\\)]{2,})\\)\\s*Tj", Pattern.DOTALL);
    private static final Pattern[] NOISE_PATTERNS = new Pattern[] {
        Pattern.compile("\\bpage\\s*:\\s*\\d+\\s*/\\s*\\d+\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("\\bnom\\s*:\\s*[^\\n\\r]{2,80}", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("\\bprenom\\s*:\\s*[^\\n\\r]{2,80}", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("\\bpr[eé]l[eè]vement\\s+fait\\s+le\\s*:\\s*[^\\n\\r]{2,80}", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("\\b(le|date)\\s*:\\s*\\d{1,2}[\\/\\-.]\\d{1,2}[\\/\\-.]\\d{2,4}(?:\\s*[aà]?\\s*\\d{1,2}:\\d{2})?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("\\b(?:ariana|tunis|sfax|sousse|nabeul)\\b\\s*:?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("\\b(?:tel|t[eé]l|fax|adresse|laboratoire)\\s*:?\\s*[^\\n\\r]{2,120}", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    };

    private static final String DOC_SYSTEM_PROMPT = "Tu es un assistant d extraction de document medical. Reponds uniquement en JSON: "
        + "{\"summary\":string,\"analysisPrediction\":string,\"abnormalFindings\":string[],\"keyPoints\":string[],"
        + "\"values\":[{\"label\":string,\"value\":string,\"unit\":string}],\"suggestedActions\":string[]}. "
        + "Regles: resume medical synthetique (2 phrases max), ne jamais recopier mot a mot le document, ignorer en-tetes administratifs (nom, page, adresse, heure), "
        + "extraire valeurs biologiques/constantes en priorite, normaliser les unites, ne pas inventer de donnees absentes. "
        + "Pour analysisPrediction: donner une interpretation prudente (profil global normal/perturbe) en te basant sur les intervalles presents.";
    private static final String NUTRITION_SYSTEM_PROMPT = "Tu es nutritionniste. Reponds uniquement en JSON: "
        + "{\"overview\":string,\"days\":[{\"day\":int,\"breakfast\":string,\"lunch\":string,\"dinner\":string,\"snack\":string}],\"tips\":string[]}.";
    private static final String WORKOUT_SYSTEM_PROMPT = "Tu es coach sportif. Reponds uniquement en JSON: "
        + "{\"overview\":string,\"sessions\":[{\"day\":string,\"focus\":string,\"durationMin\":int,\"plan\":string[]}],\"safety\":string[]}.";
    private static final String RESULT_SYSTEM_PROMPT = "Tu es assistant medical pedagogique. Reponds uniquement en JSON: "
        + "{\"plainExplanation\":string,\"possibleMeaning\":string[],\"nextSteps\":string[],\"warning\":string}. "
        + "Regles: explication simple, prudente, basee sur la valeur + intervalle fourni, pas de diagnostic definitif, actions concretes.";

    private final AiGatewayService aiGatewayService = new AiGatewayService();

    public DocumentScanResponse scanDocument(String pastedContent, File file) {
        String content = safe(pastedContent);
        String warning = "";

        if (content.isBlank() && file != null) {
            ExtractedFile extracted = extractContentFromUpload(file);
            content = extracted.content();
            warning = safe(extracted.warning());
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
        String warningNote = warning;
        DocumentScanResponse fallback = buildDocumentFallback(source, warningNote);

        String userPrompt = "Analyse ce document medical. Contenu OCR/text:\n\n" + source;
        return aiGatewayService.askForJson(DOC_SYSTEM_PROMPT, userPrompt)
            .map(ai -> mergeDocumentScan(ai, fallback, warningNote))
                .orElse(fallback);
    }

    public NutritionPlanResponse generateNutritionPlan(String goal, String dietStyle, String allergies, int days) {
        int safeDays = Math.max(1, Math.min(14, days));
        String safeStyle = safe(dietStyle).isBlank() ? "standard" : safe(dietStyle).toLowerCase(Locale.ROOT);
        NutritionPlanResponse fallback = buildNutritionFallback(goal, safeStyle, allergies, safeDays);

        String prompt = String.format(
            Locale.ROOT,
            "Objectif: %s\nStyle alimentaire: %s\nAllergies/intolerances: %s\nNombre de jours: %d",
            safe(goal),
            safeStyle,
            safe(allergies).isBlank() ? "aucune" : safe(allergies),
            safeDays
        );

        return aiGatewayService.askForJson(NUTRITION_SYSTEM_PROMPT, prompt)
            .map(ai -> mergeNutritionPlan(ai, fallback))
            .orElse(fallback);
    }

    public WorkoutPlanResponse generateWorkoutPlan(String goal, String level, int daysPerWeek, int minutes, String constraints) {
        int safeDays = Math.max(1, Math.min(7, daysPerWeek));
        int safeMinutes = Math.max(10, Math.min(120, minutes));
        String safeLevel = safe(level).isBlank() ? "intermediaire" : safe(level).toLowerCase(Locale.ROOT);
        WorkoutPlanResponse fallback = buildWorkoutFallback(goal, safeLevel, safeDays, safeMinutes, constraints);

        String prompt = String.format(
                Locale.ROOT,
                "Objectif: %s\nNiveau: %s\nJours/semaine: %d\nDuree/session: %d min\nContraintes: %s",
                safe(goal),
                safeLevel,
                safeDays,
                safeMinutes,
                safe(constraints).isBlank() ? "aucune" : safe(constraints)
        );

        return aiGatewayService.askForJson(WORKOUT_SYSTEM_PROMPT, prompt)
                .map(ai -> mergeWorkoutPlan(ai, fallback))
                .orElse(fallback);
    }

    public ResultExplanationResponse explainResult(String testName, String value, String unit, String referenceRange) {
        ResultExplanationResponse fallback = buildResultFallback(testName, value, unit, referenceRange);

        String prompt = String.format(
                Locale.ROOT,
                "Nom du test: %s\nValeur: %s %s\nIntervalle de reference: %s",
                safe(testName),
                safe(value),
                safe(unit),
                safe(referenceRange).isBlank() ? "non fourni" : safe(referenceRange)
        );

        return aiGatewayService.askForJson(RESULT_SYSTEM_PROMPT, prompt)
                .map(ai -> mergeResultExplanation(ai, fallback))
                .orElse(fallback);
    }

    private DocumentScanResponse mergeDocumentScan(Map<String, Object> ai, DocumentScanResponse fallback, String warning) {
        String summary = safe(JsonLite.asString(ai.get("summary")));
        if (summary.isBlank() || summary.length() > 380) {
            summary = fallback.summary();
        }

        String analysisPrediction = safe(JsonLite.asString(ai.get("analysisPrediction")));
        if (analysisPrediction.isBlank() || analysisPrediction.length() > 520) {
            analysisPrediction = fallback.analysisPrediction();
        }

        List<String> abnormalFindings = normalizeStringList(ai.get("abnormalFindings"));
        if (abnormalFindings.isEmpty()) {
            abnormalFindings = fallback.abnormalFindings();
        }

        List<String> keyPoints = normalizeStringList(ai.get("keyPoints"));
        if (keyPoints.isEmpty()) {
            keyPoints = fallback.keyPoints();
        }

        List<DocumentValue> values = normalizeValues(ai.get("values"));
        if (values.isEmpty()) {
            values = fallback.values();
        }

        List<String> actions = normalizeStringList(ai.get("suggestedActions"));
        if (actions.isEmpty()) {
            actions = fallback.suggestedActions();
        }

        return new DocumentScanResponse(summary, analysisPrediction, abnormalFindings, keyPoints, values, actions, warning);
    }

    private NutritionPlanResponse mergeNutritionPlan(Map<String, Object> ai, NutritionPlanResponse fallback) {
        String overview = safe(JsonLite.asString(ai.get("overview")));
        if (overview.isBlank()) {
            overview = fallback.overview();
        }

        List<NutritionDay> days = normalizeDays(ai.get("days"));
        if (days.isEmpty()) {
            days = fallback.days();
        }

        List<String> tips = normalizeStringList(ai.get("tips"));
        if (tips.isEmpty()) {
            tips = fallback.tips();
        }

        return new NutritionPlanResponse(overview, days, tips);
    }

    private WorkoutPlanResponse mergeWorkoutPlan(Map<String, Object> ai, WorkoutPlanResponse fallback) {
        String overview = safe(JsonLite.asString(ai.get("overview")));
        if (overview.isBlank()) {
            overview = fallback.overview();
        }

        List<WorkoutSession> sessions = normalizeSessions(ai.get("sessions"));
        if (sessions.isEmpty()) {
            sessions = fallback.sessions();
        }

        List<String> safety = normalizeStringList(ai.get("safety"));
        if (safety.isEmpty()) {
            safety = fallback.safety();
        }

        return new WorkoutPlanResponse(overview, sessions, safety);
    }

    private ResultExplanationResponse mergeResultExplanation(Map<String, Object> ai, ResultExplanationResponse fallback) {
        String plainExplanation = safe(JsonLite.asString(ai.get("plainExplanation")));
        if (plainExplanation.isBlank()) {
            plainExplanation = fallback.plainExplanation();
        }

        List<String> possibleMeaning = normalizeStringList(ai.get("possibleMeaning"));
        if (possibleMeaning.isEmpty()) {
            possibleMeaning = fallback.possibleMeaning();
        }

        List<String> nextSteps = normalizeStringList(ai.get("nextSteps"));
        if (nextSteps.isEmpty()) {
            nextSteps = fallback.nextSteps();
        }

        String warning = safe(JsonLite.asString(ai.get("warning")));
        if (warning.isBlank()) {
            warning = fallback.warning();
        }

        return new ResultExplanationResponse(plainExplanation, possibleMeaning, nextSteps, warning);
    }

    private DocumentScanResponse buildDocumentFallback(String content, String warning) {
        String summary = buildSmartSummary(content);
        Prediction prediction = buildHeuristicPrediction(content);
        List<DocumentValue> values = extractValues(content);
        List<String> keyPoints = buildKeyPoints(content, values);

        return new DocumentScanResponse(
                summary,
                prediction.analysisPrediction(),
                prediction.abnormalFindings(),
                keyPoints,
                values,
                List.of(
                        "Verifier les valeurs anormales avec un professionnel de sante.",
                        "Comparer avec vos resultats precedents si disponibles.",
                        "Conserver ce document dans votre dossier medical personnel."
                ),
                warning
        );
    }

    private NutritionPlanResponse buildNutritionFallback(String goal, String dietStyle, String allergies, int days) {
        String baseBreakfast = "vegetarien".equals(dietStyle)
                ? "Porridge avoine + fruits + noix"
                : "Omelette legumes + pain complet";
        String baseLunch = "vegetarien".equals(dietStyle)
                ? "Bowl quinoa pois chiches legumes"
                : "Poulet grille + quinoa + salade";
        String baseDinner = "vegetarien".equals(dietStyle)
                ? "Soupe lentilles + legumes vapeur"
                : "Poisson au four + legumes + riz complet";

        List<NutritionDay> plan = new ArrayList<>();
        for (int i = 1; i <= days; i++) {
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
                Locale.ROOT,
                "Plan nutrition %d jours oriente \"%s\" (%s).",
                days,
                safe(goal).isBlank() ? "equilibre" : safe(goal),
                dietStyle.isBlank() ? "standard" : dietStyle
        );

        return new NutritionPlanResponse(overview, plan, tips);
    }

    private WorkoutPlanResponse buildWorkoutFallback(String goal, String level, int daysPerWeek, int minutes, String constraints) {
        String[] weekDays = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};
        List<WorkoutSession> sessions = new ArrayList<>();

        for (int i = 0; i < daysPerWeek; i++) {
            String focus = i % 2 == 0 ? "Cardio + mobilite" : "Renforcement global";
            List<String> steps = new ArrayList<>();
            steps.add("Echauffement 8 min");
            steps.add("Cardio + mobilite".equals(focus) ? "Cardio modere 20 min" : "Circuit force 20 min");
            steps.add("Retour au calme 5 min");
            sessions.add(new WorkoutSession(weekDays[i], focus, minutes, steps));
        }

        List<String> safety = new ArrayList<>();
        safety.add("Respecter la technique avant l intensite.");
        safety.add("Arreter en cas de douleur aigue.");
        if (!safe(constraints).isBlank()) {
            safety.add("Adapter selon contraintes: " + safe(constraints));
        }

        String overview = String.format(
                Locale.ROOT,
                "Programme %s (%s), %d sessions/semaine.",
                safe(goal).isBlank() ? "remise en forme" : safe(goal),
                safe(level).isBlank() ? "intermediaire" : safe(level),
                daysPerWeek
        );

        return new WorkoutPlanResponse(overview, sessions, safety);
    }

    private ResultExplanationResponse buildResultFallback(String testName, String value, String unit, String referenceRange) {
        String label = safe(testName).isBlank() ? "ce resultat" : safe(testName);
        String unitPart = safe(unit).isBlank() ? "" : " " + safe(unit);

        String statusLine = buildStatusLine(value, referenceRange);
        List<String> possibleMeaning = new ArrayList<>();
        if (!statusLine.isBlank()) {
            possibleMeaning.add(statusLine);
        }
        if (!safe(referenceRange).isBlank()) {
            possibleMeaning.add("Comparer a l intervalle de reference: " + safe(referenceRange));
        }
        possibleMeaning.add("Une valeur isolee doit etre interpretee avec le contexte clinique et les symptomes.");

        List<String> nextSteps = new ArrayList<>();
        nextSteps.add("Montrer ce resultat a votre medecin traitant.");
        String statusLower = statusLine.toLowerCase(Locale.ROOT);
        if (statusLower.contains("au-dessus") || statusLower.contains("en dessous")) {
            nextSteps.add("Verifier rapidement avec un professionnel si des symptomes sont presents.");
        }
        nextSteps.add("Refaire le test si recommande par un professionnel.");

        String plainExplanation = String.format(
                Locale.ROOT,
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
        String pdfBoxText = extractTextFromPdfWithPdfBox(file);
        if (!pdfBoxText.isBlank()) {
            return pdfBoxText;
        }
        return extractTextFromPdfRaw(file);
    }

    private String extractTextFromPdfWithPdfBox(File file) {
        try (PDDocument document = PDDocument.load(file)) {
            if (document.isEncrypted()) {
                try {
                    document.setAllSecurityToBeRemoved(true);
                } catch (RuntimeException ignored) {
                    return "";
                }
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(2);
            String text = stripper.getText(document);
            return normalizeExtractedText(text);
        } catch (IOException ignored) {
            return "";
        }
    }

    private String extractTextFromPdfRaw(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String raw = new String(bytes, StandardCharsets.ISO_8859_1);
            Matcher matcher = PDF_TEXT_PATTERN.matcher(raw);
            List<String> chunks = new ArrayList<>();
            while (matcher.find()) {
                String chunk = matcher.group(1);
                if (chunk != null && chunk.length() > 1) {
                    chunks.add(chunk.replace("\\\\n", "\n").replace("\\\\r", ""));
                }
            }
            return normalizeExtractedText(String.join("\n", chunks));
        } catch (IOException ignored) {
            return "";
        }
    }

    private List<String> buildKeyPoints(String content, List<DocumentValue> values) {
        Matcher dateMatcher = DATE_PATTERN.matcher(content);
        Set<String> dates = new LinkedHashSet<>();
        while (dateMatcher.find() && dates.size() < 5) {
            dates.add(dateMatcher.group());
        }

        List<String> keyPoints = new ArrayList<>();
        if (!dates.isEmpty()) {
            keyPoints.add("Dates detectees: " + String.join(", ", dates));
        }
        if (values != null && !values.isEmpty()) {
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
            if (label.length() < 2 || lower.matches("^(date|patient|nom|prenom|age|sexe|page)$")) {
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

    private Double toFloat(String raw) {
        String normalized = safe(raw).replace(',', '.');
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String capitalize(String value) {
        String trimmed = safe(value);
        if (trimmed.isBlank()) {
            return "";
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }

    private Prediction buildHeuristicPrediction(String content) {
        Pattern pattern = Pattern.compile(
                "([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ \\-_]{2,55})\\s+([<>]?[0-9]+(?:[.,][0-9]+)?)\\s*([A-Za-z%/µ²³.]*)\\s*\\(?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*[-\\u2013]\\s*([0-9]+(?:[.,][0-9]+)?)\\s*\\)?",
                Pattern.UNICODE_CASE
        );

        Matcher matcher = pattern.matcher(content);
        List<String> findings = new ArrayList<>();
        int high = 0;
        int low = 0;
        int normal = 0;
        int scanned = 0;

        while (matcher.find() && scanned < 30) {
            scanned++;
            String label = safe(matcher.group(1));
            Double value = toFloat(matcher.group(2));
            String unit = safe(matcher.group(3));
            Double refMin = toFloat(matcher.group(4));
            Double refMax = toFloat(matcher.group(5));

            if (label.isBlank() || value == null || refMin == null || refMax == null) {
                continue;
            }

            if (label.toLowerCase(Locale.ROOT).matches("^(date|patient|nom|prenom|age|sexe|page)$")) {
                continue;
            }

            if (value < refMin) {
                low++;
                findings.add(String.format(
                        Locale.ROOT,
                        "%s: %.2f%s (en dessous de %.2f).",
                        label,
                        value,
                        unit.isBlank() ? "" : " " + unit,
                        refMin
                ));
            } else if (value > refMax) {
                high++;
                findings.add(String.format(
                        Locale.ROOT,
                        "%s: %.2f%s (au-dessus de %.2f).",
                        label,
                        value,
                        unit.isBlank() ? "" : " " + unit,
                        refMax
                ));
            } else {
                normal++;
            }
        }

        if (high == 0 && low == 0) {
            if (normal > 0) {
                return new Prediction(
                        "Prediction automatique: les parametres avec intervalles detectes semblent globalement dans les bornes usuelles. Interpretation a confirmer cliniquement.",
                        List.of()
                );
            }

            return new Prediction(
                    "Prediction automatique limitee: intervalles de reference insuffisants pour conclure un profil anormal ou normal.",
                    List.of()
            );
        }

        String profile = (high + low) >= 4
                ? "profil biologique possiblement perturbe"
                : "quelques ecarts biologiques ponctuels";

        String prediction = String.format(
                Locale.ROOT,
                "Prediction automatique: %s detectes (%d au-dessus, %d en dessous des bornes). Verification medicale recommandee.",
                profile,
                high,
                low
        );

        List<String> trimmed = findings.size() > 6 ? findings.subList(0, 6) : findings;
        return new Prediction(prediction, new ArrayList<>(trimmed));
    }

    private String buildSmartSummary(String content) {
        String flat = safe(content).replaceAll("\\s+", " ").trim();
        if (flat.isBlank()) {
            return "Document biologique analyse.";
        }

        Matcher matcher = VALUE_PATTERN.matcher(flat);
        List<String> labels = new ArrayList<>();
        int total = 0;

        while (matcher.find()) {
            total++;
            String label = safe(matcher.group(1));
            if (label.length() < 3) {
                continue;
            }
            if (label.toLowerCase(Locale.ROOT).matches("^(date|patient|nom|prenom|age|sexe|page)$")) {
                continue;
            }
            labels.add(capitalize(label));
            if (labels.size() >= 3) {
                break;
            }
        }

        if (!labels.isEmpty()) {
            int count = Math.max(1, total);
            return String.format(
                    Locale.ROOT,
                    "Document de biologie analyse avec %d mesures detectees. Parametres principaux: %s.",
                    count,
                    String.join(", ", labels)
            );
        }

        return "Document medical analyse. Aucune structure claire de valeurs biologiques na ete detectee automatiquement.";
    }

    private String removeAdministrativeNoise(String content) {
        String clean = safe(content);
        if (clean.isBlank()) {
            return "";
        }

        for (Pattern pattern : NOISE_PATTERNS) {
            clean = pattern.matcher(clean).replaceAll(" ");
        }

        clean = clean.replaceAll("\\s+", " ").trim();
        return clean;
    }

    private String normalizeExtractedText(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace('\u0000', ' ');
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private List<String> normalizeStringList(Object items) {
        List<Object> list = JsonLite.asList(items);
        if (list.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Object item : list) {
            String value = safe(JsonLite.asString(item));
            if (!value.isBlank()) {
                out.add(value);
            }
        }

        return new ArrayList<>(out);
    }

    private List<DocumentValue> normalizeValues(Object items) {
        List<Object> list = JsonLite.asList(items);
        if (list.isEmpty()) {
            return List.of();
        }

        List<DocumentValue> values = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = JsonLite.asMap(item);
            if (map.isEmpty()) {
                continue;
            }

            String label = safe(JsonLite.asString(map.get("label")));
            String value = safe(JsonLite.asString(map.get("value")));
            String unit = safe(JsonLite.asString(map.get("unit")));

            if (label.isBlank() || value.isBlank()) {
                continue;
            }

            values.add(new DocumentValue(label, value, unit));
        }

        return values;
    }

    private List<NutritionDay> normalizeDays(Object items) {
        List<Object> list = JsonLite.asList(items);
        if (list.isEmpty()) {
            return List.of();
        }

        List<NutritionDay> days = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = JsonLite.asMap(item);
            if (map.isEmpty()) {
                continue;
            }

            Integer day = JsonLite.asInt(map.get("day"));
            if (day == null || day <= 0) {
                continue;
            }

            days.add(new NutritionDay(
                    day,
                    safe(JsonLite.asString(map.get("breakfast"))),
                    safe(JsonLite.asString(map.get("lunch"))),
                    safe(JsonLite.asString(map.get("dinner"))),
                    safe(JsonLite.asString(map.get("snack")))
            ));
        }

        return days;
    }

    private List<WorkoutSession> normalizeSessions(Object items) {
        List<Object> list = JsonLite.asList(items);
        if (list.isEmpty()) {
            return List.of();
        }

        List<WorkoutSession> sessions = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = JsonLite.asMap(item);
            if (map.isEmpty()) {
                continue;
            }

            String day = safe(JsonLite.asString(map.get("day")));
            if (day.isBlank()) {
                continue;
            }

            int duration = 30;
            Integer durationValue = JsonLite.asInt(map.get("durationMin"));
            if (durationValue != null && durationValue > 0) {
                duration = durationValue;
            }

            String focus = safe(JsonLite.asString(map.get("focus")));
            List<String> plan = normalizeStringList(map.get("plan"));

            sessions.add(new WorkoutSession(day, focus, duration, plan));
        }

        return sessions;
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
