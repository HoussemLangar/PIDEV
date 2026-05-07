package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class User {
    private Integer id;
    private String username;
    private String email;
    private String password;
    private String nom;
    private String prenom;
    private LocalDateTime dateNaissance;
    private String adresse;
    private String telephone;
    private String role;
    private Boolean emailVerified;
    private Boolean adminApproved;
    private Boolean mfaEnabled;
    private String googleAuthenticatorSecret;
    private String themePreference;
    private String locale;
    private String avatarData;
    private String avatarMime;
    private Boolean reminderEnabled;
    private String emailVerificationToken;
    private LocalDateTime emailVerificationExpiresAt;
    private String subscriptionStatus;
    private String subscriptionType;
    private LocalDateTime subscriptionEndAt;
    private LocalDateTime aiFreeMonthGrantedAt;
    private Boolean isBanned;
    private String banReason;
    private LocalDateTime banUntil;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Patient patient;
    private Medecin medecin;
    private Pharmacien pharmacien;
    private CoachSportif coachSportif;
    private Nutritionniste nutritionniste;
    private GoogleFitAccount googleFitAccount;
    private List<Object> contenus;
    private List<Object> likes;
    private List<Object> commentaires;
    private List<Object> reponsesMedicaments;
    private List<Object> notifications;
    private List<Object> santeQuotidiennes;

    public User() {
        this.contenus = new ArrayList<>();
        this.likes = new ArrayList<>();
        this.commentaires = new ArrayList<>();
        this.reponsesMedicaments = new ArrayList<>();
        this.notifications = new ArrayList<>();
        this.santeQuotidiennes = new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public LocalDateTime getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(LocalDateTime dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Boolean getAdminApproved() {
        return adminApproved;
    }

    public void setAdminApproved(Boolean adminApproved) {
        this.adminApproved = adminApproved;
    }

    public Boolean getMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(Boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public String getGoogleAuthenticatorSecret() {
        return googleAuthenticatorSecret;
    }

    public void setGoogleAuthenticatorSecret(String googleAuthenticatorSecret) {
        this.googleAuthenticatorSecret = googleAuthenticatorSecret;
    }

    public String getThemePreference() {
        return themePreference;
    }

    public void setThemePreference(String themePreference) {
        this.themePreference = themePreference;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getAvatarData() {
        return avatarData;
    }

    public void setAvatarData(String avatarData) {
        this.avatarData = avatarData;
    }

    public String getAvatarMime() {
        return avatarMime;
    }

    public void setAvatarMime(String avatarMime) {
        this.avatarMime = avatarMime;
    }

    public Boolean getReminderEnabled() {
        return reminderEnabled;
    }

    public void setReminderEnabled(Boolean reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

    public String getEmailVerificationToken() {
        return emailVerificationToken;
    }

    public void setEmailVerificationToken(String emailVerificationToken) {
        this.emailVerificationToken = emailVerificationToken;
    }

    public LocalDateTime getEmailVerificationExpiresAt() {
        return emailVerificationExpiresAt;
    }

    public void setEmailVerificationExpiresAt(LocalDateTime emailVerificationExpiresAt) {
        this.emailVerificationExpiresAt = emailVerificationExpiresAt;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(String subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public String getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(String subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public LocalDateTime getSubscriptionEndAt() {
        return subscriptionEndAt;
    }

    public void setSubscriptionEndAt(LocalDateTime subscriptionEndAt) {
        this.subscriptionEndAt = subscriptionEndAt;
    }

    public LocalDateTime getAiFreeMonthGrantedAt() {
        return aiFreeMonthGrantedAt;
    }

    public void setAiFreeMonthGrantedAt(LocalDateTime aiFreeMonthGrantedAt) {
        this.aiFreeMonthGrantedAt = aiFreeMonthGrantedAt;
    }

    public Boolean getIsBanned() {
        return isBanned;
    }

    public void setIsBanned(Boolean isBanned) {
        this.isBanned = isBanned;
    }

    public String getBanReason() {
        return banReason;
    }

    public void setBanReason(String banReason) {
        this.banReason = banReason;
    }

    public LocalDateTime getBanUntil() {
        return banUntil;
    }

    public void setBanUntil(LocalDateTime banUntil) {
        this.banUntil = banUntil;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
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

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Medecin getMedecin() {
        return medecin;
    }

    public void setMedecin(Medecin medecin) {
        this.medecin = medecin;
    }

    public Pharmacien getPharmacien() {
        return pharmacien;
    }

    public void setPharmacien(Pharmacien pharmacien) {
        this.pharmacien = pharmacien;
    }

    public CoachSportif getCoachSportif() {
        return coachSportif;
    }

    public void setCoachSportif(CoachSportif coachSportif) {
        this.coachSportif = coachSportif;
    }

    public Nutritionniste getNutritionniste() {
        return nutritionniste;
    }

    public void setNutritionniste(Nutritionniste nutritionniste) {
        this.nutritionniste = nutritionniste;
    }

    public GoogleFitAccount getGoogleFitAccount() {
        return googleFitAccount;
    }

    public void setGoogleFitAccount(GoogleFitAccount googleFitAccount) {
        this.googleFitAccount = googleFitAccount;
    }

    public List<Object> getContenus() {
        return contenus;
    }

    public void setContenus(List<Object> contenus) {
        this.contenus = contenus;
    }

    public List<Object> getLikes() {
        return likes;
    }

    public void setLikes(List<Object> likes) {
        this.likes = likes;
    }

    public List<Object> getCommentaires() {
        return commentaires;
    }

    public void setCommentaires(List<Object> commentaires) {
        this.commentaires = commentaires;
    }

    public List<Object> getReponsesMedicaments() {
        return reponsesMedicaments;
    }

    public void setReponsesMedicaments(List<Object> reponsesMedicaments) {
        this.reponsesMedicaments = reponsesMedicaments;
    }

    public List<Object> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<Object> notifications) {
        this.notifications = notifications;
    }

    public List<Object> getSanteQuotidiennes() {
        return santeQuotidiennes;
    }

    public void setSanteQuotidiennes(List<Object> santeQuotidiennes) {
        this.santeQuotidiennes = santeQuotidiennes;
    }
}
