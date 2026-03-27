package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Medecin {
    private Integer id;
    private User user;
    private String specialite;
    private String numeroOrdre;
    private String cabinetAdresse;
    private String cabinetVille;
    private String cabinetLat;
    private String cabinetLng;
    private String telephoneCabinet;
    private String tarifConsultation;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Object> disponibilites;
    private List<Object> rendezVous;
    private List<Object> rapportsAnalyses;
    private List<Object> rapportsMedicaux;

    public Medecin() {
        this.disponibilites = new ArrayList<>();
        this.rendezVous = new ArrayList<>();
        this.rapportsAnalyses = new ArrayList<>();
        this.rapportsMedicaux = new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSpecialite() {
        return specialite;
    }

    public void setSpecialite(String specialite) {
        this.specialite = specialite;
    }

    public String getNumeroOrdre() {
        return numeroOrdre;
    }

    public void setNumeroOrdre(String numeroOrdre) {
        this.numeroOrdre = numeroOrdre;
    }

    public String getCabinetAdresse() {
        return cabinetAdresse;
    }

    public void setCabinetAdresse(String cabinetAdresse) {
        this.cabinetAdresse = cabinetAdresse;
    }

    public String getCabinetVille() {
        return cabinetVille;
    }

    public void setCabinetVille(String cabinetVille) {
        this.cabinetVille = cabinetVille;
    }

    public String getCabinetLat() {
        return cabinetLat;
    }

    public void setCabinetLat(String cabinetLat) {
        this.cabinetLat = cabinetLat;
    }

    public String getCabinetLng() {
        return cabinetLng;
    }

    public void setCabinetLng(String cabinetLng) {
        this.cabinetLng = cabinetLng;
    }

    public String getTelephoneCabinet() {
        return telephoneCabinet;
    }

    public void setTelephoneCabinet(String telephoneCabinet) {
        this.telephoneCabinet = telephoneCabinet;
    }

    public String getTarifConsultation() {
        return tarifConsultation;
    }

    public void setTarifConsultation(String tarifConsultation) {
        this.tarifConsultation = tarifConsultation;
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

    public List<Object> getDisponibilites() {
        return disponibilites;
    }

    public void setDisponibilites(List<Object> disponibilites) {
        this.disponibilites = disponibilites;
    }

    public List<Object> getRendezVous() {
        return rendezVous;
    }

    public void setRendezVous(List<Object> rendezVous) {
        this.rendezVous = rendezVous;
    }

    public List<Object> getRapportsAnalyses() {
        return rapportsAnalyses;
    }

    public void setRapportsAnalyses(List<Object> rapportsAnalyses) {
        this.rapportsAnalyses = rapportsAnalyses;
    }

    public List<Object> getRapportsMedicaux() {
        return rapportsMedicaux;
    }

    public void setRapportsMedicaux(List<Object> rapportsMedicaux) {
        this.rapportsMedicaux = rapportsMedicaux;
    }
}
