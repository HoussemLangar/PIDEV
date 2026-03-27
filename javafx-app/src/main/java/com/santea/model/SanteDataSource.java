package com.santea.model;

public enum SanteDataSource {
    MANUEL,
    GOOGLE_FIT;

    public static SanteDataSource fromValue(String value) {
        if (value == null || value.isBlank()) {
            return MANUEL;
        }

        return switch (value.trim().toLowerCase()) {
            case "manuel" -> MANUEL;
            case "google_fit" -> GOOGLE_FIT;
            default -> MANUEL;
        };
    }

    public String getValue() {
        return switch (this) {
            case MANUEL -> "manuel";
            case GOOGLE_FIT -> "google_fit";
        };
    }
}
