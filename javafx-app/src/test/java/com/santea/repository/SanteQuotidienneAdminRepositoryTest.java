package com.santea.repository;

import com.santea.config.DatabaseConfig;
import com.santea.service.DatabaseService;
import com.santea.testsupport.TestDatabaseBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SanteQuotidienneAdminRepositoryTest {
    private SanteQuotidienneAdminRepository repository;

    @BeforeEach
    void setUp() {
        try {
            TestDatabaseBootstrap.ensureSchemaAndSeed();
            DatabaseService databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
            repository = new SanteQuotidienneAdminRepository(databaseService);
        } catch (Exception e) {
            fail("Database connection failed: " + e.getMessage());
        }
    }

    @Test
    void testFindAllWithUser() {
        List<Object[]> data = repository.findAllWithUser();
        assertNotNull(data, "Data list should not be null");
    }

    @Test
    void testFindFiltered() {
        LocalDate now = LocalDate.now();
        List<Object[]> data = repository.findFiltered(null, now.minusDays(30), now);
        assertNotNull(data, "Filtered data should not be null");
    }

    @Test
    void testFindFilteredByEmail() {
        List<Object[]> data = repository.findFiltered("patient", null, null);
        assertNotNull(data, "Filtered data should not be null");
    }

    @Test
    void testGetStatistics() {
        SanteQuotidienneAdminRepository.StatisticsData stats = repository.getStatistics();
        
        assertNotNull(stats, "Statistics should not be null");
        assertTrue(stats.totalEntries >= 0, "Total entries should be >= 0");
        assertTrue(stats.usersWithEntries >= 0, "Users with entries should be >= 0");
        assertTrue(stats.totalPatientUsers >= 0, "Total patient users should be >= 0");
        assertTrue(stats.patientsWithoutEntries >= 0, "Patients without entries should be >= 0");
        assertTrue(stats.todayEntries >= 0, "Today entries should be >= 0");
        assertTrue(stats.weekEntries >= 0, "Week entries should be >= 0");
        
        // Validation logique
        assertTrue(stats.usersWithEntries <= stats.totalPatientUsers,
                   "Users with entries should not exceed total patients");
        assertTrue(stats.patientsWithoutEntries <= stats.totalPatientUsers,
                   "Patients without entries should not exceed total patients");
    }

    @Test
    void testGetUserStatistics() {
        List<Object[]> userStats = repository.getUserStatistics();
        assertNotNull(userStats, "User statistics should not be null");
        
        for (Object[] row : userStats) {
            assertNotNull(row[0], "Email should not be null");
            assertNotNull(row[3], "Entry count should not be null");
        }
    }

    @Test
    void testGetExportData() {
        LocalDate now = LocalDate.now();
        List<String> csvData = repository.getExportData(now.minusDays(30), now);
        
        assertNotNull(csvData, "Export data should not be null");
        // Each line should contain comma-separated values
        for (String line : csvData) {
            assertFalse(line.isEmpty(), "Export line should not be empty");
        }
    }

    @Test
    void testStatisticsDataInternal() {
        SanteQuotidienneAdminRepository.StatisticsData stats = new SanteQuotidienneAdminRepository.StatisticsData(
                100, 50, 75, 25, 10, 30
        );
        
        assertEquals(100, stats.totalEntries);
        assertEquals(50, stats.usersWithEntries);
        assertEquals(75, stats.totalPatientUsers);
        assertEquals(25, stats.patientsWithoutEntries);
        assertEquals(10, stats.todayEntries);
        assertEquals(30, stats.weekEntries);
    }
}
