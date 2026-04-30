package com.santea.service;

import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoskSpeechToTextService {
    private static final Object MODEL_LOCK = new Object();
    private static volatile Model model;

    private VoskSpeechToTextService() {
    }

    public static boolean isConfigured() {
        return resolveModelDir() != null;
    }

    public static String transcribePcmToText(byte[] pcmData, AudioFormat format) throws Exception {
        if (pcmData == null || pcmData.length == 0) {
            return "";
        }
        if (format == null) {
            throw new IllegalArgumentException("AudioFormat manquant.");
        }
        if (format.getSampleRate() <= 0) {
            throw new IllegalArgumentException("Sample rate invalide.");
        }

        Model m = ensureModelLoaded();
        try (Recognizer recognizer = new Recognizer(m, format.getSampleRate())) {
            // Feed all audio at once.
            recognizer.acceptWaveForm(pcmData, pcmData.length);
            String json = recognizer.getFinalResult();
            String text = extractJsonField(json, "text");
            return text == null ? "" : text.trim();
        }
    }

    private static Model ensureModelLoaded() throws Exception {
        if (model != null) {
            return model;
        }
        synchronized (MODEL_LOCK) {
            if (model != null) {
                return model;
            }
            File dir = resolveModelDir();
            if (dir == null) {
                throw new IllegalStateException("Modele Vosk introuvable. Definis VOSK_MODEL_PATH vers un dossier de modele.");
            }
            model = new Model(dir.getAbsolutePath());
            return model;
        }
    }

    private static File resolveModelDir() {
        String env = System.getenv("VOSK_MODEL_PATH");
        if (env != null && !env.isBlank()) {
            File d = new File(env.trim());
            if (d.isDirectory()) {
                File resolved = resolveModelRoot(d);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        // Common local defaults (repo/dev).
        File cwd = new File(System.getProperty("user.dir", "."));
        File candidate1 = new File(cwd, "models/vosk");
        if (candidate1.isDirectory()) {
            File resolved = resolveModelRoot(candidate1);
            if (resolved != null) {
                return resolved;
            }
        }
        File candidate2 = new File(cwd, "models/vosk-fr");
        if (candidate2.isDirectory()) {
            File resolved = resolveModelRoot(candidate2);
            if (resolved != null) {
                return resolved;
            }
        }
        File candidate3 = new File(cwd, "javafx-app/models/vosk");
        if (candidate3.isDirectory()) {
            File resolved = resolveModelRoot(candidate3);
            if (resolved != null) {
                return resolved;
            }
        }
        File candidate4 = new File(cwd, "javafx-app/models/vosk-fr");
        if (candidate4.isDirectory()) {
            File resolved = resolveModelRoot(candidate4);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static File resolveModelRoot(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }
        // A Vosk model root typically contains "conf" and "graph".
        if (new File(directory, "conf").isDirectory() && new File(directory, "graph").isDirectory()) {
            return directory;
        }
        File[] children = directory.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.getName().startsWith("vosk-model") || child.getName().startsWith("model")) {
                if (new File(child, "conf").isDirectory() && new File(child, "graph").isDirectory()) {
                    return child;
                }
            }
        }
        return null;
    }

    private static String extractJsonField(String json, String fieldName) {
        if (json == null || json.isBlank() || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return null;
        }
        return unescapeJson(m.group(1));
    }

    private static String unescapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
