package com.santea.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Conversation {
    private Integer id;
    private User userOne;
    private User userTwo;
    private List<Object> messages;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;

    public Conversation() {
        this.messages = new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getUserOne() {
        return userOne;
    }

    public void setUserOne(User userOne) {
        this.userOne = userOne;
    }

    public User getUserTwo() {
        return userTwo;
    }

    public void setUserTwo(User userTwo) {
        this.userTwo = userTwo;
    }

    public List<Object> getMessages() {
        return messages;
    }

    public void setMessages(List<Object> messages) {
        this.messages = messages;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }
    
    /**
     * Check if a user is part of this conversation
     * @param user User to check
     * @return true if user is userOne or userTwo
     */
    public boolean hasUser(User user) {
        if (user == null) {
            return false;
        }
        return (userOne != null && userOne.getId().equals(user.getId())) ||
               (userTwo != null && userTwo.getId().equals(user.getId()));
    }
    
    /**
     * Get the other user in the conversation
     * @param user Current user
     * @return The other user, or null if user is not in conversation
     */
    public User getOtherUser(User user) {
        if (user == null || !hasUser(user)) {
            return null;
        }
        
        if (userOne != null && userOne.getId().equals(user.getId())) {
            return userTwo;
        }
        if (userTwo != null && userTwo.getId().equals(user.getId())) {
            return userOne;
        }
        
        return null;
    }
    
    /**
     * Touch the last message timestamp (update conversation activity)
     * @param timestamp Timestamp to set
     */
    public void touchLastMessageAt(LocalDateTime timestamp) {
        this.lastMessageAt = timestamp;
    }
}
