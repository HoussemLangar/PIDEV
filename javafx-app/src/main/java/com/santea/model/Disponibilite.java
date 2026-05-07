package com.santea.model;

import java.time.LocalDateTime;

public class Disponibilite {
    private Integer id;
    private Medecin medecin;
    private LocalDateTime date;
    private LocalDateTime heureDebut;
    private LocalDateTime heureFin;
    private String statut;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private RendezVous rendezvous;

    public Disponibilite() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Medecin getMedecin() {
        return medecin;
    }

    public void setMedecin(Medecin medecin) {
        this.medecin = medecin;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public LocalDateTime getHeureDebut() {
        return heureDebut;
    }

    public void setHeureDebut(LocalDateTime heureDebut) {
        this.heureDebut = heureDebut;
    }

    public LocalDateTime getHeureFin() {
        return heureFin;
    }

    public void setHeureFin(LocalDateTime heureFin) {
        this.heureFin = heureFin;
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

    public RendezVous getRendezvous() {
        return rendezvous;
    }

    public void setRendezvous(RendezVous rendezvous) {
        this.rendezvous = rendezvous;
    }
}
