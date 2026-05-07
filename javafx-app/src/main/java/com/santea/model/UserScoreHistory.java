package com.santea.model;

import java.time.LocalDateTime;

public class UserScoreHistory {
    private Integer id;
    private User user;
    private Integer score;
    private Integer activityScore;
    private Integer seniorityScore;
    private Integer ruleComplianceScore;
    private Integer sanctionsHistoryScore;
    private UserScoreSnapshotType snapshotType;
    private LocalDateTime createdAt;

    public UserScoreHistory() {
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

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getActivityScore() {
        return activityScore;
    }

    public void setActivityScore(Integer activityScore) {
        this.activityScore = activityScore;
    }

    public Integer getSeniorityScore() {
        return seniorityScore;
    }

    public void setSeniorityScore(Integer seniorityScore) {
        this.seniorityScore = seniorityScore;
    }

    public Integer getRuleComplianceScore() {
        return ruleComplianceScore;
    }

    public void setRuleComplianceScore(Integer ruleComplianceScore) {
        this.ruleComplianceScore = ruleComplianceScore;
    }

    public Integer getSanctionsHistoryScore() {
        return sanctionsHistoryScore;
    }

    public void setSanctionsHistoryScore(Integer sanctionsHistoryScore) {
        this.sanctionsHistoryScore = sanctionsHistoryScore;
    }

    public UserScoreSnapshotType getSnapshotType() {
        return snapshotType;
    }

    public void setSnapshotType(UserScoreSnapshotType snapshotType) {
        this.snapshotType = snapshotType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
