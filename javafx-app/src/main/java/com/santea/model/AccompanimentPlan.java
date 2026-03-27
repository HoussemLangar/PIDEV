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
    private List<Object> exercisePlans;
    private List<Object> dietPlans;

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

    public List<Object> getExercisePlans() {
        return exercisePlans;
    }

    public void setExercisePlans(List<Object> exercisePlans) {
        this.exercisePlans = exercisePlans;
    }

    public List<Object> getDietPlans() {
        return dietPlans;
    }

    public void setDietPlans(List<Object> dietPlans) {
        this.dietPlans = dietPlans;
    }
}
