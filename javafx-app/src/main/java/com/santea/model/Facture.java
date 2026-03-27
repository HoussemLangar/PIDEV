package com.santea.model;

import java.time.LocalDateTime;

public class Facture {
    private Integer id;
    private String numero;
    private User user;
    private Abonnement abonnement;
    private String montantHt;
    private String tvaTaux;
    private String tvaMontant;
    private String montantTtc;
    private String devise;
    private String pdfPath;
    private LocalDateTime createdAt;

    public Facture() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Abonnement getAbonnement() {
        return abonnement;
    }

    public void setAbonnement(Abonnement abonnement) {
        this.abonnement = abonnement;
    }

    public String getMontantHt() {
        return montantHt;
    }

    public void setMontantHt(String montantHt) {
        this.montantHt = montantHt;
    }

    public String getTvaTaux() {
        return tvaTaux;
    }

    public void setTvaTaux(String tvaTaux) {
        this.tvaTaux = tvaTaux;
    }

    public String getTvaMontant() {
        return tvaMontant;
    }

    public void setTvaMontant(String tvaMontant) {
        this.tvaMontant = tvaMontant;
    }

    public String getMontantTtc() {
        return montantTtc;
    }

    public void setMontantTtc(String montantTtc) {
        this.montantTtc = montantTtc;
    }

    public String getDevise() {
        return devise;
    }

    public void setDevise(String devise) {
        this.devise = devise;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
