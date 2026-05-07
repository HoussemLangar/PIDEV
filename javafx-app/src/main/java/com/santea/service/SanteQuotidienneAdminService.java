package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.repository.SanteQuotidienneAdminRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class SanteQuotidienneAdminService {
    private SanteQuotidienneAdminRepository repository;
    private DatabaseService databaseService;
    private boolean initialized = false;

    public SanteQuotidienneAdminService() {
        // Delay database initialization to avoid blocking FXML loading
    }

    private synchronized void ensureInitialized() {
        if (!initialized) {
            try {
                this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
                this.repository = new SanteQuotidienneAdminRepository(databaseService);
                initialized = true;
            } catch (Exception e) {
                System.err.println("❌ Erreur initialisation base de données: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Impossible de se connecter à la base de données", e);
            }
        }
    }

    public ObservableList<Object[]> getAllData() {
        ensureInitialized();
        List<Object[]> data = repository.findAllWithUser();
        return FXCollections.observableArrayList(data);
    }

    public ObservableList<Object[]> getFilteredData(String searchTerm, LocalDate startDate, LocalDate endDate) {
        ensureInitialized();
        List<Object[]> data = repository.findFiltered(searchTerm, startDate, endDate);
        return FXCollections.observableArrayList(data);
    }

    public SanteQuotidienneAdminRepository.StatisticsData getStatistics() {
        ensureInitialized();
        return repository.getStatistics();
    }

    public ObservableList<Object[]> getUserStatistics() {
        ensureInitialized();
        List<Object[]> data = repository.getUserStatistics();
        return FXCollections.observableArrayList(data);
    }

    public boolean exportToCSV(File file, LocalDate startDate, LocalDate endDate) {
        try {
            ensureInitialized();
            List<String> csvLines = repository.getExportData(startDate, endDate);
            try (FileWriter writer = new FileWriter(file)) {
                for (String line : csvLines) {
                    writer.write(line);
                    writer.write(System.lineSeparator());
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String formatDouble(double value) {
        return String.format("%.2f", value);
    }

    public int getEntryCountForUser(String email) {
        ensureInitialized();
        return repository.getUserStatistics().stream()
            .filter(row -> row[0] != null && row[0].toString().equalsIgnoreCase(email))
            .map(row -> ((Number) row[3]).intValue())
            .findFirst()
            .orElse(0);
    }
}
