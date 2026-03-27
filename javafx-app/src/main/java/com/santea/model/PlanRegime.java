package com.santea.model;

import java.time.LocalDateTime;

public class PlanRegime {
    private Integer id;
    private Patient patient;
    private Accompagnement accompagnement;
    private AccompanimentPlan plan;
    private String titre;
    private String description;
    private String typeRegime;
    private String objectif;
    private String restrictions;
    private Integer caloriesJour;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PlanRegime() {
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

    public String getTypeRegime() {
        return typeRegime;
    }

    public void setTypeRegime(String typeRegime) {
        this.typeRegime = typeRegime;
    }

    public String getObjectif() {
        return objectif;
    }

    public void setObjectif(String objectif) {
        this.objectif = objectif;
    }

    public String getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(String restrictions) {
        this.restrictions = restrictions;
    }

    public Integer getCaloriesJour() {
        return caloriesJour;
    }

    public void setCaloriesJour(Integer caloriesJour) {
        this.caloriesJour = caloriesJour;
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
