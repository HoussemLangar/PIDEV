package com.santea.model;

import java.time.LocalDateTime;

public class PartageAnalyse {
    private Integer id;
    private Accompagnement accompagnement;
    private Patient patient;
    private String titre;
    private String description;
    private String fichierUrl;
    private LocalDateTime datePartage;
    private LocalDateTime createdAt;

    public PartageAnalyse() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Accompagnement getAccompagnement() {
        return accompagnement;
    }

    public void setAccompagnement(Accompagnement accompagnement) {
        this.accompagnement = accompagnement;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
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

    public String getFichierUrl() {
        return fichierUrl;
    }

    public void setFichierUrl(String fichierUrl) {
        this.fichierUrl = fichierUrl;
    }

    public LocalDateTime getDatePartage() {
        return datePartage;
    }

    public void setDatePartage(LocalDateTime datePartage) {
        this.datePartage = datePartage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
