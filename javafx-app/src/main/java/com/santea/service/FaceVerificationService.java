package com.santea.service;

import com.santea.config.DatabaseConfig;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FaceVerificationService {
    private static final int GRID_SIZE = 8;
    private static final String DESCRIPTOR_VERSION_PREFIX = "v2|";
    private static final double LEGACY_FACE_THRESHOLD = 25.0;
    private static final double V2_FACE_THRESHOLD = 0.55;

    private final DatabaseService databaseService;

    public FaceVerificationService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    }

    public FaceProcessResult registerFace(int userId, Image image) {
        if (userId <= 0) {
            return FaceProcessResult.failure("Utilisateur invalide.");
        }
        if (image == null) {
            return FaceProcessResult.failure("Image faciale manquante.");
        }
        if (!databaseService.canConnect()) {
            return FaceProcessResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        ensureTable();
        boolean hadExistingFace = hasRegisteredFace(userId);
        double[] descriptor = computeDescriptorV2(image);
        String descriptorJson = DESCRIPTOR_VERSION_PREFIX + toJson(descriptor);

        String sql = "INSERT INTO face_data(user_id, face_descriptor, created_at) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE face_descriptor = VALUES(face_descriptor), created_at = VALUES(created_at)";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);
            statement.setString(2, descriptorJson);
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
            if (hadExistingFace) {
                return FaceProcessResult.success("Visage mis a jour avec succes.", 0.0);
            }
            return FaceProcessResult.success("Visage enregistre avec succes.", 0.0);
        } catch (SQLException exception) {
            return FaceProcessResult.failure("Erreur enregistrement visage: " + exception.getMessage());
        }
    }

    public FaceProcessResult verifyFace(int userId, Image image) {
        if (userId <= 0) {
            return FaceProcessResult.failure("Utilisateur invalide.");
        }
        if (image == null) {
            return FaceProcessResult.failure("Image faciale manquante.");
        }
        if (!databaseService.canConnect()) {
            return FaceProcessResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        ensureTable();
        String stored = getStoredDescriptor(userId);
        if (stored == null || stored.isBlank()) {
            return FaceProcessResult.failure("Aucun visage enregistre. Veuillez d'abord enregistrer votre visage.");
        }

        boolean isV2Descriptor = stored.startsWith(DESCRIPTOR_VERSION_PREFIX);
        String rawDescriptor = isV2Descriptor ? stored.substring(DESCRIPTOR_VERSION_PREFIX.length()) : stored;

        double[] current = isV2Descriptor ? computeDescriptorV2(image) : computeLegacyDescriptor(image);
        double[] storedDescriptor = parseJsonDescriptor(rawDescriptor);
        double distance = calculateAverageAbsoluteDistance(storedDescriptor, current);
        double threshold = isV2Descriptor ? V2_FACE_THRESHOLD : LEGACY_FACE_THRESHOLD;
        double similarityPercent = similarityFromDistance(distance, threshold);

        if (!Double.isFinite(distance)) {
            return FaceProcessResult.failure("Empreinte faciale invalide. Re-enregistrez votre visage." );
        }

        if (distance <= threshold) {
            return FaceProcessResult.success(
                    "Reconnaissance faciale reussie. Similarite: "
                            + Math.round(similarityPercent * 10.0) / 10.0 + "%",
                    distance
            );
        }

        String retryHint = isV2Descriptor
                ? ""
                : " Essayez de re-enregistrer votre visage pour recalibrer Face ID.";

        return FaceProcessResult.failure(
                "Reconnaissance faciale echouee. Similarite: "
                        + Math.round(similarityPercent * 10.0) / 10.0 + "%" + retryHint
        );
    }

    private double similarityFromDistance(double distance, double threshold) {
        if (!Double.isFinite(distance)) {
            return 0.0;
        }
        double normalizationRange = Math.max(0.0001, threshold * 2.5);
        return Math.max(0.0, Math.min(100.0, 100.0 - (distance / normalizationRange * 100.0)));
    }

    public boolean hasRegisteredFace(int userId) {
        if (userId <= 0 || !databaseService.canConnect()) {
            return false;
        }

        ensureTable();
        String sql = "SELECT 1 FROM face_data WHERE user_id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    private String getStoredDescriptor(int userId) {
        String sql = "SELECT face_descriptor FROM face_data WHERE user_id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("face_descriptor");
                }
            }
        } catch (SQLException exception) {
            return null;
        }
        return null;
    }

    private void ensureTable() {
        String sql = "CREATE TABLE IF NOT EXISTS face_data ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "face_descriptor LONGTEXT NOT NULL,"
                + "created_at DATETIME NOT NULL,"
                + "user_id INT NOT NULL UNIQUE"
                + ")";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException ignored) {
        }
    }

    private double[] computeDescriptorV2(Image image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();

        if (reader == null || width <= 0 || height <= 0) {
            return new double[GRID_SIZE * GRID_SIZE];
        }

        // Focus on central facial zone to reduce background noise.
        int cropX = Math.max(0, (int) Math.round(width * 0.20));
        int cropY = Math.max(0, (int) Math.round(height * 0.15));
        int cropW = Math.max(1, Math.min(width - cropX, (int) Math.round(width * 0.60)));
        int cropH = Math.max(1, Math.min(height - cropY, (int) Math.round(height * 0.70)));

        double[] descriptor = new double[GRID_SIZE * GRID_SIZE];
        int featureIndex = 0;

        for (int gy = 0; gy < GRID_SIZE; gy++) {
            int startY = cropY + (gy * cropH) / GRID_SIZE;
            int endY = cropY + ((gy + 1) * cropH) / GRID_SIZE;
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int startX = cropX + (gx * cropW) / GRID_SIZE;
                int endX = cropX + ((gx + 1) * cropW) / GRID_SIZE;

                double sumLuma = 0.0;
                int count = 0;

                for (int y = startY; y < endY; y++) {
                    for (int x = startX; x < endX; x++) {
                        int argb = reader.getArgb(x, y);
                        double r = ((argb >> 16) & 0xFF) / 255.0;
                        double g = ((argb >> 8) & 0xFF) / 255.0;
                        double b = (argb & 0xFF) / 255.0;
                        double luma = (0.299 * r) + (0.587 * g) + (0.114 * b);
                        sumLuma += luma;
                        count++;
                    }
                }

                if (count > 0) {
                    descriptor[featureIndex++] = sumLuma / count;
                } else {
                    descriptor[featureIndex++] = 0.0;
                }
            }
        }

        normalizeDescriptor(descriptor);

        return descriptor;
    }

    private double[] computeLegacyDescriptor(Image image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();

        if (reader == null || width <= 0 || height <= 0) {
            return new double[GRID_SIZE * GRID_SIZE * 3];
        }

        double[] descriptor = new double[GRID_SIZE * GRID_SIZE * 3];
        int featureIndex = 0;

        for (int gy = 0; gy < GRID_SIZE; gy++) {
            int startY = (gy * height) / GRID_SIZE;
            int endY = ((gy + 1) * height) / GRID_SIZE;
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int startX = (gx * width) / GRID_SIZE;
                int endX = ((gx + 1) * width) / GRID_SIZE;

                double sumR = 0.0;
                double sumG = 0.0;
                double sumB = 0.0;
                int count = 0;

                for (int y = startY; y < endY; y++) {
                    for (int x = startX; x < endX; x++) {
                        int argb = reader.getArgb(x, y);
                        sumR += (argb >> 16) & 0xFF;
                        sumG += (argb >> 8) & 0xFF;
                        sumB += argb & 0xFF;
                        count++;
                    }
                }

                if (count > 0) {
                    descriptor[featureIndex++] = sumR / count;
                    descriptor[featureIndex++] = sumG / count;
                    descriptor[featureIndex++] = sumB / count;
                } else {
                    descriptor[featureIndex++] = 0.0;
                    descriptor[featureIndex++] = 0.0;
                    descriptor[featureIndex++] = 0.0;
                }
            }
        }

        return descriptor;
    }

    private void normalizeDescriptor(double[] descriptor) {
        if (descriptor == null || descriptor.length == 0) {
            return;
        }

        double mean = 0.0;
        for (double value : descriptor) {
            mean += value;
        }
        mean /= descriptor.length;

        double variance = 0.0;
        for (double value : descriptor) {
            double delta = value - mean;
            variance += delta * delta;
        }
        variance /= descriptor.length;
        double std = Math.sqrt(variance);
        if (std < 1e-9) {
            std = 1.0;
        }

        for (int i = 0; i < descriptor.length; i++) {
            double normalized = (descriptor[i] - mean) / std;
            descriptor[i] = Math.max(-3.0, Math.min(3.0, normalized));
        }
    }

    private double calculateAverageAbsoluteDistance(double[] first, double[] second) {
        if (first == null || second == null || first.length == 0 || first.length != second.length) {
            return Double.POSITIVE_INFINITY;
        }

        double sum = 0.0;
        for (int i = 0; i < first.length; i++) {
            sum += Math.abs(first[i] - second[i]);
        }
        return sum / first.length;
    }

    private String toJson(double[] values) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    private double[] parseJsonDescriptor(String json) {
        String normalized = json == null ? "" : json.trim();
        if (normalized.isBlank()) {
            return new double[0];
        }

        normalized = normalized.replace("[", "").replace("]", "");
        String[] chunks = normalized.split(",");
        List<Double> values = new ArrayList<>();
        for (String chunk : chunks) {
            String part = chunk.trim();
            if (part.isBlank()) {
                continue;
            }
            try {
                values.add(Double.parseDouble(part));
            } catch (NumberFormatException ignored) {
            }
        }

        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    public record FaceProcessResult(boolean success, String message, double distance) {
        public static FaceProcessResult success(String message, double distance) {
            return new FaceProcessResult(true, message, distance);
        }

        public static FaceProcessResult failure(String message) {
            return new FaceProcessResult(false, message, Double.POSITIVE_INFINITY);
        }
    }
}
