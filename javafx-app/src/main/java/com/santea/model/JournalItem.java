package com.santea.model;

import java.time.LocalDateTime;

public class JournalItem {
    private Integer id;
    private Patient patient;
    private String titre;
    private String description;
    private String type;
    private LocalDateTime dateJournal;
    private LocalDateTime heure;
    private String valeur;
    private String unite;
    private String humeur;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public JournalItem() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDateTime getDateJournal() {
        return dateJournal;
    }

    public void setDateJournal(LocalDateTime dateJournal) {
        this.dateJournal = dateJournal;
    }

    public LocalDateTime getHeure() {
        return heure;
    }

    public void setHeure(LocalDateTime heure) {
        this.heure = heure;
    }

    public String getValeur() {
        return valeur;
    }

    public void setValeur(String valeur) {
        this.valeur = valeur;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    public String getHumeur() {
        return humeur;
    }

    public void setHumeur(String humeur) {
        this.humeur = humeur;
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
