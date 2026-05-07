package com.santea.model;

import java.time.LocalDateTime;

public class RapportMedical {
    private Integer id;
    private Patient patient;
    private Medecin medecin;
    private String titre;
    private String diagnostic;
    private String traitement;
    private String observations;
    private LocalDateTime dateRapport;
    private String fichierPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RapportMedical() {
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

    public Medecin getMedecin() {
        return medecin;
    }

    public void setMedecin(Medecin medecin) {
        this.medecin = medecin;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public void setDiagnostic(String diagnostic) {
        this.diagnostic = diagnostic;
    }

    public String getTraitement() {
        return traitement;
    }

    public void setTraitement(String traitement) {
        this.traitement = traitement;
    }

    public String getObservations() {
        return observations;
    }

    public void setObservations(String observations) {
        this.observations = observations;
    }

    public LocalDateTime getDateRapport() {
        return dateRapport;
    }

    public void setDateRapport(LocalDateTime dateRapport) {
        this.dateRapport = dateRapport;
    }

    public String getFichierPath() {
        return fichierPath;
    }

    public void setFichierPath(String fichierPath) {
        this.fichierPath = fichierPath;
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
