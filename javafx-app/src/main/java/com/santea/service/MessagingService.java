package com.santea.service;

import com.santea.model.Conversation;
import com.santea.model.Message;
import com.santea.model.User;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MessagingService - Section 2.8
 * Manages real-time messaging between patients and professionals
 * 
 * Responsibilities:
 * - 1:1 conversations management (one conversation per patient-professional pair)
 * - Message sending and reading with validation
 * - Unread message counters per conversation and total
 * - Real-time message publishing via listeners (replaces Mercure in JavaFX)
 * - Message history retrieval with filtering
 * - Communication rules: patient <-> professional only
 * 
 * Database Logic from Symfony:
 * - Entity Conversation: id, userOne, userTwo, createdAt, lastMessageAt
 * - Entity Message: id, conversation, sender, recipient, content, createdAt, isRead, readAt
 * - Professional roles: ROLE_MEDECIN, ROLE_PHARMACIEN, ROLE_COACH, ROLE_NUTRITIONNISTE
 * - Rule: Patients can only message professionals and vice versa
 */
public class MessagingService {
    
    private static final List<String> PROFESSIONAL_ROLES = Arrays.asList(
        "ROLE_MEDECIN", "ROLE_PHARMACIEN", "ROLE_COACH", "ROLE_NUTRITIONNISTE"
    );
    
    // Core storage maps matching database structure
    private static final Map<String, Conversation> conversations = new HashMap<>();
    private static final Map<String, List<Message>> conversationMessages = new HashMap<>();
    private static final Map<String, Integer> unreadCounts = new HashMap<>();
    private static final Map<String, List<String>> messageObservers = new HashMap<>();
    private static final List<MessageListener> listeners = new ArrayList<>();

    private String getEffectiveRole(User user) {
        return user.getSubscriptionType() != null && !user.getSubscriptionType().isBlank()
            ? user.getSubscriptionType()
            : user.getRole();
    }

    private boolean isPatient(User user) {
        return "ROLE_PATIENT".equals(getEffectiveRole(user));
    }

    private boolean isProfessional(User user) {
        return PROFESSIONAL_ROLES.contains(getEffectiveRole(user));
    }

