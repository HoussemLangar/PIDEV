package com.santea.model;

import java.time.LocalDateTime;

public class SymptomeQuotidien {
    private Integer id;
    private Patient patient;
    private SymptomeListe symptome;
    private LocalDateTime dateSymptome;
    private Integer intensite;
    private String duree;
    private String notes;
    private LocalDateTime createdAt;

    public SymptomeQuotidien() {
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

    public SymptomeListe getSymptome() {
        return symptome;
    }

    public void setSymptome(SymptomeListe symptome) {
        this.symptome = symptome;
    }

    public LocalDateTime getDateSymptome() {
        return dateSymptome;
    }

    public void setDateSymptome(LocalDateTime dateSymptome) {
        this.dateSymptome = dateSymptome;
    }

    public Integer getIntensite() {
        return intensite;
    }

    public void setIntensite(Integer intensite) {
        this.intensite = intensite;
    }

    public String getDuree() {
        return duree;
    }

    public void setDuree(String duree) {
        this.duree = duree;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
