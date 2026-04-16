package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccompanimentPlan {
    private Integer id;
    private Patient patient;
    private CoachSportif coach;
    private Nutritionniste nutritionist;
    private String title;
    private String objectives;
    private String description;
    private String status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer durationWeeks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PlanExercice> exercisePlans;
    private List<PlanRegime> dietPlans;

    public AccompanimentPlan() {
        this.exercisePlans = new ArrayList<>();
        this.dietPlans = new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public CoachSportif getCoach() {
        return coach;
    }

    public void setCoach(CoachSportif coach) {
        this.coach = coach;
    }

    public Nutritionniste getNutritionist() {
        return nutritionist;
    }

    public void setNutritionist(Nutritionniste nutritionist) {
        this.nutritionist = nutritionist;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getObjectives() {
        return objectives;
    }

    public void setObjectives(String objectives) {
        this.objectives = objectives;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public Integer getDurationWeeks() {
        return durationWeeks;
    }

    public void setDurationWeeks(Integer durationWeeks) {
        this.durationWeeks = durationWeeks;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PlanExercice> getExercisePlans() {
        return exercisePlans;
    }

    public void setExercisePlans(List<PlanExercice> exercisePlans) {
        this.exercisePlans = exercisePlans;
    }

    public List<PlanRegime> getDietPlans() {
        return dietPlans;
    }

    public void setDietPlans(List<PlanRegime> dietPlans) {
        this.dietPlans = dietPlans;
    }

    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }

    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status);
    }

    public boolean isCancelled() {
        return "cancelled".equalsIgnoreCase(status);
    }

    public int getProgressPercentage() {
        if (isCompleted()) {
            return 100;
        }
        if (isCancelled()) {
            return 0;
        }
        if (startDate == null || endDate == null) {
            return isActive() ? 35 : 15;
        }
        long totalSeconds = java.time.Duration.between(startDate, endDate).getSeconds();
        if (totalSeconds <= 0) {
            return isActive() ? 50 : 15;
        }
        long elapsedSeconds = java.time.Duration.between(startDate, LocalDateTime.now()).getSeconds();
        int progress = (int) Math.round((elapsedSeconds * 100.0) / totalSeconds);
        if (isActive()) {
            return Math.max(5, Math.min(progress, 95));
        }
        return Math.max(0, Math.min(progress, 100));
    }
}
