package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Medicament {
    private Integer id;
    private String nom;
    private String type;
    private String description;
    private String forme;
    private String dosage;
    private String prix;
    private Integer stock;
    private String laboratoire;
    private String codeBarre;
    private String imageName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Object> stockPharmacies;
    private List<Object> reponsesMedicaments;

    public Medicament() {
        this.stockPharmacies = new ArrayList<>();
        this.reponsesMedicaments = new ArrayList<>();
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getForme() {
        return forme;
    }

    public void setForme(String forme) {
        this.forme = forme;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getPrix() {
        return prix;
    }

    public void setPrix(String prix) {
        this.prix = prix;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getLaboratoire() {
        return laboratoire;
    }

    public void setLaboratoire(String laboratoire) {
        this.laboratoire = laboratoire;
    }

    public String getCodeBarre() {
        return codeBarre;
    }

    public void setCodeBarre(String codeBarre) {
        this.codeBarre = codeBarre;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
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

    public List<Object> getStockPharmacies() {
        return stockPharmacies;
    }

    public void setStockPharmacies(List<Object> stockPharmacies) {
        this.stockPharmacies = stockPharmacies;
    }

    public List<Object> getReponsesMedicaments() {
        return reponsesMedicaments;
    }

    public void setReponsesMedicaments(List<Object> reponsesMedicaments) {
        this.reponsesMedicaments = reponsesMedicaments;
    }
}
