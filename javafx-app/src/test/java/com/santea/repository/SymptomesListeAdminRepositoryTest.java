package com.santea.repository;

import com.santea.config.DatabaseConfig;
import com.santea.service.DatabaseService;
import com.santea.testsupport.TestDatabaseBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SymptomesListeAdminRepositoryTest {
    private SymptomesListeAdminRepository repository;

    @BeforeEach
    void setUp() {
        try {
            TestDatabaseBootstrap.ensureSchemaAndSeed();
            DatabaseService databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
            repository = new SymptomesListeAdminRepository(databaseService);
        } catch (Exception e) {
            fail("Database connection failed: " + e.getMessage());
        }
    }

    @Test
    void testFindAllSymptoms() {
        List<SymptomesListeAdminRepository.SymptomRowData> symptoms = repository.findAllSymptoms();
        assertNotNull(symptoms, "Symptoms list should not be null");
        assertFalse(symptoms.isEmpty(), "Symptoms list should contain data");
    }

    @Test
    void testFindFilteredSymptomsByKeyword() {
        List<SymptomesListeAdminRepository.SymptomRowData> symptoms = 
            repository.findFilteredSymptoms("migraine", null, null);
        
        assertNotNull(symptoms, "Filtered symptoms list should not be null");
        for (SymptomesListeAdminRepository.SymptomRowData symptom : symptoms) {
            assertTrue(symptom.nom().toLowerCase().contains("migraine") || 
                      symptom.categorie().toLowerCase().contains("migraine"),
                      "Symptom should contain keyword");
        }
    }

    @Test
    void testFindFilteredSymptomsByDateRange() {
        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to = LocalDate.now();
        
        List<SymptomesListeAdminRepository.SymptomRowData> symptoms =
            repository.findFilteredSymptoms(null, from, to);
        
        assertNotNull(symptoms, "Filtered symptoms list should not be null");
        for (SymptomesListeAdminRepository.SymptomRowData symptom : symptoms) {
            assertNotNull(symptom.createdAt(), "Created date should not be null");
            assertTrue(!symptom.createdAt().toLocalDate().isBefore(from) &&
                      !symptom.createdAt().toLocalDate().isAfter(to),
                      "Symptom date should be in range");
        }
    }

    @Test
    void testInsertSymptom() {
        String nomTest = "Test" + (System.currentTimeMillis() % 10000);
        String categorieTest = "Test Category";
        LocalDateTime now = LocalDateTime.now();
        
        SymptomesListeAdminRepository.MutationResult result = 
            repository.insertSymptom(nomTest, categorieTest, now);
        
        assertTrue(result.success(), "Insert should succeed");
        assertNotNull(result.id(), "Generated ID should not be null");
    }

    @Test
    void testUpdateSymptom() {
        // Créer d'abord un symptôme
        String nomTest = "TestUpd" + (System.currentTimeMillis() % 10000);
        SymptomesListeAdminRepository.MutationResult insertResult =
            repository.insertSymptom(nomTest, "Category", LocalDateTime.now());
        
        assertTrue(insertResult.success(), "Insert should succeed");
        Integer id = insertResult.id();
        
        // Mettre à jour
        String nomUpdated = "UpdatedNam" + (System.currentTimeMillis() % 1000);
        SymptomesListeAdminRepository.MutationResult updateResult =
            repository.updateSymptom(id, nomUpdated, "New Category", LocalDateTime.now());
        
        assertTrue(updateResult.success(), "Update should succeed");
    }

    @Test
    void testDeleteSymptom() {
        // Créer d'abord un symptôme
        String nomTest = "TestDel" + (System.currentTimeMillis() % 10000);
        SymptomesListeAdminRepository.MutationResult insertResult =
            repository.insertSymptom(nomTest, "Category", LocalDateTime.now());
        
        assertTrue(insertResult.success(), "Insert should succeed");
        Integer id = insertResult.id();
        
        // Supprimer
        SymptomesListeAdminRepository.MutationResult deleteResult =
            repository.deleteSymptom(id);
        
        assertTrue(deleteResult.success(), "Delete should succeed");
    }

    @Test
    void testFindTodayUsageStats() {
        List<SymptomesListeAdminRepository.SymptomPieStatData> stats =
            repository.findTodayUsageStats();
        
        assertNotNull(stats, "Stats list should not be null");
    }
}
