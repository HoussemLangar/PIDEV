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
    private static final double FACE_THRESHOLD = 25.0;

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
        if (hasRegisteredFace(userId)) {
            return FaceProcessResult.failure("Visage deja enregistre. La mise a jour et la suppression ne sont pas autorisees.");
        }
        double[] descriptor = computeDescriptor(image);
        String descriptorJson = toJson(descriptor);

        String sql = "INSERT INTO face_data(user_id, face_descriptor, created_at) VALUES (?, ?, ?)";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);
            statement.setString(2, descriptorJson);
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
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

        double[] current = computeDescriptor(image);
        double[] storedDescriptor = parseJsonDescriptor(stored);
        double distance = calculateAverageAbsoluteDistance(storedDescriptor, current);

        if (distance < FACE_THRESHOLD) {
            return FaceProcessResult.success("Reconnaissance faciale reussie.", distance);
        }

        double similarityPercent = similarityFromDistance(distance);
        return FaceProcessResult.failure("Reconnaissance faciale echouee. Similarite: "
                + Math.round(similarityPercent * 10.0) / 10.0 + "%");
    }

    private double similarityFromDistance(double distance) {
        if (!Double.isFinite(distance)) {
            return 0.0;
        }
        return Math.max(0.0, 100.0 - (distance / FACE_THRESHOLD * 100.0));
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

    private double[] computeDescriptor(Image image) {
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
