package com.santea.service;

import com.santea.testsupport.TestDatabaseBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SymptomesListeAdminServiceTest {
    private SymptomesListeAdminService service;

    @BeforeEach
    void setUp() {
        TestDatabaseBootstrap.ensureSchemaAndSeed();
        service = new SymptomesListeAdminService();
    }

    @Test
    void testFindAllSymptoms() {
        List<SymptomesListeAdminService.SymptomRow> symptoms = service.findAllSymptoms();
        assertNotNull(symptoms, "Symptoms list should not be null");
        assertFalse(symptoms.isEmpty(), "Symptoms list should contain data");
    }

    @Test
    void testFindFilteredSymptoms() {
        List<SymptomesListeAdminService.SymptomRow> symptoms = 
            service.findFilteredSymptoms("douleur", null, null);
        
        assertNotNull(symptoms, "Filtered symptoms list should not be null");
    }

    @Test
    void testCreateSymptomSuccess() {
        String nomTest = "Test" + (System.currentTimeMillis() % 10000);
        SymptomesListeAdminService.ActionResult result =
            service.createSymptom(nomTest, "Test Category", LocalDate.now());
        
        assertTrue(result.success(), "Create should succeed: " + result.message());
    }

    @Test
    void testCreateSymptomMissingName() {
        SymptomesListeAdminService.ActionResult result =
            service.createSymptom("", "Test Category", LocalDate.now());
        
        assertFalse(result.success(), "Create should fail with empty name");
        assertTrue(result.message().contains("obligatoire"), "Error message should indicate required field");
    }

    @Test
    void testCreateSymptomInvalidNameLength() {
        SymptomesListeAdminService.ActionResult result =
            service.createSymptom("ab", "Test Category", LocalDate.now());
        
        assertFalse(result.success(), "Create should fail with name too short");
        assertTrue(result.message().contains("3 et 20"), "Error message should mention length constraint");
    }

    @Test
    void testCreateSymptomMissingCategory() {
        SymptomesListeAdminService.ActionResult result =
            service.createSymptom("Valid Name", "", LocalDate.now());
        
        assertFalse(result.success(), "Create should fail with empty category");
    }

    @Test
    void testCreateSymptomNullDate() {
        SymptomesListeAdminService.ActionResult result =
            service.createSymptom("Valid Name", "Test Category", null);
        
        assertFalse(result.success(), "Create should fail with null date");
    }

    @Test
    void testUpdateSymptomSuccess() {
        // Créer d'abord avec un nom unique
        String nomTest = "TestUpd" + System.currentTimeMillis();
        SymptomesListeAdminService.ActionResult createResult =
            service.createSymptom(nomTest, "Test Category", LocalDate.now());
        
        assertTrue(createResult.success(), "Create should succeed");
        
        // Retrouver l'ID créé
        List<SymptomesListeAdminService.SymptomRow> allSymptoms = service.findAllSymptoms();
        Integer createdId = allSymptoms.stream()
            .filter(s -> nomTest.equals(s.nom()))
            .map(SymptomesListeAdminService.SymptomRow::id)
            .findFirst()
            .orElse(null);
        
        assertNotNull(createdId, "Created symptom should be findable");
        
        // Mettre à jour avec l'ID créé et un nom unique
        String updatedName = "Updated" + System.currentTimeMillis();
        SymptomesListeAdminService.ActionResult updateResult =
            service.updateSymptom(createdId, updatedName, "Updated Category", LocalDate.now());
        
        assertTrue(updateResult.success(), "Update should succeed: " + updateResult.message());
    }

    @Test
    void testUpdateSymptomWithoutSelection() {
        SymptomesListeAdminService.ActionResult result =
            service.updateSymptom(null, "Updated Name", "Updated Category", LocalDate.now());
        
        assertFalse(result.success(), "Update should fail without selection");
    }

    @Test
    void testDeleteSymptomSuccess() {
        // Créer d'abord avec un nom unique
        String nomTest = "TestDel" + System.currentTimeMillis();
        SymptomesListeAdminService.ActionResult createResult =
            service.createSymptom(nomTest, "Test Category", LocalDate.now());
        
        assertTrue(createResult.success(), "Create should succeed");
        
        // Retrouver l'ID créé
        List<SymptomesListeAdminService.SymptomRow> allSymptoms = service.findAllSymptoms();
        Integer createdId = allSymptoms.stream()
            .filter(s -> nomTest.equals(s.nom()))
            .map(SymptomesListeAdminService.SymptomRow::id)
            .findFirst()
            .orElse(null);
        
        assertNotNull(createdId, "Created symptom should be findable");
        
        // Supprimer avec l'ID créé
        SymptomesListeAdminService.ActionResult deleteResult =
            service.deleteSymptom(createdId);
        
        assertTrue(deleteResult.success(), "Delete should succeed: " + deleteResult.message());
    }

    @Test
    void testLoadTodayUsageStats() {
        List<SymptomesListeAdminService.PieStat> stats = service.loadTodayUsageStats();
        assertNotNull(stats, "Stats list should not be null");
    }

    @Test
    void testGetRegistrationStats() {
        SymptomesListeAdminService.RegistrationStats stats = service.getRegistrationStats();
        assertNotNull(stats, "Registration stats should not be null");
        assertTrue(stats.totalPatients() >= 0, "Total patients should be >= 0");
        assertTrue(stats.withSymptoms() >= 0, "Patients with symptoms should be >= 0");
        assertTrue(stats.withSymptoms() <= stats.totalPatients(), 
                   "Patients with symptoms should not exceed total");
    }
}
