package com.santea.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SanteQuotidienneAdminServiceTest {
    private SanteQuotidienneAdminService service;

    @BeforeEach
    void setUp() {
        service = new SanteQuotidienneAdminService();
    }

    @Test
    void testGetAllData() {
        var data = service.getAllData();
        assertNotNull(data, "Data list should not be null");
    }

    @Test
    void testGetFilteredData() {
        var data = service.getFilteredData("patient", 
            LocalDate.now().minusDays(30), 
            LocalDate.now());
        assertNotNull(data, "Filtered data should not be null");
    }

    @Test
    void testGetFilteredDataWithoutDates() {
        var data = service.getFilteredData("patient", null, null);
        assertNotNull(data, "Filtered data without dates should not be null");
    }

    @Test
    void testGetStatistics() {
        var stats = service.getStatistics();
        assertNotNull(stats, "Statistics should not be null");
        assertTrue(stats.totalEntries >= 0, "Total entries should be >= 0");
        assertTrue(stats.usersWithEntries >= 0, "Users with entries should be >= 0");
        assertTrue(stats.totalPatientUsers >= 0, "Total patient users should be >= 0");
        assertTrue(stats.patientsWithoutEntries >= 0, "Patients without entries should be >= 0");
    }

    @Test
    void testGetUserStatistics() {
        var userStats = service.getUserStatistics();
        assertNotNull(userStats, "User statistics should not be null");
    }

    @Test
    void testExportToCSVWithValidDates() throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("test_export", ".csv");
        tempFile.deleteOnExit();
        
        boolean result = service.exportToCSV(tempFile, 
            LocalDate.now().minusDays(30), 
            LocalDate.now());
        
        assertTrue(result, "Export should succeed");
        assertTrue(tempFile.length() > 0, "Exported file should not be empty");
    }

    @Test
    void testFormatDouble() {
        String formatted = SanteQuotidienneAdminService.formatDouble(123.456789);
        assertEquals("123.46", formatted, "Double formatting should work");
    }

    @Test
    void testFormatDoubleZero() {
        String formatted = SanteQuotidienneAdminService.formatDouble(0.0);
        assertEquals("0.00", formatted, "Zero should be formatted as 0.00");
    }

    @Test
    void testFormatDoubleNegative() {
        String formatted = SanteQuotidienneAdminService.formatDouble(-45.6789);
        assertEquals("-45.68", formatted, "Negative numbers should be formatted");
    }

    @Test
    void testStatisticsDataRecord() {
        var stats = service.getStatistics();
        
        // Vérifier que les champs sont accessibles
        assertNotNull(stats);
        int total = stats.totalEntries;
        int users = stats.usersWithEntries;
        
        assertTrue(total >= 0 && users >= 0, "Statistics values should be accessible");
    }

    @Test
    void testMultipleCallsToGetStatistics() {
        var stats1 = service.getStatistics();
        var stats2 = service.getStatistics();
        
        assertNotNull(stats1, "First call should return non-null statistics");
        assertNotNull(stats2, "Second call should return non-null statistics");
        // Both should have consistent data types
        assertEquals(stats1.getClass(), stats2.getClass(), "Statistics class should be consistent");
    }

    @Test
    void testLazyInitialization() {
        // The service should work without explicit initialization
        assertDoesNotThrow(() -> {
            service.getStatistics();
        }, "Service should handle lazy initialization");
    }

    @Test
    void testGetAllDataObservableList() {
        var data = service.getAllData();
        assertNotNull(data, "Observable list should not be null");
        // Check if it's modifiable without throwing exceptions
        assertDoesNotThrow(() -> {
            data.clear();
            data.addAll(service.getAllData());
        }, "Observable list should be modifiable");
    }

    @Test
    void testGetUserStatisticsOrdering() {
        var stats = service.getUserStatistics();
        assertNotNull(stats, "User statistics should not be null");
        
        // If there's data, it should be ordered by entry count descending
        if (stats.size() > 1) {
            for (int i = 0; i < stats.size() - 1; i++) {
                Object[] row1 = stats.get(i);
                Object[] row2 = stats.get(i + 1);
                
                int count1 = ((Number) row1[3]).intValue();
                int count2 = ((Number) row2[3]).intValue();
                
                assertTrue(count1 >= count2, "Statistics should be ordered by count descending");
            }
        }
    }
}
