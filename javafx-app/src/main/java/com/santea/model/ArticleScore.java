package com.santea.model;

import java.time.LocalDateTime;

public class ArticleScore {
    private Contenu contenu;
    private Double scoreArticle;
    private Integer nbCommentaires;
    private LocalDateTime updatedAt;

    public ArticleScore() {
    }

    public Contenu getContenu() {
        return contenu;
    }

    public void setContenu(Contenu contenu) {
        this.contenu = contenu;
    }

    public Double getScoreArticle() {
        return scoreArticle;
    }

    public void setScoreArticle(Double scoreArticle) {
        this.scoreArticle = scoreArticle;
    }

    public Integer getNbCommentaires() {
        return nbCommentaires;
    }

    public void setNbCommentaires(Integer nbCommentaires) {
        this.nbCommentaires = nbCommentaires;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
