package com.santea.model;

public enum UserScoreSnapshotType {
    DAILY;

    public static UserScoreSnapshotType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        return switch (value.trim().toLowerCase()) {
            case "daily" -> DAILY;
            default -> DAILY;
        };
    }

    public String getValue() {
        return name().toLowerCase();
    }
}
