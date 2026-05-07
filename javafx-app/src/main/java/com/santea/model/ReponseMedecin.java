package com.santea.model;

import java.time.LocalDateTime;

public class ReponseMedecin {
    private Integer id;
    private PartageAnalyse partage;
    private Medecin medecin;
    private String reponse;
    private LocalDateTime dateReponse;
    private LocalDateTime createdAt;

    public ReponseMedecin() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public PartageAnalyse getPartage() {
        return partage;
    }

    public void setPartage(PartageAnalyse partage) {
        this.partage = partage;
    }

    public Medecin getMedecin() {
        return medecin;
    }

    public void setMedecin(Medecin medecin) {
        this.medecin = medecin;
    }

    public String getReponse() {
        return reponse;
    }

    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public LocalDateTime getDateReponse() {
        return dateReponse;
    }

    public void setDateReponse(LocalDateTime dateReponse) {
        this.dateReponse = dateReponse;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
