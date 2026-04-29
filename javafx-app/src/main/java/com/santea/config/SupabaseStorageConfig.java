package com.santea.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupabaseStorageConfig {
    // Same setup style as Jitsi: API URL defined in code.
    // Replace with your real project URL.
    private static final String SUPABASE_API_URL = "https://pvilrslrzesoacbctjie.supabase.co";
    private static final String SUPABASE_STORAGE_BUCKET = "documents";
    private static final Map<String, String> DOTENV_VALUES = loadDotEnv();

    private final boolean enabled;
    private final String url;
    private final String serviceRoleKey;
    private final String bucket;

    public SupabaseStorageConfig(boolean enabled, String url, String serviceRoleKey, String bucket) {
        this.enabled = enabled;
        this.url = url;
        this.serviceRoleKey = serviceRoleKey;
        this.bucket = bucket;
    }

    public static SupabaseStorageConfig fromEnvironment() {
        String key = env("SUPABASE_SERVICE_ROLE_KEY");
        String bucket = SUPABASE_STORAGE_BUCKET;

        String enabledRaw = env("SUPABASE_STORAGE_ENABLED");
        boolean enabledByFlag = "1".equals(enabledRaw) || "true".equalsIgnoreCase(enabledRaw) || "yes".equalsIgnoreCase(enabledRaw);
        boolean autoEnabled = !SUPABASE_API_URL.isBlank() && !SUPABASE_API_URL.contains("your-project-ref") && !key.isBlank();

        return new SupabaseStorageConfig(enabledByFlag || autoEnabled, normalizeUrl(SUPABASE_API_URL), key, bucket);
    }

    public boolean enabled() {
        return enabled;
    }

    public String url() {
        return url;
    }

    public String serviceRoleKey() {
        return serviceRoleKey;
    }

    public String bucket() {
        return bucket;
    }

    private static String env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = DOTENV_VALUES.getOrDefault(key, "");
        }
        return value == null ? "" : value.trim();
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<>();
        loadDotEnvFile(values, Path.of(".env"));
        loadDotEnvFile(values, Path.of(".env.local"));
        return values;
    }

    private static void loadDotEnvFile(Map<String, String> target, Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            List<String> lines = Files.readAllLines(path);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int idx = line.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String k = line.substring(0, idx).trim();
                String v = line.substring(idx + 1).trim();
                if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                    v = v.substring(1, v.length() - 1);
                }
                if (!k.isBlank()) {
                    target.put(k, v);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }
}