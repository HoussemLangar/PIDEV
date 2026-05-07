package com.santea.model;

import java.time.LocalDateTime;

public class Teleconsultation {
    private Integer id;
    private User initiator;
    private User recipient;
    private String roomName;
    private String description;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String status;
    private Integer durationSeconds;
    private String type;
    private LocalDateTime createdAt;

    public Teleconsultation() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getInitiator() {
        return initiator;
    }

    public void setInitiator(User initiator) {
        this.initiator = initiator;
    }

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User recipient) {
        this.recipient = recipient;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getJitsiRoomName() {
        return roomName;
    }

    public void setJitsiRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getJitsiRoomUrl() {
        return "https://meet.jit.si/" + (roomName != null ? roomName : "");
    }

    public Integer getPatientId() {
        return recipient != null ? recipient.getId() : null;
    }

    public Integer getProfessionalId() {
        return initiator != null ? initiator.getId() : null;
    }

    public Integer getDurationMinutes() {
        if (durationSeconds == null) return 0;
        return durationSeconds / 60;
    }

    public void startConsultation() {
        this.status = "ongoing";
        this.startedAt = LocalDateTime.now();
    }

    public void endConsultation() {
        this.status = "completed";
        this.endedAt = LocalDateTime.now();
        if (startedAt != null) {
            this.durationSeconds = (int) java.time.temporal.ChronoUnit.SECONDS.between(startedAt, endedAt);
        }
    }

    public void cancelConsultation() {
        this.status = "cancelled";
    }

    public boolean isActive() {
        return isOngoing();
    }

    public boolean isOngoing() {
        return "ongoing".equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return "pending".equalsIgnoreCase(status);
    }

    public boolean isRequested() {
        return "requested".equalsIgnoreCase(status);
    }

    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status);
    }

    public boolean isCancel() {
        return "cancelled".equalsIgnoreCase(status);
    }

    public String getDurationFormatted() {
        if (durationSeconds == null || durationSeconds <= 0) {
            return "-";
        }

        int hours = durationSeconds / 3600;
        int minutes = (durationSeconds % 3600) / 60;
        int seconds = durationSeconds % 60;
        StringBuilder out = new StringBuilder();
        if (hours > 0) {
            out.append(hours).append("h");
        }
        if (minutes > 0) {
            if (out.length() > 0) out.append(" ");
            out.append(minutes).append("m");
        }
        if (seconds > 0 || out.length() == 0) {
            if (out.length() > 0) out.append(" ");
            out.append(seconds).append("s");
        }
        return out.toString();
    }

    public String getNotes() {
        return description;
    }

    public void setNotes(String notes) {
        this.description = notes;
    }

    public LocalDateTime getUpdatedAt() {
        return endedAt != null ? endedAt : startedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        if (endedAt == null) {
            this.endedAt = updatedAt;
        }
    }
}
