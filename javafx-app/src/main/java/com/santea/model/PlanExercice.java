package com.santea.model;

import java.time.LocalDateTime;

public class PlanExercice {
    private Integer id;
    private Patient patient;
    private Accompagnement accompagnement;
    private AccompanimentPlan plan;
    private String titre;
    private String description;
    private String frequence;
    private Integer dureMinutes;
    private String niveau;
    private String objectifs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PlanExercice() {
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

    public Accompagnement getAccompagnement() {
        return accompagnement;
    }

    public void setAccompagnement(Accompagnement accompagnement) {
        this.accompagnement = accompagnement;
    }

    public AccompanimentPlan getPlan() {
        return plan;
    }

    public void setPlan(AccompanimentPlan plan) {
        this.plan = plan;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFrequence() {
        return frequence;
    }

    public void setFrequence(String frequence) {
        this.frequence = frequence;
    }

    public Integer getDureMinutes() {
        return dureMinutes;
    }

    public void setDureMinutes(Integer dureMinutes) {
        this.dureMinutes = dureMinutes;
    }

    public String getNiveau() {
        return niveau;
    }

    public void setNiveau(String niveau) {
        this.niveau = niveau;
    }

    public String getObjectifs() {
        return objectifs;
    }

    public void setObjectifs(String objectifs) {
        this.objectifs = objectifs;
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
}
