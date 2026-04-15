package com.santea.repository;

import com.santea.model.Conversation;
import com.santea.model.Message;
import java.util.*;

/**
 * ConversationRepository
 * Data access layer for Conversation and Message entities
 */
public class ConversationRepository {
    
    private Map<String, Conversation> conversations = new HashMap<>();
    private Map<String, List<Message>> conversationMessages = new HashMap<>();
    
    /**
     * Save a conversation
     * @param conversation Conversation to save
     * @return Saved conversation
     */
    public Conversation saveConversation(Conversation conversation) {
        String convId = String.valueOf(conversation.getId());
        conversations.put(convId, conversation);
        if (!conversationMessages.containsKey(convId)) {
            conversationMessages.put(convId, new ArrayList<>());
        }
        return conversation;
    }
    
    /**
     * Find conversation by ID
     * @param id Conversation ID
     * @return Conversation or null
     */
    public Conversation findConversationById(String id) {
        return conversations.get(id);
    }
    
    /**
     * Find conversation by two users
     * @param userOneId First user ID
     * @param userTwoId Second user ID
     * @return Conversation or null
     */
    public Conversation findConversationBetweenUsers(String userOneId, String userTwoId) {
        return conversations.values().stream()
            .filter(c -> (c.getUserOne().getId().toString().equals(userOneId) && 
                         c.getUserTwo().getId().toString().equals(userTwoId)) ||
                        (c.getUserOne().getId().toString().equals(userTwoId) && 
                         c.getUserTwo().getId().toString().equals(userOneId)))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Find all conversations for a user
     * @param userId User ID
     * @return List of conversations
     */
    public List<Conversation> findConversationsByUserId(String userId) {
        List<Conversation> result = new ArrayList<>();
        conversations.values().forEach(conv -> {
            if (conv.getUserOne().getId().toString().equals(userId) || 
                conv.getUserTwo().getId().toString().equals(userId)) {
                result.add(conv);
            }
        });
        return result;
    }
    
    /**
     * Find all conversations
     * @return All conversations
     */
    public List<Conversation> findAllConversations() {
        return new ArrayList<>(conversations.values());
    }
    
    /**
     * Delete conversation by ID
     * @param id Conversation ID
     * @return true if deleted
     */
    public boolean deleteConversation(String id) {
        conversationMessages.remove(id);
        return conversations.remove(id) != null;
    }
    
    /**
     * Save a message
     * @param message Message to save
     * @param conversationId Conversation ID
     * @return Saved message
     */
    public Message saveMessage(Message message, String conversationId) {
        conversationMessages.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
        return message;
    }
    
    /**
     * Find all messages for a conversation
     * @param conversationId Conversation ID
     * @return List of messages
     */
    public List<Message> findMessagesByConversationId(String conversationId) {
        return new ArrayList<>(conversationMessages.getOrDefault(conversationId, new ArrayList<>()));
    }
    
    /**
     * Find message by ID
     * @param messageId Message ID
     * @return Message or null
     */
    public Message findMessageById(String messageId) {
        for (List<Message> messages : conversationMessages.values()) {
            for (Message message : messages) {
                if (message.getId().toString().equals(messageId)) {
                    return message;
                }
            }
        }
        return null;
    }
    
    /**
     * Find unread messages for a user
     * @param conversationId Conversation ID
     * @param userId User ID
     * @return List of unread messages
     */
    public List<Message> findUnreadMessages(String conversationId, String userId) {
        List<Message> result = new ArrayList<>();
        List<Message> messages = conversationMessages.getOrDefault(conversationId, new ArrayList<>());
        
        messages.forEach(msg -> {
            if (!msg.getIsRead() && msg.getRecipient().getId().toString().equals(userId)) {
                result.add(msg);
            }
        });
        
        return result;
    }
    
    /**
     * Count unread messages for a user in a conversation
     * @param conversationId Conversation ID
     * @param userId User ID
     * @return Unread message count
     */
    public long countUnreadMessages(String conversationId, String userId) {
        return findUnreadMessages(conversationId, userId).size();
    }
    
    /**
     * Count total unread messages for a user
     * @param userId User ID
     * @return Total unread count
     */
    public long countTotalUnreadMessages(String userId) {
        long count = 0;
        for (List<Message> messages : conversationMessages.values()) {
            count += messages.stream()
                .filter(msg -> !msg.getIsRead() && msg.getRecipient().getId().toString().equals(userId))
                .count();
        }
        return count;
    }
    
    /**
     * Delete message by ID
     * @param conversationId Conversation ID
     * @param messageId Message ID
     * @return true if deleted
     */
    public boolean deleteMessage(String conversationId, String messageId) {
        List<Message> messages = conversationMessages.get(conversationId);
        if (messages != null) {
            return messages.removeIf(msg -> msg.getId().toString().equals(messageId));
        }
        return false;
    }
    
    /**
     * Count conversations
     * @return Total conversation count
     */
    public long countConversations() {
        return conversations.size();
    }
    
    /**
     * Count messages
     * @return Total message count
     */
    public long countMessages() {
        return conversationMessages.values().stream().mapToLong(List::size).sum();
    }
    
    /**
     * Search messages in a conversation
     * @param conversationId Conversation ID
     * @param query Search query
     * @return List of matching messages
     */
    public List<Message> searchMessages(String conversationId, String query) {
        List<Message> result = new ArrayList<>();
        List<Message> messages = conversationMessages.getOrDefault(conversationId, new ArrayList<>());
        
        messages.forEach(msg -> {
            if (msg.getContent().toLowerCase().contains(query.toLowerCase())) {
                result.add(msg);
            }
        });
        
        return result;
    }
}
