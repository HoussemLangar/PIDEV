package com.santea.service;

import com.santea.model.Teleconsultation;
import com.santea.model.User;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TeleconsultationService - Section 2.4
 * Manages video consultations via Jitsi integration
 * 
 * Responsibilities:
 * - Creation and planning of teleconsultations
 * - Jitsi room management
 * - Status tracking and duration calculation
 * - Real-time status updates
 * - Statistics aggregation (completed, pending, duration tracking)
 * 
 * Database Logic from Symfony:
 * - Entity: Teleconsultation with fields: id, initiator, recipient, scheduledAt, startedAt, endedAt, status, jitsiRoomName, duration, notes
 * - Statuses: PENDING, ACTIVE, COMPLETED, CANCELLED
 * - Professional roles: ROLE_MEDECIN, ROLE_COACH, ROLE_NUTRITIONNISTE
 */
public class TeleconsultationService {
    private static final String JITSI_SERVER_URL = "https://meet.jit.si";
    private static final List<String> PROFESSIONAL_ROLES = Arrays.asList(
        "ROLE_MEDECIN", "ROLE_COACH", "ROLE_NUTRITIONNISTE"
    );
    
    // Core storage maps matching database structure
    private static final Map<String, Teleconsultation> consultations = new HashMap<>();
    private static final Map<String, List<String>> consultationObservers = new HashMap<>();
    private static final List<TeleconsultationListener> listeners = new ArrayList<>();

    private String getEffectiveRole(User user) {
        return user.getSubscriptionType() != null && !user.getSubscriptionType().isBlank()
            ? user.getSubscriptionType()
            : user.getRole();
    }

    private boolean isPatient(User user) {
        return "ROLE_PATIENT".equals(getEffectiveRole(user));
    }

    private boolean isProfessional(User user) {
        return PROFESSIONAL_ROLES.contains(getEffectiveRole(user));
    }
    
    /**
     * Create a new teleconsultation
     * @param patientId The patient's user ID
     * @param professionalId The professional's user ID
     * @param professionalType Type of professional (MEDECIN, COACH, NUTRITIONNISTE)
     * @param scheduledAt Scheduled consultation time
     * @return Created Teleconsultation object
     */
    public Teleconsultation createTeleconsultation(String patientId, String professionalId, 
                                                    String professionalType, LocalDateTime scheduledAt) {
        Teleconsultation consultation = new Teleconsultation();
        consultation.setRecipient(new User());
        consultation.getRecipient().setId(Integer.parseInt(patientId));
        consultation.setInitiator(new User());
        consultation.getInitiator().setId(Integer.parseInt(professionalId));
        consultation.setScheduledAt(scheduledAt);
        String roomName = generateJitsiRoomName(patientId, professionalId);
        consultation.setJitsiRoomName(roomName);
        consultation.setStatus("PENDING");
        consultation.setId((int) System.currentTimeMillis() % 1000000);
        consultation.setCreatedAt(LocalDateTime.now());
        
        String consultationKey = String.valueOf(consultation.getId());
        consultations.put(consultationKey, consultation);
        
        // Notify listeners
        notifyListeners("CONSULTATION_CREATED", consultation);
        
        return consultation;
    }

    public Teleconsultation createTeleconsultation(User initiator, User recipient,
                                                   String type, String description, LocalDateTime scheduledAt) {
        if (initiator == null || recipient == null || scheduledAt == null) {
            return null;
        }
        if (initiator.getId().equals(recipient.getId())) {
            return null;
        }
        if (scheduledAt.isBefore(LocalDateTime.now())) {
            return null;
        }

        boolean initiatorPatient = isPatient(initiator);
        boolean initiatorProfessional = isProfessional(initiator);
        boolean recipientPatient = isPatient(recipient);
        boolean recipientProfessional = isProfessional(recipient);

        if (initiatorPatient && !recipientProfessional) {
            return null;
        }
        if (initiatorProfessional && !recipientPatient) {
            return null;
        }
        if (!initiatorPatient && !initiatorProfessional) {
            return null;
        }

        User professional = initiatorProfessional ? initiator : recipient;
        if (!isDoctorAvailable(professional, scheduledAt)) {
            return null;
        }

        Teleconsultation consultation = new Teleconsultation();
        consultation.setInitiator(initiator);
        consultation.setRecipient(recipient);
        consultation.setScheduledAt(scheduledAt);
        consultation.setType(type == null || type.isBlank() ? "general" : type);
        consultation.setDescription(description);
        consultation.setRoomName(generateJitsiRoomName(
            initiator.getId().toString(),
            recipient.getId().toString()
        ));
        consultation.setStatus(initiatorPatient ? "requested" : "pending");
        consultation.setCreatedAt(LocalDateTime.now());
        consultation.setId((int) (System.currentTimeMillis() % 1000000));

        consultations.put(String.valueOf(consultation.getId()), consultation);
        notifyListeners("CONSULTATION_CREATED", consultation);
        return consultation;
    }
    
