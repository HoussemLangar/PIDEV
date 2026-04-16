package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.Teleconsultation;
import com.santea.model.User;
import com.santea.repository.UserRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
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

    private final DatabaseService databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    private final UserRepository userRepository = new UserRepository(databaseService);
    
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
        if (patientId == null || professionalId == null || scheduledAt == null) {
            return null;
        }
        if (scheduledAt.isBefore(LocalDateTime.now())) {
            return null;
        }

        Optional<User> patient = userRepository.findById(Integer.parseInt(patientId));
        Optional<User> professional = userRepository.findById(Integer.parseInt(professionalId));
        if (patient.isEmpty() || professional.isEmpty()) {
            return null;
        }
        return createTeleconsultation(
            patient.get(),
            professional.get(),
            professionalType == null ? "general" : professionalType,
            null,
            scheduledAt
        );
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

        String roomName = generateJitsiRoomName(
            initiator.getId().toString(),
            recipient.getId().toString()
        );
        String status = initiatorPatient ? "requested" : "pending";
        LocalDateTime now = LocalDateTime.now();

        String sql = "INSERT INTO teleconsultations "
            + "(initiator_id, recipient_id, room_name, description, scheduled_at, started_at, ended_at, status, duration_seconds, type, created_at) "
            + "VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, NULL, ?, ?)";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, initiator.getId());
            statement.setInt(2, recipient.getId());
            statement.setString(3, roomName);
            statement.setString(4, description);
            statement.setTimestamp(5, Timestamp.valueOf(scheduledAt));
            statement.setString(6, status);
            statement.setString(7, type == null || type.isBlank() ? "general" : type);
            statement.setTimestamp(8, Timestamp.valueOf(now));
            statement.executeUpdate();

            int id = 0;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getInt(1);
                }
            }

            Teleconsultation consultation = new Teleconsultation();
            consultation.setId(id);
            consultation.setInitiator(initiator);
            consultation.setRecipient(recipient);
            consultation.setRoomName(roomName);
            consultation.setDescription(description);
            consultation.setScheduledAt(scheduledAt);
            consultation.setStatus(status);
            consultation.setType(type == null || type.isBlank() ? "general" : type);
            consultation.setCreatedAt(now);

            notifyListeners("CONSULTATION_CREATED", consultation);
            return consultation;
        } catch (SQLException exception) {
            Teleconsultation consultation = new Teleconsultation();
            consultation.setInitiator(initiator);
            consultation.setRecipient(recipient);
            consultation.setScheduledAt(scheduledAt);
            consultation.setType(type == null || type.isBlank() ? "general" : type);
            consultation.setDescription(description);
            consultation.setRoomName(roomName);
            consultation.setStatus(status);
            consultation.setCreatedAt(now);
            consultation.setId((int) (System.currentTimeMillis() % 1000000));

            consultations.put(String.valueOf(consultation.getId()), consultation);
            notifyListeners("CONSULTATION_CREATED", consultation);
            return consultation;
        }
    }
    
    /**
     * Start a teleconsultation session
     * @param consultationId The consultation ID
     * @return Updated Teleconsultation with ACTIVE status
     */
    public Teleconsultation startTeleconsultation(String consultationId) {
        Teleconsultation consultation = getTeleconsultation(consultationId);
        if (consultation == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        String sql = "UPDATE teleconsultations SET status = 'ongoing', started_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now));
            statement.setInt(2, consultation.getId());
            statement.executeUpdate();
            consultation.setStatus("ongoing");
            consultation.setStartedAt(now);
            notifyListeners("CONSULTATION_STARTED", consultation);
            return consultation;
        } catch (SQLException exception) {
            consultation.startConsultation();
            consultations.put(String.valueOf(consultation.getId()), consultation);
            notifyListeners("CONSULTATION_STARTED", consultation);
            return consultation;
        }
    }
    
    /**
     * End a teleconsultation session
     * @param consultationId The consultation ID
     * @param notes Optional notes from the professional
     * @return Updated Teleconsultation with COMPLETED status
     */
    public Teleconsultation endTeleconsultation(String consultationId, String notes) {
        Teleconsultation consultation = getTeleconsultation(consultationId);
        if (consultation == null) {
            return null;
        }
        LocalDateTime endedAt = LocalDateTime.now();
        Integer duration = consultation.getStartedAt() == null
            ? null
            : (int) ChronoUnit.SECONDS.between(consultation.getStartedAt(), endedAt);
        String sql = "UPDATE teleconsultations SET status = 'completed', ended_at = ?, duration_seconds = ?, description = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(endedAt));
            if (duration == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setInt(2, duration);
            }
            statement.setString(3, notes == null ? consultation.getDescription() : notes);
            statement.setInt(4, consultation.getId());
            statement.executeUpdate();

            consultation.setStatus("completed");
            consultation.setEndedAt(endedAt);
            consultation.setDurationSeconds(duration);
            if (notes != null) {
                consultation.setNotes(notes);
            }
            notifyListeners("CONSULTATION_ENDED", consultation);
            return consultation;
        } catch (SQLException exception) {
            consultation.endConsultation();
            if (notes != null) {
                consultation.setNotes(notes);
            }
            consultations.put(String.valueOf(consultation.getId()), consultation);
            notifyListeners("CONSULTATION_ENDED", consultation);
            return consultation;
        }
    }
    
    /**
     * Cancel a teleconsultation
     * @param consultationId The consultation ID
     * @return Updated Teleconsultation with CANCELLED status
     */
    public Teleconsultation cancelTeleconsultation(String consultationId) {
        Teleconsultation consultation = getTeleconsultation(consultationId);
        if (consultation == null) {
            return null;
        }
        LocalDateTime endedAt = LocalDateTime.now();
        Integer duration = consultation.getStartedAt() == null
            ? null
            : (int) ChronoUnit.SECONDS.between(consultation.getStartedAt(), endedAt);
        String sql = "UPDATE teleconsultations SET status = 'cancelled', ended_at = ?, duration_seconds = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(endedAt));
            if (duration == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setInt(2, duration);
            }
            statement.setInt(3, consultation.getId());
            statement.executeUpdate();

            consultation.setStatus("cancelled");
            consultation.setEndedAt(endedAt);
            consultation.setDurationSeconds(duration);
            notifyListeners("CONSULTATION_CANCELLED", consultation);
            return consultation;
        } catch (SQLException exception) {
            consultation.cancelConsultation();
            consultation.setEndedAt(endedAt);
            if (consultation.getStartedAt() != null) {
                consultation.setDurationSeconds((int) ChronoUnit.SECONDS.between(
                    consultation.getStartedAt(),
                    consultation.getEndedAt()
                ));
            }
            consultations.put(String.valueOf(consultation.getId()), consultation);
            notifyListeners("CONSULTATION_CANCELLED", consultation);
            return consultation;
        }
    }
    
    /**
     * Get a teleconsultation by ID
     * @param consultationId The consultation ID
     * @return Teleconsultation object or null
     */
    public Teleconsultation getTeleconsultation(String consultationId) {
        String sql = "SELECT id, initiator_id, recipient_id, room_name, description, scheduled_at, started_at, ended_at, status, duration_seconds, type, created_at "
            + "FROM teleconsultations WHERE id = ? LIMIT 1";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Integer.parseInt(consultationId));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapTeleconsultation(resultSet);
                }
            }
        } catch (Exception ignored) {
            return consultations.get(consultationId);
        }
        return null;
    }
    
    /**
     * Get all teleconsultations for a user (as patient)
     * @param patientId The patient's user ID
     * @return List of teleconsultations
     */
    public List<Teleconsultation> getTeleconsultationsForPatient(String patientId) {
        try {
            return findByUserAndSide(Integer.parseInt(patientId), false);
        } catch (NumberFormatException exception) {
            return List.of();
        }
    }
    
    /**
     * Get all teleconsultations for a professional
     * @param professionalId The professional's user ID
     * @return List of teleconsultations
     */
    public List<Teleconsultation> getTeleconsultationsForProfessional(String professionalId) {
        try {
            return findByUserAndSide(Integer.parseInt(professionalId), true);
        } catch (NumberFormatException exception) {
            return List.of();
        }
    }
    
    /**
     * Get all upcoming consultations for a professional
     * @param professionalId The professional's user ID
     * @return List of upcoming consultations
     */
    public List<Teleconsultation> getUpcomingConsultations(String professionalId) {
        try {
            String sql = "SELECT id, initiator_id, recipient_id, room_name, description, scheduled_at, started_at, ended_at, status, duration_seconds, type, created_at "
                + "FROM teleconsultations WHERE recipient_id = ? AND status IN ('pending','requested') AND scheduled_at >= ? ORDER BY scheduled_at ASC";
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, Integer.parseInt(professionalId));
                statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                List<Teleconsultation> list = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        Teleconsultation consultation = mapTeleconsultation(resultSet);
                        if (consultation != null) {
                            list.add(consultation);
                        }
                    }
                }
                return list;
            }
        } catch (Exception exception) {
            List<Teleconsultation> result = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            consultations.values().forEach(c -> {
                if (c.getInitiator() != null && professionalId.equals(String.valueOf(c.getInitiator().getId())) &&
                    c.getScheduledAt() != null && c.getScheduledAt().isAfter(now) &&
                    (c.isPending() || c.isRequested())) {
                    result.add(c);
                }
            });
            result.sort(Comparator.comparing(Teleconsultation::getScheduledAt));
            return result;
        }
    }
    
    /**
     * Get consultation history (completed consultations)
     * @param userId The user's ID (patient or professional)
     * @return List of completed consultations
     */
    public List<Teleconsultation> getConsultationHistory(String userId) {
        try {
            int id = Integer.parseInt(userId);
            String sql = "SELECT id, initiator_id, recipient_id, room_name, description, scheduled_at, started_at, ended_at, status, duration_seconds, type, created_at "
                + "FROM teleconsultations WHERE (initiator_id = ? OR recipient_id = ?) AND status = 'completed' ORDER BY ended_at DESC";
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                statement.setInt(2, id);
                List<Teleconsultation> list = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        Teleconsultation consultation = mapTeleconsultation(resultSet);
                        if (consultation != null) {
                            list.add(consultation);
                        }
                    }
                }
                return list;
            }
        } catch (Exception exception) {
            List<Teleconsultation> result = new ArrayList<>();
            consultations.values().forEach(c -> {
                if (isUserInConsultation(userId, c) && c.isCompleted()) {
                    result.add(c);
                }
            });
            result.sort(Comparator.comparing(Teleconsultation::getEndedAt).reversed());
            return result;
        }
    }
    
    /**
     * Reschedule a teleconsultation
     * @param consultationId The consultation ID
     * @param newScheduledAt New scheduled time
     * @return Updated Teleconsultation
     */
    public Teleconsultation rescheduleTeleconsultation(String consultationId, LocalDateTime newScheduledAt) {
        Teleconsultation consultation = getTeleconsultation(consultationId);
        if (consultation == null || newScheduledAt == null) {
            return null;
        }
        if (!(consultation.isPending() || consultation.isRequested())) {
            return null;
        }
        if (!newScheduledAt.isAfter(LocalDateTime.now())) {
            return null;
        }

        User professional = isProfessional(consultation.getInitiator()) ? consultation.getInitiator() : consultation.getRecipient();
        if (professional == null || !isDoctorAvailable(professional, newScheduledAt)) {
            return null;
        }

        consultation.setScheduledAt(newScheduledAt);
        if (consultation.isRequested()) {
            consultation.setStatus("pending");
        }
        String sql = "UPDATE teleconsultations SET scheduled_at = ?, status = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(newScheduledAt));
            statement.setString(2, consultation.getStatus());
            statement.setInt(3, consultation.getId());
            statement.executeUpdate();
        } catch (SQLException ignored) {
            consultations.put(String.valueOf(consultation.getId()), consultation);
        }
        notifyListeners("CONSULTATION_RESCHEDULED", consultation);
        return consultation;
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
        try {
            int id = Integer.parseInt(userId);
            String sql = "SELECT "
                + "SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completed, "
                + "SUM(CASE WHEN status IN ('pending','requested') THEN 1 ELSE 0 END) AS pending, "
                + "SUM(CASE WHEN status = 'cancelled' THEN 1 ELSE 0 END) AS cancelled, "
                + "SUM(CASE WHEN status = 'completed' THEN COALESCE(duration_seconds,0) ELSE 0 END) AS totalDuration, "
                + "AVG(CASE WHEN status = 'completed' THEN duration_seconds ELSE NULL END) AS avgDuration "
                + "FROM teleconsultations WHERE initiator_id = ? OR recipient_id = ?";
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                statement.setInt(2, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        stats.put("completed", resultSet.getLong("completed"));
                        stats.put("pending", resultSet.getLong("pending"));
                        stats.put("cancelled", resultSet.getLong("cancelled"));
                        stats.put("totalDuration", resultSet.getLong("totalDuration"));
                        stats.put("avgDuration", resultSet.getLong("avgDuration"));
                        return stats;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        List<Teleconsultation> userConsultations = consultations.values().stream()
            .filter(c -> isUserInConsultation(userId, c))
            .collect(Collectors.toList());

        long completed = userConsultations.stream().filter(Teleconsultation::isCompleted).count();
        long pending = userConsultations.stream()
            .filter(c -> (c.isPending() || c.isRequested()) && !isInPast(c.getScheduledAt()))
            .count();
        long cancelled = userConsultations.stream().filter(Teleconsultation::isCancel).count();
        long totalDuration = userConsultations.stream()
            .filter(Teleconsultation::isCompleted)
            .mapToLong(c -> c.getDurationSeconds() != null ? c.getDurationSeconds() : 0)
            .sum();
        long avgDuration = completed > 0 ? totalDuration / completed : 0;

        stats.put("completed", completed);
        stats.put("pending", pending);
        stats.put("cancelled", cancelled);
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
        if (user == null || user.getId() == null) {
            return List.of();
        }
        String sql = "SELECT id, initiator_id, recipient_id, room_name, description, scheduled_at, started_at, ended_at, status, duration_seconds, type, created_at "
            + "FROM teleconsultations WHERE (initiator_id = ? OR recipient_id = ?) AND status IN ('pending','requested') AND scheduled_at >= ? ORDER BY scheduled_at ASC";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            statement.setInt(2, user.getId());
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            List<Teleconsultation> list = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Teleconsultation consultation = mapTeleconsultation(resultSet);
                    if (consultation != null) {
                        list.add(consultation);
                    }
                }
            }
            return list;
        } catch (SQLException exception) {
            String userId = user.getId().toString();
            LocalDateTime now = LocalDateTime.now();
            return consultations.values().stream()
                .filter(c -> isUserInConsultation(userId, c))
                .filter(c -> c.isPending() || c.isRequested())
                .filter(c -> c.getScheduledAt() != null && c.getScheduledAt().isAfter(now))
                .sorted(Comparator.comparing(Teleconsultation::getScheduledAt))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Find ongoing consultations (ACTIVE status)
     * @param user User object
     * @return List of active consultations
     */
    public List<Teleconsultation> findOngoing(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        String sql = "SELECT id, initiator_id, recipient_id, room_name, description, scheduled_at, started_at, ended_at, status, duration_seconds, type, created_at "
            + "FROM teleconsultations WHERE (initiator_id = ? OR recipient_id = ?) AND status = 'ongoing' ORDER BY started_at DESC";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            statement.setInt(2, user.getId());
            List<Teleconsultation> list = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Teleconsultation consultation = mapTeleconsultation(resultSet);
                    if (consultation != null) {
                        list.add(consultation);
                    }
                }
            }
            return list;
        } catch (SQLException exception) {
            String userId = user.getId().toString();
            return consultations.values().stream()
                .filter(c -> isUserInConsultation(userId, c))
                .filter(Teleconsultation::isOngoing)
                .sorted(Comparator.comparing(Teleconsultation::getStartedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Find past consultations (COMPLETED status)
     * @param user User object
     * @return List of completed consultations sorted by endedAt descending
     */
    public List<Teleconsultation> findPast(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        String sql = "SELECT id, initiator_id, recipient_id, room_name, description, scheduled_at, started_at, ended_at, status, duration_seconds, type, created_at "
            + "FROM teleconsultations WHERE (initiator_id = ? OR recipient_id = ?) "
            + "AND (status IN ('completed','cancelled') OR (status = 'pending' AND scheduled_at < ?)) ORDER BY ended_at DESC";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            statement.setInt(2, user.getId());
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            List<Teleconsultation> list = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Teleconsultation consultation = mapTeleconsultation(resultSet);
                    if (consultation != null) {
                        list.add(consultation);
                    }
                }
            }
            return list;
        } catch (SQLException exception) {
            String userId = user.getId().toString();
            return consultations.values().stream()
                .filter(c -> isUserInConsultation(userId, c))
                .filter(c -> c.isCompleted() || c.isCancel() || (c.isPending() && isInPast(c.getScheduledAt())))
                .sorted(Comparator.comparing(Teleconsultation::getEndedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Check if a doctor/professional is available at specified time
     * (Simplified: check no other consultation at same time)
     * @param professional Professional user
     * @param dateTime Requested date/time
     * @return true if available
     */
    public boolean isDoctorAvailable(User professional, LocalDateTime dateTime) {
        if (professional == null || professional.getId() == null || dateTime == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) AS total FROM teleconsultations "
            + "WHERE (initiator_id = ? OR recipient_id = ?) "
            + "AND status IN ('pending','ongoing') "
            + "AND scheduled_at BETWEEN ? AND ?";
        LocalDateTime rangeStart = dateTime.minusMinutes(30);
        LocalDateTime rangeEnd = dateTime.plusMinutes(30);
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, professional.getId());
            statement.setInt(2, professional.getId());
            statement.setTimestamp(3, Timestamp.valueOf(rangeStart));
            statement.setTimestamp(4, Timestamp.valueOf(rangeEnd));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("total") == 0;
                }
            }
        } catch (SQLException ignored) {
        }

        String profId = professional.getId().toString();

        return consultations.values().stream()
            .filter(c -> profId.equals(c.getInitiator().getId().toString()) || 
                        profId.equals(c.getRecipient().getId().toString()))
            .filter(c -> c.isOngoing() || c.isPending() || c.isRequested())
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
        Teleconsultation consultation = getTeleconsultation(consultationId);
        if (consultation != null
            && consultation.isRequested()
            && consultation.getRecipient() != null
            && isProfessional(consultation.getRecipient())) {
            consultation.setStatus("pending");
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE teleconsultations SET status = 'pending' WHERE id = ?")) {
                statement.setInt(1, consultation.getId());
                statement.executeUpdate();
            } catch (SQLException ignored) {
                consultations.put(String.valueOf(consultation.getId()), consultation);
            }
            notifyListeners("CONSULTATION_APPROVED", consultation);
            return consultation;
        }
        return null;
    }

    public Teleconsultation rejectConsultation(String consultationId) {
        Teleconsultation consultation = getTeleconsultation(consultationId);
        if (consultation != null
            && consultation.isRequested()
            && consultation.getRecipient() != null
            && isProfessional(consultation.getRecipient())) {
            consultation.setStatus("cancelled");
            consultation.setEndedAt(LocalDateTime.now());
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE teleconsultations SET status = 'cancelled', ended_at = ? WHERE id = ?")) {
                statement.setTimestamp(1, Timestamp.valueOf(consultation.getEndedAt()));
                statement.setInt(2, consultation.getId());
                statement.executeUpdate();
            } catch (SQLException ignored) {
                consultations.put(String.valueOf(consultation.getId()), consultation);
            }
            notifyListeners("CONSULTATION_REJECTED", consultation);
            return consultation;
        }
        return null;
    }

    private List<Teleconsultation> findByUserAndSide(int userId, boolean initiatorSide) {
        String sql = "SELECT id, initiator_id, recipient_id, room_name, description, scheduled_at, started_at, ended_at, status, duration_seconds, type, created_at "
            + "FROM teleconsultations WHERE " + (initiatorSide ? "initiator_id" : "recipient_id") + " = ? ORDER BY scheduled_at ASC";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            List<Teleconsultation> list = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Teleconsultation consultation = mapTeleconsultation(resultSet);
                    if (consultation != null) {
                        list.add(consultation);
                    }
                }
            }
            return list;
        } catch (SQLException exception) {
            return consultations.values().stream()
                .filter(c -> {
                    User side = initiatorSide ? c.getInitiator() : c.getRecipient();
                    return side != null && side.getId() != null && side.getId().equals(userId);
                })
                .sorted(Comparator.comparing(Teleconsultation::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        }
    }

    private Teleconsultation mapTeleconsultation(ResultSet resultSet) throws SQLException {
        int initiatorId = resultSet.getInt("initiator_id");
        int recipientId = resultSet.getInt("recipient_id");
        Optional<User> initiator = userRepository.findById(initiatorId);
        Optional<User> recipient = userRepository.findById(recipientId);
        if (initiator.isEmpty() || recipient.isEmpty()) {
            return null;
        }

        Teleconsultation consultation = new Teleconsultation();
        consultation.setId(resultSet.getInt("id"));
        consultation.setInitiator(initiator.get());
        consultation.setRecipient(recipient.get());
        consultation.setRoomName(resultSet.getString("room_name"));
        consultation.setDescription(resultSet.getString("description"));
        consultation.setStatus(resultSet.getString("status"));
        consultation.setType(resultSet.getString("type"));

        Timestamp scheduledAt = resultSet.getTimestamp("scheduled_at");
        if (scheduledAt != null) {
            consultation.setScheduledAt(scheduledAt.toLocalDateTime());
        }
        Timestamp startedAt = resultSet.getTimestamp("started_at");
        if (startedAt != null) {
            consultation.setStartedAt(startedAt.toLocalDateTime());
        }
        Timestamp endedAt = resultSet.getTimestamp("ended_at");
        if (endedAt != null) {
            consultation.setEndedAt(endedAt.toLocalDateTime());
        }
        int duration = resultSet.getInt("duration_seconds");
        if (!resultSet.wasNull()) {
            consultation.setDurationSeconds(duration);
        }
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        if (createdAt != null) {
            consultation.setCreatedAt(createdAt.toLocalDateTime());
        }

        return consultation;
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
        if (userId == null || consultation == null) {
            return false;
        }
        User initiator = consultation.getInitiator();
        User recipient = consultation.getRecipient();
        return (initiator != null && initiator.getId() != null && userId.equals(initiator.getId().toString())) ||
               (recipient != null && recipient.getId() != null && userId.equals(recipient.getId().toString()));
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
