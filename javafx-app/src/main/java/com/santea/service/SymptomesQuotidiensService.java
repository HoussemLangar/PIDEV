package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.repository.SymptomesQuotidiensRepository;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public class SymptomesQuotidiensService {
    private final SymptomesQuotidiensRepository repository;

    public SymptomesQuotidiensService() {
        DatabaseService databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
        this.repository = new SymptomesQuotidiensRepository(databaseService);
    }

    public List<SymptomeChoice> loadAvailableSymptoms() {
        List<SymptomesQuotidiensRepository.SymptomeChoiceRow> rows = repository.findAvailableSymptoms();
        List<SymptomeChoice> mapped = new ArrayList<>();
        for (SymptomesQuotidiensRepository.SymptomeChoiceRow row : rows) {
            mapped.add(new SymptomeChoice(row.id(), row.nom(), row.categorie()));
        }
        return mapped;
    }

    public SaveResult saveDailySymptoms(int userId,
                                        List<Integer> symptomIds,
                                        LocalDate symptomDate,
                                        Integer intensity,
                                        String duration,
                                        String notes) {
        if (userId <= 0) {
            return SaveResult.failure("Utilisateur invalide.");
        }
        if (symptomIds == null || symptomIds.isEmpty()) {
            return SaveResult.failure("Selectionne au moins un symptome.");
        }
        if (intensity == null || intensity < 1 || intensity > 10) {
            return SaveResult.failure("L'intensite doit etre comprise entre 1 et 10.");
        }

        LocalDate safeDate = symptomDate == null ? LocalDate.now() : symptomDate;

        SymptomesQuotidiensRepository.SaveDailyOutcome outcome = repository.saveDailySymptoms(
                userId,
                symptomIds,
                safeDate,
                intensity,
                duration,
                notes
        );

        if (!outcome.success()) {
            return SaveResult.failure("Erreur SQL: " + safe(outcome.errorMessage()));
        }
        if (outcome.insertedCount() == 0) {
            return SaveResult.failure("Aucun nouvel enregistrement: ces symptomes existent deja pour la date choisie.");
        }

        String message = outcome.insertedCount() + " symptome(s) enregistre(s)"
                + (outcome.skippedCount() > 0 ? " | " + outcome.skippedCount() + " deja existant(s)" : "")
                + ".";

        return SaveResult.success(outcome.insertedCount(), outcome.skippedCount(), message);
    }

    public List<SymptomeDailyRow> loadRowsForUser(int userId, LocalDate anchorDate, PeriodFilter periodFilter) {
        List<SymptomeDailyRow> rows = new ArrayList<>();
        if (userId <= 0) {
            return rows;
        }

        LocalDate safeAnchorDate = anchorDate == null ? LocalDate.now() : anchorDate;
        PeriodFilter safeFilter = periodFilter == null ? PeriodFilter.DAY : periodFilter;
        DateRange dateRange = resolveDateRange(safeAnchorDate, safeFilter);

        List<SymptomesQuotidiensRepository.SymptomeDailyRowData> raw = repository.findRowsForUserBetweenDates(
                userId,
                dateRange.start(),
                dateRange.end()
        );

        for (SymptomesQuotidiensRepository.SymptomeDailyRowData row : raw) {
            rows.add(new SymptomeDailyRow(
                    row.id(),
                    row.symptomId(),
                    row.symptomName(),
                    row.category(),
                    row.intensity(),
                    row.duration(),
                    row.notes(),
                    row.date()
            ));
        }

        return rows;
    }

    public SaveResult updateSymptomEntry(int userId,
                                         int entryId,
                                         int symptomId,
                                         LocalDate symptomDate,
                                         Integer intensity,
                                         String duration,
                                         String notes) {
        if (userId <= 0 || entryId <= 0 || symptomId <= 0) {
            return SaveResult.failure("Parametres invalides pour la modification.");
        }
        if (intensity == null || intensity < 1 || intensity > 10) {
            return SaveResult.failure("L'intensite doit etre comprise entre 1 et 10.");
        }

        LocalDate safeDate = symptomDate == null ? LocalDate.now() : symptomDate;
        SymptomesQuotidiensRepository.MutationOutcome outcome = repository.updateSymptomEntry(
                userId,
                entryId,
                symptomId,
                safeDate,
                intensity,
                duration,
                notes
        );

        if (!outcome.success()) {
            if ("entree introuvable".equals(outcome.errorMessage())) {
                return SaveResult.failure("Modification impossible: entree introuvable.");
            }
            return SaveResult.failure("Erreur SQL: " + safe(outcome.errorMessage()));
        }

        return SaveResult.success(1, 0, "Symptome modifie avec succes.");
    }

    public SaveResult deleteSymptomEntry(int userId, int entryId) {
        if (userId <= 0 || entryId <= 0) {
            return SaveResult.failure("Parametres invalides pour la suppression.");
        }

        SymptomesQuotidiensRepository.MutationOutcome outcome = repository.deleteSymptomEntry(userId, entryId);
        if (!outcome.success()) {
            if ("entree introuvable".equals(outcome.errorMessage())) {
                return SaveResult.failure("Suppression impossible: entree introuvable.");
            }
            return SaveResult.failure("Erreur SQL: " + safe(outcome.errorMessage()));
        }

        return SaveResult.success(1, 0, "Symptome supprime avec succes.");
    }

    private DateRange resolveDateRange(LocalDate anchorDate, PeriodFilter filter) {
        return switch (filter) {
            case WEEK -> {
                LocalDate start = anchorDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                LocalDate end = start.plusDays(6);
                yield new DateRange(start, end);
            }
            case MONTH -> new DateRange(anchorDate.withDayOfMonth(1), anchorDate.withDayOfMonth(anchorDate.lengthOfMonth()));
            case DAY -> new DateRange(anchorDate, anchorDate);
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record SymptomeChoice(int id, String nom, String categorie) {
    }

    public record SymptomeDailyRow(int id,
                                   int symptomId,
                                   String symptomName,
                                   String category,
                                   int intensity,
                                   String duration,
                                   String notes,
                                   LocalDate date) {
    }

    public record SaveResult(boolean success, String message, int insertedCount, int skippedCount) {
        public static SaveResult success(int insertedCount, int skippedCount, String message) {
            return new SaveResult(true, message, insertedCount, skippedCount);
        }

        public static SaveResult failure(String message) {
            return new SaveResult(false, message, 0, 0);
        }
    }

    public enum PeriodFilter {
        DAY,
        WEEK,
        MONTH
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