    /**
     * Start a teleconsultation session
     * @param consultationId The consultation ID
     * @return Updated Teleconsultation with ACTIVE status
     */
    public Teleconsultation startTeleconsultation(String consultationId) {
        Teleconsultation consultation = consultations.get(consultationId);
        if (consultation != null) {
            consultation.startConsultation();
            notifyListeners("CONSULTATION_STARTED", consultation);
            return consultation;
        }
        return null;
    }
    
    /**
     * End a teleconsultation session
     * @param consultationId The consultation ID
     * @param notes Optional notes from the professional
     * @return Updated Teleconsultation with COMPLETED status
     */
    public Teleconsultation endTeleconsultation(String consultationId, String notes) {
        Teleconsultation consultation = consultations.get(consultationId);
        if (consultation != null) {
            consultation.endConsultation();
            if (notes != null) {
                consultation.setNotes(notes);
            }
            notifyListeners("CONSULTATION_ENDED", consultation);
            return consultation;
        }
        return null;
    }
    
    /**
     * Cancel a teleconsultation
     * @param consultationId The consultation ID
     * @return Updated Teleconsultation with CANCELLED status
     */
    public Teleconsultation cancelTeleconsultation(String consultationId) {
        Teleconsultation consultation = consultations.get(consultationId);
        if (consultation != null) {
            consultation.cancelConsultation();
            consultation.setEndedAt(LocalDateTime.now());
            if (consultation.getStartedAt() != null) {
                consultation.setDurationSeconds((int) ChronoUnit.SECONDS.between(
                    consultation.getStartedAt(),
                    consultation.getEndedAt()
                ));
            }
            notifyListeners("CONSULTATION_CANCELLED", consultation);
            return consultation;
        }
        return null;
    }
    
    /**
     * Get a teleconsultation by ID
     * @param consultationId The consultation ID
     * @return Teleconsultation object or null
     */
    public Teleconsultation getTeleconsultation(String consultationId) {
        return consultations.get(consultationId);
    }
    
