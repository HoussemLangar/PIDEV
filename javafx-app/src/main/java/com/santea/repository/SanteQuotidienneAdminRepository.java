package com.santea.repository;

import com.santea.service.DatabaseService;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SanteQuotidienneAdminRepository {
    private final DatabaseService databaseService;

    public SanteQuotidienneAdminRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Récupère tous les enregistrements santé quotidienne avec l'utilisateur associé
     */
    public List<Object[]> findAllWithUser() {
        String sql = "SELECT sq.id, u.email, u.prenom, u.nom, sq.date, sq.poids, sq.sommeil, "
                + "sq.humeur, sq.activite_physique, sq.alimentation, sq.eau_bue, sq.tension_arterielle "
                + "FROM sante_quotidienne sq "
                + "JOIN users u ON sq.user_id = u.id "
                + "ORDER BY sq.date DESC, sq.id DESC";

        List<Object[]> results = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                results.add(mapDetailedRow(resultSet));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Recherche avec filtrage par email, nom, prénom et date
     */
    public List<Object[]> findFiltered(String searchTerm, LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder(
            "SELECT sq.id, u.email, u.prenom, u.nom, sq.date, sq.poids, sq.sommeil, "
                + "sq.humeur, sq.activite_physique, sq.alimentation, sq.eau_bue, sq.tension_arterielle "
                + "FROM sante_quotidienne sq "
                + "JOIN users u ON sq.user_id = u.id "
                + "WHERE 1=1"
        );

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append(" AND (LOWER(u.email) LIKE ? OR LOWER(u.nom) LIKE ? OR LOWER(u.prenom) LIKE ?)");
        }
        if (startDate != null) {
            sql.append(" AND DATE(sq.date) >= ?");
        }
        if (endDate != null) {
            sql.append(" AND DATE(sq.date) <= ?");
        }
        sql.append(" ORDER BY sq.date DESC, sq.id DESC");

        List<Object[]> results = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                String normalized = "%" + searchTerm.trim().toLowerCase() + "%";
                statement.setString(paramIndex++, normalized);
                statement.setString(paramIndex++, normalized);
                statement.setString(paramIndex++, normalized);
            }
            if (startDate != null) {
                statement.setDate(paramIndex++, Date.valueOf(startDate));
            }
            if (endDate != null) {
                statement.setDate(paramIndex++, Date.valueOf(endDate));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapDetailedRow(resultSet));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Récupère les statistiques globales demandées par l'admin
     */
    public StatisticsData getStatistics() {
        String sql = "SELECT "
                + "COUNT(sq.id) AS total_entries, "
                + "COUNT(DISTINCT sq.user_id) AS users_with_entries, "
                + "COUNT(DISTINCT CASE WHEN u.role = 'ROLE_PATIENT' THEN u.id END) AS total_patient_users, "
                + "COUNT(CASE WHEN DATE(sq.date) = CURDATE() THEN 1 END) AS today_entries, "
                + "COUNT(CASE WHEN sq.date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) THEN 1 END) AS week_entries "
                + "FROM users u "
                + "LEFT JOIN sante_quotidienne sq ON u.id = sq.user_id";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                int totalEntries = resultSet.getInt("total_entries");
                int usersWithEntries = resultSet.getInt("users_with_entries");
                int totalPatientUsers = resultSet.getInt("total_patient_users");
                int patientsWithoutEntries = Math.max(totalPatientUsers - usersWithEntries, 0);
                int todayEntries = resultSet.getInt("today_entries");
                int weekEntries = resultSet.getInt("week_entries");

                return new StatisticsData(
                    totalEntries,
                    usersWithEntries,
                    totalPatientUsers,
                    patientsWithoutEntries,
                    todayEntries,
                    weekEntries
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return new StatisticsData(0, 0, 0, 0, 0, 0);
    }

    /**
     * Récupère les statistiques par utilisateur :
     * email, prénom, nom, nombre d'entrées
     */
    public List<Object[]> getUserStatistics() {
        String sql = "SELECT "
                + "u.email AS email, "
                + "u.prenom AS prenom, "
                + "u.nom AS nom, "
                + "COUNT(sq.id) AS entry_count "
                + "FROM users u "
                + "LEFT JOIN sante_quotidienne sq ON u.id = sq.user_id "
                + "WHERE u.role = 'ROLE_PATIENT' "
                + "GROUP BY u.id, u.email, u.prenom, u.nom "
                + "HAVING COUNT(sq.id) > 0 "
                + "ORDER BY COUNT(sq.id) DESC, u.email ASC";

        List<Object[]> results = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                results.add(new Object[]{
                    resultSet.getString("email"),
                    resultSet.getString("prenom"),
                    resultSet.getString("nom"),
                    resultSet.getInt("entry_count")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Données pour export CSV
     */
    public List<String> getExportData(LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder(
            "SELECT u.email, u.prenom, u.nom, sq.date, sq.poids, sq.sommeil, "
                + "sq.humeur, sq.activite_physique, sq.alimentation, sq.eau_bue, sq.tension_arterielle "
                + "FROM sante_quotidienne sq "
                + "JOIN users u ON sq.user_id = u.id WHERE 1=1"
        );

        if (startDate != null) {
            sql.append(" AND DATE(sq.date) >= ?");
        }
        if (endDate != null) {
            sql.append(" AND DATE(sq.date) <= ?");
        }
        sql.append(" ORDER BY sq.date DESC, sq.id DESC");

        List<String> csvLines = new ArrayList<>();
        csvLines.add("Email,Prenom,Nom,Date,Poids(kg),Sommeil(h),Humeur,Activite(min),Alimentation,Eau(L),Tension");

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (startDate != null) {
                statement.setDate(paramIndex++, Date.valueOf(startDate));
            }
            if (endDate != null) {
                statement.setDate(paramIndex++, Date.valueOf(endDate));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String line = String.format("%s,%s,%s,%s,%.2f,%d,%s,%d,%s,%.2f,%s",
                        safeCsv(resultSet.getString("email")),
                        safeCsv(resultSet.getString("prenom")),
                        safeCsv(resultSet.getString("nom")),
                        resultSet.getDate("date"),
                        resultSet.getDouble("poids"),
                        resultSet.getInt("sommeil"),
                        safeCsv(resultSet.getString("humeur")),
                        resultSet.getInt("activite_physique"),
                        safeCsv(resultSet.getString("alimentation")),
                        resultSet.getDouble("eau_bue"),
                        safeCsv(resultSet.getString("tension_arterielle"))
                    );
                    csvLines.add(line);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return csvLines;
    }

    private Object[] mapDetailedRow(ResultSet resultSet) throws SQLException {
        Date sqlDate = resultSet.getDate("date");
        return new Object[]{
            resultSet.getInt("id"),
            resultSet.getString("email"),
            resultSet.getString("prenom"),
            resultSet.getString("nom"),
            sqlDate != null ? sqlDate.toLocalDate() : null,
            resultSet.getDouble("poids"),
            resultSet.getInt("sommeil"),
            resultSet.getString("humeur"),
            resultSet.getInt("activite_physique"),
            resultSet.getString("alimentation"),
            resultSet.getDouble("eau_bue"),
            resultSet.getString("tension_arterielle")
        };
    }

    private String safeCsv(String value) {
        return value == null ? "" : value.replace(",", " ");
    }

    public static class StatisticsData {
        public final int totalEntries;
        public final int usersWithEntries;
        public final int totalPatientUsers;
        public final int patientsWithoutEntries;
        public final int todayEntries;
        public final int weekEntries;

        public StatisticsData(int totalEntries, int usersWithEntries, int totalPatientUsers, int patientsWithoutEntries, int todayEntries, int weekEntries) {
            this.totalEntries = totalEntries;
            this.usersWithEntries = usersWithEntries;
            this.totalPatientUsers = totalPatientUsers;
            this.patientsWithoutEntries = patientsWithoutEntries;
            this.todayEntries = todayEntries;
            this.weekEntries = weekEntries;
        }
    }
}
