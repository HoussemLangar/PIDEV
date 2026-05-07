package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Pharmacien {
    private Integer id;
    private User user;
    private String numeroOrdre;
    private String pharmacieNom;
    private String pharmacieAdresse;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Object> pharmacies;

    public Pharmacien() {
        this.pharmacies = new ArrayList<>();
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

    public String getNumeroOrdre() {
        return numeroOrdre;
    }

    public void setNumeroOrdre(String numeroOrdre) {
        this.numeroOrdre = numeroOrdre;
    }

    public String getPharmacieNom() {
        return pharmacieNom;
    }

    public void setPharmacieNom(String pharmacieNom) {
        this.pharmacieNom = pharmacieNom;
    }

    public String getPharmacieAdresse() {
        return pharmacieAdresse;
    }

    public void setPharmacieAdresse(String pharmacieAdresse) {
        this.pharmacieAdresse = pharmacieAdresse;
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

    public List<Object> getPharmacies() {
        return pharmacies;
    }

    public void setPharmacies(List<Object> pharmacies) {
        this.pharmacies = pharmacies;
    }
}
