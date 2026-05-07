package com.santea.model;

import java.time.LocalDateTime;

public class ReponseMedicament {
    private Integer id;
    private Medicament medicament;
    private User user;
    private String question;
    private String reponse;
    private LocalDateTime dateQuestion;
    private LocalDateTime dateReponse;
    private String statut;
    private LocalDateTime createdAt;

    public ReponseMedicament() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Medicament getMedicament() {
        return medicament;
    }

    public void setMedicament(Medicament medicament) {
        this.medicament = medicament;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return reponse;
    }

    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public LocalDateTime getDateQuestion() {
        return dateQuestion;
    }

    public void setDateQuestion(LocalDateTime dateQuestion) {
        this.dateQuestion = dateQuestion;
    }

    public LocalDateTime getDateReponse() {
        return dateReponse;
    }

    public void setDateReponse(LocalDateTime dateReponse) {
        this.dateReponse = dateReponse;
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
}