    private void validateMessagingPair(User sender, User recipient) {
        if (sender == null || recipient == null) {
            throw new IllegalArgumentException("Sender and recipient are required");
        }
        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("A user cannot have a conversation with themselves");
        }
        if (isPatient(sender) && !isProfessional(recipient)) {
            throw new IllegalArgumentException("Patients can only message health professionals");
        }
        if (isProfessional(sender) && !isPatient(recipient)) {
            throw new IllegalArgumentException("Professionals can only message patients");
        }
        if (!isPatient(sender) && !isProfessional(sender)) {
            throw new IllegalArgumentException("Sender role is not allowed for messaging");
        }
    }
    
    /**
     * Find all conversations for a user
     * Sorted by lastMessageAt (most recent first)
     * @param user User object
     * @return List of conversations
     */
    public List<Conversation> findUserConversations(User user) {
        Integer userId = user.getId();
        return conversations.values().stream()
            .filter(conv -> conv.getUserOne().getId().equals(userId) || 
                          conv.getUserTwo().getId().equals(userId))
            .sorted((c1, c2) -> {
                LocalDateTime t1 = c1.getLastMessageAt() != null ? c1.getLastMessageAt() : c1.getCreatedAt();
                LocalDateTime t2 = c2.getLastMessageAt() != null ? c2.getLastMessageAt() : c2.getCreatedAt();
                return t2.compareTo(t1);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get total unread message count for a user across all conversations
     * @param user User object
     * @return Total unread count
     */
    public int getUnreadCount(User user) {
        return conversations.values().stream()
            .filter(conv -> conv.getUserOne().getId().equals(user.getId()) || 
                          conv.getUserTwo().getId().equals(user.getId()))
            .mapToInt(conv -> getUnreadCount(conv, user))
            .sum();
    }

    /**
     * Get unread message count for one conversation and one user.
     * @param conversation Conversation object
     * @param user User object
     * @return Unread count for that user in that conversation
     */
    public int getUnreadCount(Conversation conversation, User user) {
        if (conversation == null || user == null || user.getId() == null) {
            return 0;
        }

        String conversationId = generateConversationId(
            conversation.getUserOne().getId().toString(),
            conversation.getUserTwo().getId().toString()
        );
        return getUnreadCount(conversationId, user.getId());
    }
    
    /**
     * Find or create a conversation between two users
     * Ensures uniqueness by sorting user IDs
     * @param userOne First user
     * @param userTwo Second user
     * @return Conversation object
     * @throws IllegalArgumentException if users are the same
     */
    public Conversation findOrCreateConversation(User userOne, User userTwo) {
        validateMessagingPair(userOne, userTwo);
        
        // Generate conversation ID (sorted to ensure uniqueness)
        String conversationId = generateConversationId(userOne.getId().toString(), userTwo.getId().toString());
        
        if (conversations.containsKey(conversationId)) {
            return conversations.get(conversationId);
        }
        
        // Create new conversation
        Conversation conversation = new Conversation();
        conversation.setId(conversationId.hashCode());
        conversation.setCreatedAt(LocalDateTime.now());
        
        // Store users in consistent order
        Integer id1 = userOne.getId();
        Integer id2 = userTwo.getId();
        if (id1 < id2) {
            conversation.setUserOne(userOne);
            conversation.setUserTwo(userTwo);
        } else {
            conversation.setUserOne(userTwo);
            conversation.setUserTwo(userOne);
        }
        
        conversations.put(conversationId, conversation);
        conversationMessages.put(conversationId, new ArrayList<>());
        
        // Initialize unread counts
        String userOneKey = generateUnreadKey(conversationId, conversation.getUserOne().getId().toString());
        String userTwoKey = generateUnreadKey(conversationId, conversation.getUserTwo().getId().toString());
        unreadCounts.put(userOneKey, 0);
        unreadCounts.put(userTwoKey, 0);
        
        notifyListeners("CONVERSATION_CREATED", conversation);
        publishMessageRealtime(conversationId, "CONVERSATION_CREATED", null);
        
        return conversation;
    }

    /**
     * Backing helper for unread-count aggregation.
     */
    private int getUnreadCount(String conversationId, Integer userId) {
        if (conversationId == null || conversationId.isBlank() || userId == null) {
            return 0;
        }
        return unreadCounts.getOrDefault(generateUnreadKey(conversationId, userId.toString()), 0);
    }
    
    /**
     * Send a message in a conversation
     * @param conversation Conversation object
     * @param sender Sender user
     * @param recipient Recipient user
     * @param content Message content
     * @return Message object
     * @throws IllegalArgumentException if validation fails
     */
    public Message sendMessage(Conversation conversation, User sender, User recipient, String content) {
        validateMessagingPair(sender, recipient);
        // Validate sender and recipient are in conversation
        if (!conversation.hasUser(sender)) {
            throw new IllegalArgumentException("Sender is not part of this conversation");
        }
        
        if (!conversation.hasUser(recipient)) {
            throw new IllegalArgumentException("Recipient is not part of this conversation");
        }
        
        // Validate content is not empty
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }
        
        // Create message
        Message message = new Message();
        message.setId(UUID.randomUUID().hashCode());
        message.setConversation(conversation);
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        message.setIsRead(false);
        
        // Store message
        String convKey = generateConversationId(
            conversation.getUserOne().getId().toString(),
            conversation.getUserTwo().getId().toString());
        conversationMessages.get(convKey).add(message);
        
        // Update conversation last message time
        conversation.setLastMessageAt(LocalDateTime.now());
        
        // Increment unread count for recipient
        String unreadKey = generateUnreadKey(convKey, recipient.getId().toString());
        unreadCounts.put(unreadKey, unreadCounts.getOrDefault(unreadKey, 0) + 1);
        
        // Publish to real-time subscribers (Mercure equivalent)
        publishMessageRealtime(convKey, "MESSAGE_SENT", message);
        
        // Notify listeners
        notifyListeners("MESSAGE_SENT", message);
        
        return message;
    }
    
    /**
     * Mark all messages as read in a conversation
     * @param user User marking messages as read
     * @param conversation Conversation
     */
    public void markAllAsRead(User user, Conversation conversation) {
        String convKey = generateConversationId(
            conversation.getUserOne().getId().toString(),
            conversation.getUserTwo().getId().toString());
        
        List<Message> messages = conversationMessages.get(convKey);
        
        if (messages != null) {
            messages.stream()
                .filter(m -> !m.getIsRead() && m.getRecipient().getId().equals(user.getId()))
                .forEach(m -> {
                    m.setIsRead(true);
                    m.setReadAt(LocalDateTime.now());
                });
        }
        
        // Reset unread count
        String unreadKey = generateUnreadKey(convKey, user.getId().toString());
        unreadCounts.put(unreadKey, 0);
        
        notifyListeners("MESSAGES_READ", conversation);
        publishMessageRealtime(convKey, "MESSAGES_READ", null);
    }
    
    /**
     * Get messages for a conversation with optional pagination
     * @param conversation Conversation
     * @param limit Optional limit (null or <= 0 for all)
     * @return List of messages in chronological order
     */
    public List<Message> getConversationMessages(Conversation conversation, Integer limit) {
        String convKey = generateConversationId(
            conversation.getUserOne().getId().toString(),
            conversation.getUserTwo().getId().toString());
        
        List<Message> messages = conversationMessages.getOrDefault(convKey, new ArrayList<>());
        
        if (limit != null && limit > 0) {
            int startIndex = Math.max(0, messages.size() - limit);
            return new ArrayList<>(messages.subList(startIndex, messages.size()));
        }
        
        return new ArrayList<>(messages);
    }
    
    /**
     * Delete a message (only sender can delete)
     * @param user User requesting deletion
     * @param message Message to delete
     * @throws IllegalArgumentException if user is not sender
     */
    public void deleteMessage(User user, Message message) {
        if (!user.getId().equals(message.getSender().getId())) {
            throw new IllegalArgumentException("Only the sender can delete their messages");
        }
        
        Conversation conversation = message.getConversation();
        String convKey = generateConversationId(
            conversation.getUserOne().getId().toString(),
            conversation.getUserTwo().getId().toString());
        
        List<Message> messages = conversationMessages.get(convKey);
        if (messages != null) {
            messages.remove(message);
        }
        
        notifyListeners("MESSAGE_DELETED", message);
    }
    
    /**
     * Add message listener for real-time updates
     * @param listener MessageListener
     */
    public void addListener(MessageListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove message listener
     * @param listener MessageListener
     */
    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Publish message to real-time subscribers (Mercure Hub equivalent)
     * @param conversationId Conversation ID
     * @param eventType Event type
     * @param message Message object (can be null for status updates)
     */
    private void publishMessageRealtime(String conversationId, String eventType, Message message) {
        List<String> subscribers = messageObservers.get(conversationId);
        if (subscribers != null && !subscribers.isEmpty()) {
            subscribers.forEach(subscriberId -> {
                // In production, publish to Mercure Hub
                System.out.println("[MessagingService] Publishing " + eventType + 
                                 " to subscriber " + subscriberId + " in conversation " + conversationId);
            });
        }
    }
    
    /**
     * Subscribe to real-time updates for a conversation
     * @param conversationId Conversation ID
     * @param subscriberId Subscriber ID
     */
    public void subscribe(String conversationId, String subscriberId) {
        messageObservers.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(subscriberId);
    }
    
    /**
     * Unsubscribe from real-time updates for a conversation
     * @param conversationId Conversation ID
     * @param subscriberId Subscriber ID
     */
    public void unsubscribe(String conversationId, String subscriberId) {
        List<String> subscribers = messageObservers.get(conversationId);
        if (subscribers != null) {
            subscribers.remove(subscriberId);
        }
    }
    
    /**
     * Helper: Check if sender is part of conversation
     */
    private boolean isSenderInConversation(Conversation conversation, User sender) {
        return sender.getId().equals(conversation.getUserOne().getId()) ||
               sender.getId().equals(conversation.getUserTwo().getId());
    }
    
    /**
     * Helper: Generate unique conversation ID from two user IDs (sorted)
     */
    private String generateConversationId(String userOneId, String userTwoId) {
        if (userOneId == null || userTwoId == null) {
            return ""; // Placeholder
        }
        
        Integer id1 = Integer.parseInt(userOneId);
        Integer id2 = Integer.parseInt(userTwoId);
        int min = Math.min(id1, id2);
        int max = Math.max(id1, id2);
        return "conv-" + min + "-" + max;
    }
    
    /**
     * Helper: Generate unread count key for conversation + user
     */
    private String generateUnreadKey(String conversationId, String userId) {
        return conversationId + "-unread-" + userId;
    }
    
    /**
     * Notify all listeners of messaging event
     * @param eventType Event type (MESSAGE_SENT, MESSAGES_READ, CONVERSATION_CREATED, etc.)
     * @param data Event data (Message, Conversation, etc.)
     */
    private void notifyListeners(String eventType, Object data) {
        listeners.forEach(listener -> listener.onMessagingEvent(eventType, data));
    }
    
    /**
     * Interface for messaging event listeners (replaces Mercure in JavaFX)
     */
    public interface MessageListener {
        void onMessagingEvent(String eventType, Object data);
    }
}
