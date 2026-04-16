package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.repository.SymptomesListeAdminRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SymptomesListeAdminService {
    private SymptomesListeAdminRepository repository;
    private boolean initialized = false;

    private synchronized void ensureInitialized() {
        if (!initialized) {
            DatabaseService databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
            this.repository = new SymptomesListeAdminRepository(databaseService);
            initialized = true;
        }
    }

    public List<SymptomRow> findAllSymptoms() {
        return findFilteredSymptoms(null, null, null);
    }

    public List<SymptomRow> findFilteredSymptoms(String keyword, LocalDate createdFrom, LocalDate createdTo) {
        ensureInitialized();
        List<SymptomRow> rows = new ArrayList<>();
        for (SymptomesListeAdminRepository.SymptomRowData row : repository.findFilteredSymptoms(keyword, createdFrom, createdTo)) {
            rows.add(new SymptomRow(row.id(), row.nom(), row.categorie(), row.createdAt()));
        }
        return rows;
    }

    public ActionResult createSymptom(String nom, String categorie, LocalDate createdDate) {
        ensureInitialized();
        ValidationResult validation = validateInputs(nom, categorie, createdDate);
        if (!validation.valid()) {
            return ActionResult.failure(validation.message());
        }

        SymptomesListeAdminRepository.MutationResult result = repository.insertSymptom(
                nom.trim(),
                categorie.trim(),
                createdDate.atStartOfDay()
        );

        if (!result.success()) {
            String msg = result.errorMessage().toLowerCase().contains("duplicate")
                    ? "Le nom du symptome existe deja."
                    : "Erreur SQL: " + result.errorMessage();
            return ActionResult.failure(msg);
        }

        return ActionResult.success("Symptome ajoute avec succes.");
    }

    public ActionResult updateSymptom(Integer id, String nom, String categorie, LocalDate createdDate) {
        ensureInitialized();
        if (id == null || id <= 0) {
            return ActionResult.failure("Selectionne un symptome a modifier.");
        }

        ValidationResult validation = validateInputs(nom, categorie, createdDate);
        if (!validation.valid()) {
            return ActionResult.failure(validation.message());
        }

        SymptomesListeAdminRepository.MutationResult result = repository.updateSymptom(
                id,
                nom.trim(),
                categorie.trim(),
                createdDate.atStartOfDay()
        );

        if (!result.success()) {
            String msg = result.errorMessage().toLowerCase().contains("duplicate")
                    ? "Le nom du symptome existe deja."
                    : "Erreur SQL: " + result.errorMessage();
            return ActionResult.failure(msg);
        }

        return ActionResult.success("Symptome modifie avec succes.");
    }

    public ActionResult deleteSymptom(Integer id) {
        ensureInitialized();
        if (id == null || id <= 0) {
            return ActionResult.failure("Selectionne un symptome a supprimer.");
        }

        SymptomesListeAdminRepository.MutationResult result = repository.deleteSymptom(id);
        if (!result.success()) {
            return ActionResult.failure("Erreur SQL: " + result.errorMessage());
        }
        return ActionResult.success("Symptome supprime avec succes.");
    }

    public List<PieStat> loadTodayUsageStats() {
        ensureInitialized();
        List<PieStat> stats = new ArrayList<>();
        for (SymptomesListeAdminRepository.SymptomPieStatData row : repository.findTodayUsageStats()) {
            stats.add(new PieStat(row.nom(), row.patientsCount()));
        }
        return stats;
    }

    public RegistrationStats getRegistrationStats() {
        ensureInitialized();
        SymptomesListeAdminRepository.RegistrationStatsData stats = repository.getRegistrationStats();
        return new RegistrationStats(stats.withSymptoms(), stats.totalPatients());
    }

    private ValidationResult validateInputs(String nom, String categorie, LocalDate createdDate) {
        String safeNom = nom == null ? "" : nom.trim();
        String safeCategorie = categorie == null ? "" : categorie.trim();

        if (safeNom.isEmpty()) {
            return ValidationResult.invalid("Le nom est obligatoire.");
        }
        if (safeNom.length() < 3 || safeNom.length() > 20) {
            return ValidationResult.invalid("Le nom doit contenir entre 3 et 20 caracteres.");
        }

        if (safeCategorie.isEmpty()) {
            return ValidationResult.invalid("La categorie est obligatoire.");
        }
        if (safeCategorie.length() < 5 || safeCategorie.length() > 20) {
            return ValidationResult.invalid("La categorie doit contenir entre 5 et 20 caracteres.");
        }

        if (createdDate == null) {
            return ValidationResult.invalid("created_at est obligatoire.");
        }

        return ValidationResult.ok();
    }

    private record ValidationResult(boolean valid, String message) {
        static ValidationResult ok() {
            return new ValidationResult(true, "");
        }

        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }

    public record SymptomRow(int id, String nom, String categorie, LocalDateTime createdAt) {
    }

    public record PieStat(String label, int count) {
    }

    public record RegistrationStats(int withSymptoms, int totalPatients) {
    }
}
