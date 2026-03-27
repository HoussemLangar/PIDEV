package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SymptomeListe {
    private Integer id;
    private String nom;
    private String categorie;
    private LocalDateTime createdAt;
    private List<Object> symptomesQuotidiens;

    public SymptomeListe() {
        this.symptomesQuotidiens = new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getCategorie() {
        return categorie;
    }

    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Object> getSymptomesQuotidiens() {
        return symptomesQuotidiens;
    }

    public void setSymptomesQuotidiens(List<Object> symptomesQuotidiens) {
        this.symptomesQuotidiens = symptomesQuotidiens;
    }
}
