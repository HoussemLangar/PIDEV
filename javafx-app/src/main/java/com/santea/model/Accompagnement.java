package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Accompagnement {
    private Integer id;
    private Abonnement abonnement;
    private String nom;
    private String description;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String typeAccompagnement;
    private String statut;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Object> plansExercices;
    private List<Object> plansRegimes;

    public Accompagnement() {
        this.plansExercices = new ArrayList<>();
        this.plansRegimes = new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Abonnement getAbonnement() {
        return abonnement;
    }

    public void setAbonnement(Abonnement abonnement) {
        this.abonnement = abonnement;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDateTime dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDateTime getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDateTime dateFin) {
        this.dateFin = dateFin;
    }

    public String getTypeAccompagnement() {
        return typeAccompagnement;
    }

    public void setTypeAccompagnement(String typeAccompagnement) {
        this.typeAccompagnement = typeAccompagnement;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
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

    public List<Object> getPlansExercices() {
        return plansExercices;
    }

    public void setPlansExercices(List<Object> plansExercices) {
        this.plansExercices = plansExercices;
    }

    public List<Object> getPlansRegimes() {
        return plansRegimes;
    }

    public void setPlansRegimes(List<Object> plansRegimes) {
        this.plansRegimes = plansRegimes;
    }
}
