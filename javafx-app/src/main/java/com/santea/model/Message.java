package com.santea.model;

import java.time.LocalDateTime;

public class Message {
    private Integer id;
    private Conversation conversation;
    private User sender;
    private User recipient;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private Boolean isRead;

    public Message() {
    }
    
    /**
     * Constructor matching Symfony Message entity structure
     * @param conversation Conversation object
     * @param sender Sender user
     * @param recipient Recipient user
     * @param content Message content
     */
    public Message(Conversation conversation, User sender, User recipient, String content) {
        this.conversation = conversation;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.isRead = false;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User recipient) {
        this.recipient = recipient;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }
}
