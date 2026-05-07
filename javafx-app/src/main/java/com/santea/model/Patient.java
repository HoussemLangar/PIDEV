package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Patient {
    private Integer id;
    private User user;
    private String numeroSecu;
    private String groupeSanguin;
    private String allergies;
    private String antecedentsMedicaux;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Object> rendezVous;
    private List<Object> rapportsAnalyses;
    private List<Object> rapportsMedicaux;
    private List<Object> journalItems;
    private List<Object> symptomesQuotidiens;
    private List<Object> plansExercices;
    private List<Object> plansRegimes;

    public Patient() {
        this.rendezVous = new ArrayList<>();
        this.rapportsAnalyses = new ArrayList<>();
        this.rapportsMedicaux = new ArrayList<>();
        this.journalItems = new ArrayList<>();
        this.symptomesQuotidiens = new ArrayList<>();
        this.plansExercices = new ArrayList<>();
        this.plansRegimes = new ArrayList<>();
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

    public String getNumeroSecu() {
        return numeroSecu;
    }

    public void setNumeroSecu(String numeroSecu) {
        this.numeroSecu = numeroSecu;
    }

    public String getGroupeSanguin() {
        return groupeSanguin;
    }

    public void setGroupeSanguin(String groupeSanguin) {
        this.groupeSanguin = groupeSanguin;
    }

    public String getAllergies() {
        return allergies;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }

    public String getAntecedentsMedicaux() {
        return antecedentsMedicaux;
    }

    public void setAntecedentsMedicaux(String antecedentsMedicaux) {
        this.antecedentsMedicaux = antecedentsMedicaux;
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

    public List<Object> getJournalItems() {
        return journalItems;
    }

    public void setJournalItems(List<Object> journalItems) {
        this.journalItems = journalItems;
    }

    public List<Object> getSymptomesQuotidiens() {
        return symptomesQuotidiens;
    }

    public void setSymptomesQuotidiens(List<Object> symptomesQuotidiens) {
        this.symptomesQuotidiens = symptomesQuotidiens;
    }

    public List<Object> getPlansExercices() {
        return plansExercices;
    }

    public void setPlansExercices(List<Object> plansExercices) {
        this.plansExercices = plansExercices;
    }

    public List<Object> getPlansRegimes() {
        return plansRegimes;
    }

    public void setPlansRegimes(List<Object> plansRegimes) {
        this.plansRegimes = plansRegimes;
    }
}
