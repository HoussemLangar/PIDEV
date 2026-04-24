package com.santea.service;

import com.santea.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SymptomesMedecinDashboardService {
    private final DatabaseService databaseService;

    public SymptomesMedecinDashboardService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    }

    public List<PatientOption> loadPatients() {
        String sql = "SELECT p.id AS patient_id, u.email, u.nom, u.prenom "
                + "FROM patients p "
                + "INNER JOIN users u ON u.id = p.user_id "
                + "ORDER BY u.nom ASC, u.prenom ASC, u.email ASC";

        List<PatientOption> rows = new ArrayList<>();
        rows.add(new PatientOption(null, "Tous les patients", ""));

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                Integer patientId = resultSet.getInt("patient_id");
                String fullName = buildDisplayName(
                        resultSet.getString("nom"),
                        resultSet.getString("prenom"),
                        patientId
                );
                String email = safe(resultSet.getString("email"));
                rows.add(new PatientOption(patientId, fullName, email));
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public DashboardData loadDashboard(Integer patientId) {
        List<DailyPoint> daily = loadDailyEvolution(patientId, 35);
        List<HeatmapCell> heatmap = buildHeatmap(daily, 8);
        List<PeakAlert> alerts = buildPeakAlerts(daily);

        return new DashboardData(daily, heatmap, alerts);
    }

    private List<DailyPoint> loadDailyEvolution(Integer patientId, int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        String sql = "SELECT DATE(sq.date_symptome) AS symptom_day, COUNT(*) AS day_count, AVG(sq.intensite) AS avg_intensity "
                + "FROM symptomes_quotidiens sq "
                + (patientId != null ? "WHERE sq.patient_id = ? AND sq.date_symptome >= ? " : "WHERE sq.date_symptome >= ? ")
                + "GROUP BY DATE(sq.date_symptome) "
                + "ORDER BY symptom_day ASC";

        List<DailyPoint> points = new ArrayList<>();

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            int index = 1;
            if (patientId != null) {
                statement.setInt(index++, patientId);
            }
            statement.setDate(index, Date.valueOf(start));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    LocalDate day = resultSet.getDate("symptom_day").toLocalDate();
                    int count = resultSet.getInt("day_count");
                    double avgIntensity = resultSet.getDouble("avg_intensity");
                    points.add(new DailyPoint(day, count, round2(avgIntensity)));
                }
            }
        } catch (SQLException ignored) {
        }

        return points;
    }

    private List<HeatmapCell> buildHeatmap(List<DailyPoint> daily, int weeks) {
        LocalDate firstWeek = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(weeks - 1L);

        Map<LocalDate, DailyPoint> byDate = new HashMap<>();
        for (DailyPoint point : daily) {
            byDate.put(point.day(), point);
        }

        List<HeatmapCell> cells = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            LocalDate weekStart = firstWeek.plusWeeks(w);
            for (int d = 0; d < 7; d++) {
                LocalDate cellDate = weekStart.plusDays(d);
                DailyPoint point = byDate.get(cellDate);
                cells.add(new HeatmapCell(
                        weekStart,
                        d + 1,
                        cellDate,
                        point == null ? 0.0 : point.avgIntensity(),
                        point == null ? 0 : point.count()
                ));
            }
        }
        return cells;
    }

    private List<PeakAlert> buildPeakAlerts(List<DailyPoint> daily) {
        if (daily.size() < 4) {
            return List.of();
        }

        double mean = daily.stream().mapToDouble(DailyPoint::avgIntensity).average().orElse(0.0);

        double varianceAcc = 0.0;
        for (DailyPoint point : daily) {
            double diff = point.avgIntensity() - mean;
            varianceAcc += diff * diff;
        }

        double stdDev = Math.sqrt(varianceAcc / daily.size());
        double threshold = Math.max(7.0, mean + (1.5 * stdDev));

        List<PeakAlert> alerts = new ArrayList<>();
        for (DailyPoint point : daily) {
            if (point.count() >= 2 && point.avgIntensity() >= threshold) {
                alerts.add(new PeakAlert(point.day(), point.count(), point.avgIntensity(), round2(threshold)));
            }
        }

        return alerts;
    }

    private String buildDisplayName(String nom, String prenom, Integer patientId) {
        String name = (safe(nom) + " " + safe(prenom)).trim();
        if (!name.isBlank()) {
            return name;
        }
        return "Patient #" + (patientId == null ? 0 : patientId);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record PatientOption(Integer patientId, String displayName, String email) {
        @Override
        public String toString() {
            if (email == null || email.isBlank()) {
                return displayName;
            }
            return displayName + " - " + email;
        }
    }

    public record DailyPoint(LocalDate day, int count, double avgIntensity) {
    }

    public record HeatmapCell(LocalDate weekStart, int dayOfWeek, LocalDate date, double avgIntensity, int count) {
    }

    public record PeakAlert(LocalDate date, int count, double avgIntensity, double threshold) {
    }

    public record DashboardData(List<DailyPoint> daily, List<HeatmapCell> heatmap, List<PeakAlert> alerts) {
    }
}
