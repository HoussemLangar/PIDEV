package com.santea.repository;

import com.santea.model.Teleconsultation;
import java.time.LocalDateTime;
import java.util.*;

/**
 * TeleconsultationRepository
 * Data access layer for Teleconsultation entities
 */
public class TeleconsultationRepository {
    
    private Map<String, Teleconsultation> store = new HashMap<>();
    
    /**
     * Save a teleconsultation
     * @param consultation Teleconsultation to save
     * @return Saved consultation
     */
    public Teleconsultation save(Teleconsultation consultation) {
        store.put(String.valueOf(consultation.getId()), consultation);
        return consultation;
    }
    
    /**
     * Find by ID
     * @param id Consultation ID
     * @return Teleconsultation or null
     */
    public Teleconsultation findById(String id) {
        return store.get(Integer.parseInt(id));
    }
    
    /**
     * Find all by patient ID
     * @param patientId Patient ID
     * @return List of consultations
     */
    public List<Teleconsultation> findByPatientId(String patientId) {
        List<Teleconsultation> result = new ArrayList<>();
        Integer patientIdInt = Integer.parseInt(patientId);
        store.values().forEach(c -> {
            if (c.getPatientId() != null && c.getPatientId().equals(patientIdInt)) {
                result.add(c);
            }
        });
        return result;
    }
    
    /**
     * Find all by professional ID
     * @param professionalId Professional ID
     * @return List of consultations
     */
    public List<Teleconsultation> findByProfessionalId(String professionalId) {
        List<Teleconsultation> result = new ArrayList<>();
        Integer professionalIdInt = Integer.parseInt(professionalId);
        store.values().forEach(c -> {
            if (c.getProfessionalId() != null && c.getProfessionalId().equals(professionalIdInt)) {
                result.add(c);
            }
        });
        return result;
    }
    
    /**
     * Find upcoming consultations for professional
     * @param professionalId Professional ID
     * @return List of upcoming consultations
     */
    public List<Teleconsultation> findUpcomingForProfessional(String professionalId) {
        List<Teleconsultation> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        Integer professionalIdInt = Integer.parseInt(professionalId);
        store.values().forEach(c -> {
            if (c.getProfessionalId() != null && c.getProfessionalId().equals(professionalIdInt) &&
                c.getScheduledAt() != null && c.getScheduledAt().isAfter(now) &&
                !c.getStatus().equals("CANCELLED")) {
                result.add(c);
            }
        });
        result.sort(Comparator.comparing(Teleconsultation::getScheduledAt));
        return result;
    }
    
    /**
     * Find by status
     * @param status Status to find
     * @return List of consultations
     */
    public List<Teleconsultation> findByStatus(String status) {
        List<Teleconsultation> result = new ArrayList<>();
        store.values().forEach(c -> {
            if (c.getStatus().equals(status)) {
                result.add(c);
            }
        });
        return result;
    }
    
    /**
     * Find all
     * @return All consultations
     */
    public List<Teleconsultation> findAll() {
        return new ArrayList<>(store.values());
    }
    
    /**
     * Delete by ID
     * @param id Consultation ID
     * @return true if deleted
     */
    public boolean deleteById(String id) {
        return store.remove(id) != null;
    }
    
    /**
     * Check existence
     * @param id Consultation ID
     * @return true if exists
     */
    public boolean existsById(String id) {
        return store.containsKey(id);
    }
    
    /**
     * Count all
     * @return Total count
     */
    public long count() {
        return store.size();
    }
}