    /**
     * Get all teleconsultations for a user (as patient)
     * @param patientId The patient's user ID
     * @return List of teleconsultations
     */
    public List<Teleconsultation> getTeleconsultationsForPatient(String patientId) {
        return consultations.values().stream()
            .filter(c -> c.getRecipient() != null)
            .filter(c -> patientId.equals(String.valueOf(c.getRecipient().getId())))
            .sorted(Comparator.comparing(Teleconsultation::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all teleconsultations for a professional
     * @param professionalId The professional's user ID
     * @return List of teleconsultations
     */
    public List<Teleconsultation> getTeleconsultationsForProfessional(String professionalId) {
        return consultations.values().stream()
            .filter(c -> c.getInitiator() != null)
            .filter(c -> professionalId.equals(String.valueOf(c.getInitiator().getId())))
            .sorted(Comparator.comparing(Teleconsultation::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all upcoming consultations for a professional
     * @param professionalId The professional's user ID
     * @return List of upcoming consultations
     */
    public List<Teleconsultation> getUpcomingConsultations(String professionalId) {
        List<Teleconsultation> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        consultations.values().forEach(c -> {
            if (professionalId.equals(String.valueOf(c.getInitiator().getId())) && 
                c.getScheduledAt() != null && c.getScheduledAt().isAfter(now) &&
                ("pending".equals(c.getStatus()) || "requested".equals(c.getStatus()))) {
                result.add(c);
            }
        });
        result.sort(Comparator.comparing(Teleconsultation::getScheduledAt));
        return result;
    }
    
    /**
     * Get consultation history (completed consultations)
     * @param userId The user's ID (patient or professional)
     * @return List of completed consultations
     */
    public List<Teleconsultation> getConsultationHistory(String userId) {
        List<Teleconsultation> result = new ArrayList<>();
        consultations.values().forEach(c -> {
            if ((c.getPatientId().equals(userId) || c.getProfessionalId().equals(userId)) &&
                c.isCompleted()) {
                result.add(c);
            }
        });
        result.sort(Comparator.comparing(Teleconsultation::getEndedAt).reversed());
        return result;
    }
    
    /**
     * Reschedule a teleconsultation
     * @param consultationId The consultation ID
     * @param newScheduledAt New scheduled time
     * @return Updated Teleconsultation
     */
    public Teleconsultation rescheduleTeleconsultation(String consultationId, LocalDateTime newScheduledAt) {
        Teleconsultation consultation = consultations.get(consultationId);
        if (consultation != null && (consultation.isPending() || consultation.isRequested())) {
            consultation.setScheduledAt(newScheduledAt);
            if (newScheduledAt.isAfter(LocalDateTime.now()) && consultation.isRequested()) {
                consultation.setStatus("pending");
            }
            notifyListeners("CONSULTATION_RESCHEDULED", consultation);
            return consultation;
        }
        return null;
    }
    
    /**
     * Generate a unique Jitsi room name
     * @param patientId The patient's ID
     * @param professionalId The professional's ID
     * @return Unique room name
     */
    private String generateJitsiRoomName(String patientId, String professionalId) {
        return "santea-" + patientId + "-" + professionalId + "-" + System.currentTimeMillis();
    }
    
    /**
     * Generate Jitsi room URL
     * @param roomName The room name
     * @return Full Jitsi room URL
     */
    private String generateJitsiRoomUrl(String roomName) {
        return JITSI_SERVER_URL + "/" + roomName;
    }
    
    /**
     * Get consultation statistics for user (matching Symfony logic)
     * @param userId The user's ID
     * @return Map with stats: completed, pending, totalDuration, avgDuration
     */
    public Map<String, Object> getStatistics(String userId) {
        Map<String, Object> stats = new HashMap<>();
        
        List<Teleconsultation> userConsultations = consultations.values().stream()
            .filter(c -> isUserInConsultation(userId, c))
            .collect(Collectors.toList());
        
        long completed = userConsultations.stream()
            .filter(c -> "COMPLETED".equals(c.getStatus()))
            .count();
        
        long pending = userConsultations.stream()
            .filter(c -> "PENDING".equals(c.getStatus()) && !isInPast(c.getScheduledAt()))
            .count();
        
        long totalDuration = userConsultations.stream()
            .filter(c -> "COMPLETED".equals(c.getStatus()))
            .mapToLong(c -> c.getDurationMinutes() != null ? c.getDurationMinutes() : 0)
            .sum() * 60; // Convert to seconds for calculation
        
        long avgDuration = completed > 0 ? totalDuration / completed : 0;
        
        stats.put("completed", completed);
        stats.put("pending", pending);
        stats.put("totalDuration", totalDuration);
        stats.put("avgDuration", avgDuration);
        
        return stats;
    }
    
    /**
     * Find upcoming consultations (PENDING with future scheduledAt)
     * @param userId User ID
     * @return List of upcoming consultations sorted by scheduledAt
     */
    public List<Teleconsultation> findUpcoming(User user) {
        String userId = user.getId().toString();
        LocalDateTime now = LocalDateTime.now();
        
        return consultations.values().stream()
            .filter(c -> isUserInConsultation(userId, c))
            .filter(c -> c.isPending() || c.isRequested())
            .filter(c -> c.getScheduledAt() != null && c.getScheduledAt().isAfter(now))
            .sorted(Comparator.comparing(Teleconsultation::getScheduledAt))
            .collect(Collectors.toList());
    }
    
    /**
     * Find ongoing consultations (ACTIVE status)
     * @param user User object
     * @return List of active consultations
     */
    public List<Teleconsultation> findOngoing(User user) {
        String userId = user.getId().toString();
        
        return consultations.values().stream()
            .filter(c -> isUserInConsultation(userId, c))
            .filter(Teleconsultation::isOngoing)
            .sorted(Comparator.comparing(Teleconsultation::getStartedAt, 
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
    }
    
    /**
     * Find past consultations (COMPLETED status)
     * @param user User object
     * @return List of completed consultations sorted by endedAt descending
     */
    public List<Teleconsultation> findPast(User user) {
        String userId = user.getId().toString();
        
        return consultations.values().stream()
            .filter(c -> isUserInConsultation(userId, c))
            .filter(c -> c.isCompleted() || c.isCancel() || (c.isPending() && isInPast(c.getScheduledAt())))
            .sorted(Comparator.comparing(Teleconsultation::getEndedAt, 
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
    }
    
    /**
     * Check if a doctor/professional is available at specified time
     * (Simplified: check no other consultation at same time)
     * @param professional Professional user
     * @param dateTime Requested date/time
     * @return true if available
     */
    public boolean isDoctorAvailable(User professional, LocalDateTime dateTime) {
        String profId = professional.getId().toString();
        LocalDateTime rangeStart = dateTime.minusMinutes(30);
        LocalDateTime rangeEnd = dateTime.plusMinutes(30);
        
        return consultations.values().stream()
            .filter(c -> profId.equals(c.getInitiator().getId().toString()) || 
                        profId.equals(c.getRecipient().getId().toString()))
            .filter(c -> c.isOngoing() || c.isPending())
            .noneMatch(c -> {
                LocalDateTime scheduled = c.getScheduledAt();
                return scheduled != null && 
                       !scheduled.isBefore(rangeStart) && 
                       !scheduled.isAfter(rangeEnd);
            });
    }

    public boolean canStartConsultation(Teleconsultation consultation) {
        if (consultation == null) {
            return false;
        }
        if (consultation.isOngoing()) {
            return true;
        }
        if (!consultation.isPending()) {
            return false;
        }
        LocalDateTime scheduledAt = consultation.getScheduledAt();
        return scheduledAt != null && !LocalDateTime.now().isBefore(scheduledAt.minusMinutes(5));
    }

    public Teleconsultation approveConsultation(String consultationId) {
        Teleconsultation consultation = consultations.get(consultationId);
        if (consultation != null && consultation.isRequested()) {
            consultation.setStatus("pending");
            notifyListeners("CONSULTATION_APPROVED", consultation);
            return consultation;
        }
        return null;
    }

    public Teleconsultation rejectConsultation(String consultationId) {
        Teleconsultation consultation = consultations.get(consultationId);
        if (consultation != null && consultation.isRequested()) {
            consultation.setStatus("cancelled");
            consultation.setEndedAt(LocalDateTime.now());
            notifyListeners("CONSULTATION_REJECTED", consultation);
            return consultation;
        }
        return null;
    }
    
    /**
     * Add listener for real-time consultation updates
     * @param listener TeleconsultationListener
     */
    public void addListener(TeleconsultationListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove listener
     * @param listener TeleconsultationListener
     */
    public void removeListener(TeleconsultationListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notify all listeners of consultation event
     * @param eventType Event type (CREATED, STARTED, ENDED, etc.)
     * @param consultation Affected consultation
     */
    private void notifyListeners(String eventType, Teleconsultation consultation) {
        listeners.forEach(listener -> listener.onTeleconsultationEvent(eventType, consultation));
    }
    
    /**
     * Helper: Check if user (patient or professional) is part of consultation
     */
    private boolean isUserInConsultation(String userId, Teleconsultation consultation) {
        return userId.equals(consultation.getInitiator().getId().toString()) ||
               userId.equals(consultation.getRecipient().getId().toString());
    }
    
    /**
     * Helper: Check if a date/time is in the past
     */
    private boolean isInPast(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isBefore(LocalDateTime.now());
    }
    
    /**
     * Interface for real-time consultation event listeners (replaces Mercure in JavaFX)
     */
    public interface TeleconsultationListener {
        void onTeleconsultationEvent(String eventType, Teleconsultation consultation);
    }
}
