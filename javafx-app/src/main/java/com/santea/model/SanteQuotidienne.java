package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SanteQuotidienne {
    private Integer id;
    private User user;
    private Double poids;
    private Double taille;
    private Double imc;
    private Double tensionArterielle;
    private Double sommeil;
    private String activitePhysique;
    private List<Object> humeur;
    private String alimentation;
    private Double eauBue;
    private Integer pas;
    private Integer calories;
    private Integer dureeActiviteMinutes;
    private SanteDataSource sourceDonnees;
    private LocalDateTime date;

    public SanteQuotidienne() {
        this.humeur = new ArrayList<>();
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

    public Double getPoids() {
        return poids;
    }

    public void setPoids(Double poids) {
        this.poids = poids;
    }

    public Double getTaille() {
        return taille;
    }

    public void setTaille(Double taille) {
        this.taille = taille;
    }

    public Double getImc() {
        return imc;
    }

    public void setImc(Double imc) {
        this.imc = imc;
    }

    public Double getTensionArterielle() {
        return tensionArterielle;
    }

    public void setTensionArterielle(Double tensionArterielle) {
        this.tensionArterielle = tensionArterielle;
    }

    public Double getSommeil() {
        return sommeil;
    }

    public void setSommeil(Double sommeil) {
        this.sommeil = sommeil;
    }

    public String getActivitePhysique() {
        return activitePhysique;
    }

    public void setActivitePhysique(String activitePhysique) {
        this.activitePhysique = activitePhysique;
    }

    public List<Object> getHumeur() {
        return humeur;
    }

    public void setHumeur(List<Object> humeur) {
        this.humeur = humeur;
    }

    public String getAlimentation() {
        return alimentation;
    }

    public void setAlimentation(String alimentation) {
        this.alimentation = alimentation;
    }

    public Double getEauBue() {
        return eauBue;
    }

    public void setEauBue(Double eauBue) {
        this.eauBue = eauBue;
    }

    public Integer getPas() {
        return pas;
    }

    public void setPas(Integer pas) {
        this.pas = pas;
    }

    public Integer getCalories() {
        return calories;
    }

    public void setCalories(Integer calories) {
        this.calories = calories;
    }

    public Integer getDureeActiviteMinutes() {
        return dureeActiviteMinutes;
    }

    public void setDureeActiviteMinutes(Integer dureeActiviteMinutes) {
        this.dureeActiviteMinutes = dureeActiviteMinutes;
    }

    public SanteDataSource getSourceDonnees() {
        return sourceDonnees;
    }

    public void setSourceDonnees(SanteDataSource sourceDonnees) {
        this.sourceDonnees = sourceDonnees;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }
}
