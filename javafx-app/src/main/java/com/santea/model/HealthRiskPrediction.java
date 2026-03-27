package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HealthRiskPrediction {
    private Integer id;
    private User user;
    private LocalDateTime predictionDate;
    private Double riskHtn;
    private Double riskDiabetes;
    private Double riskDepression;
    private Double riskRespiratory;
    private String levelHtn;
    private String levelDiabetes;
    private String levelDepression;
    private String levelRespiratory;
    private List<Object> explanationsJson;
    private List<Object> featureSnapshot;
    private LocalDateTime updatedAt;

    public HealthRiskPrediction() {
        this.explanationsJson = new ArrayList<>();
        this.featureSnapshot = new ArrayList<>();
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

    public LocalDateTime getPredictionDate() {
        return predictionDate;
    }

    public void setPredictionDate(LocalDateTime predictionDate) {
        this.predictionDate = predictionDate;
    }

    public Double getRiskHtn() {
        return riskHtn;
    }

    public void setRiskHtn(Double riskHtn) {
        this.riskHtn = riskHtn;
    }

    public Double getRiskDiabetes() {
        return riskDiabetes;
    }

    public void setRiskDiabetes(Double riskDiabetes) {
        this.riskDiabetes = riskDiabetes;
    }

    public Double getRiskDepression() {
        return riskDepression;
    }

    public void setRiskDepression(Double riskDepression) {
        this.riskDepression = riskDepression;
    }

    public Double getRiskRespiratory() {
        return riskRespiratory;
    }

    public void setRiskRespiratory(Double riskRespiratory) {
        this.riskRespiratory = riskRespiratory;
    }

    public String getLevelHtn() {
        return levelHtn;
    }

    public void setLevelHtn(String levelHtn) {
        this.levelHtn = levelHtn;
    }

    public String getLevelDiabetes() {
        return levelDiabetes;
    }

    public void setLevelDiabetes(String levelDiabetes) {
        this.levelDiabetes = levelDiabetes;
    }

    public String getLevelDepression() {
        return levelDepression;
    }

    public void setLevelDepression(String levelDepression) {
        this.levelDepression = levelDepression;
    }

    public String getLevelRespiratory() {
        return levelRespiratory;
    }

    public void setLevelRespiratory(String levelRespiratory) {
        this.levelRespiratory = levelRespiratory;
    }

    public List<Object> getExplanationsJson() {
        return explanationsJson;
    }

    public void setExplanationsJson(List<Object> explanationsJson) {
        this.explanationsJson = explanationsJson;
    }

    public List<Object> getFeatureSnapshot() {
        return featureSnapshot;
    }

    public void setFeatureSnapshot(List<Object> featureSnapshot) {
        this.featureSnapshot = featureSnapshot;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
