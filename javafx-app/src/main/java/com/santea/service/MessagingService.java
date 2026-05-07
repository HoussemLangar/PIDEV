package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.Conversation;
import com.santea.model.Message;
import com.santea.model.User;
import com.santea.repository.UserRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class MessagingService {

    private static final List<String> PROFESSIONAL_ROLES = Arrays.asList(
        "ROLE_MEDECIN", "ROLE_PHARMACIEN", "ROLE_COACH", "ROLE_NUTRITIONNISTE"
    );

    private final DatabaseService databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    private final UserRepository userRepository = new UserRepository(databaseService);

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

    public List<Conversation> findUserConversations(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }

        String sql = "SELECT id, user_one_id, user_two_id, created_at, last_message_at "
            + "FROM conversations WHERE user_one_id = ? OR user_two_id = ? ORDER BY last_message_at DESC";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            statement.setInt(2, user.getId());

            List<Conversation> result = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Conversation conversation = mapConversation(resultSet);
                    if (conversation != null) {
                        result.add(conversation);
                    }
                }
            }
            return result;
        } catch (SQLException exception) {
            Integer userId = user.getId();
            return conversations.values().stream()
                .filter(conv -> conv.getUserOne().getId().equals(userId)
                    || conv.getUserTwo().getId().equals(userId))
                .sorted((c1, c2) -> {
                    LocalDateTime t1 = c1.getLastMessageAt() != null ? c1.getLastMessageAt() : c1.getCreatedAt();
                    LocalDateTime t2 = c2.getLastMessageAt() != null ? c2.getLastMessageAt() : c2.getCreatedAt();
                    if (t1 == null && t2 == null) {
                        return 0;
                    }
                    if (t1 == null) {
                        return 1;
                    }
                    if (t2 == null) {
                        return -1;
                    }
                    return t2.compareTo(t1);
                })
                .collect(Collectors.toList());
        }
    }

    public int getUnreadCount(User user) {
        if (user == null || user.getId() == null) {
            return 0;
        }

        String sql = "SELECT COUNT(*) AS total FROM messages WHERE recipient_id = ? AND is_read = 0";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("total");
                }
            }
        } catch (SQLException exception) {
            return conversations.values().stream()
                .filter(conv -> conv.getUserOne().getId().equals(user.getId())
                    || conv.getUserTwo().getId().equals(user.getId()))
                .mapToInt(conv -> getUnreadCount(conv, user))
                .sum();
        }

        return 0;
    }

    public int getUnreadCount(Conversation conversation, User user) {
        if (conversation == null || user == null || user.getId() == null) {
            return 0;
        }

        if (conversation.getId() != null) {
            String sql = "SELECT COUNT(*) AS total FROM messages "
                + "WHERE conversation_id = ? AND recipient_id = ? AND is_read = 0";
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, conversation.getId());
                statement.setInt(2, user.getId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt("total");
                    }
                }
            } catch (SQLException ignored) {
            }
        }

        String conversationId = generateConversationId(
            conversation.getUserOne().getId().toString(),
            conversation.getUserTwo().getId().toString()
        );
        return getUnreadCount(conversationId, user.getId());
    }

    public Conversation findOrCreateConversation(User userOne, User userTwo) {
        validateMessagingPair(userOne, userTwo);

        User left = userOne;
        User right = userTwo;
        if (left.getId() > right.getId()) {
            left = userTwo;
            right = userOne;
        }

        String findSql = "SELECT id, user_one_id, user_two_id, created_at, last_message_at "
            + "FROM conversations WHERE user_one_id = ? AND user_two_id = ? LIMIT 1";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement findStatement = connection.prepareStatement(findSql)) {
            findStatement.setInt(1, left.getId());
            findStatement.setInt(2, right.getId());
            try (ResultSet resultSet = findStatement.executeQuery()) {
                if (resultSet.next()) {
                    Conversation existing = mapConversation(resultSet);
                    if (existing != null) {
                        return existing;
                    }
                }
            }

            String insertSql = "INSERT INTO conversations (user_one_id, user_two_id, created_at, last_message_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                LocalDateTime now = LocalDateTime.now();
                insertStatement.setInt(1, left.getId());
                insertStatement.setInt(2, right.getId());
                insertStatement.setTimestamp(3, Timestamp.valueOf(now));
                insertStatement.setTimestamp(4, Timestamp.valueOf(now));
                insertStatement.executeUpdate();

                int generatedId = 0;
                try (ResultSet keys = insertStatement.getGeneratedKeys()) {
                    if (keys.next()) {
                        generatedId = keys.getInt(1);
                    }
                }

                Conversation created = new Conversation();
                created.setId(generatedId);
                created.setUserOne(left);
                created.setUserTwo(right);
                created.setCreatedAt(now);
                created.setLastMessageAt(now);

                notifyListeners("CONVERSATION_CREATED", created);
                publishMessageRealtime(String.valueOf(created.getId()), "CONVERSATION_CREATED", null);
                return created;
            }
        } catch (SQLException ignored) {
        }

        String conversationId = generateConversationId(userOne.getId().toString(), userTwo.getId().toString());
        if (conversations.containsKey(conversationId)) {
            return conversations.get(conversationId);
        }

        Conversation conversation = new Conversation();
        conversation.setId(conversationId.hashCode());
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setLastMessageAt(LocalDateTime.now());

        if (userOne.getId() < userTwo.getId()) {
            conversation.setUserOne(userOne);
            conversation.setUserTwo(userTwo);
        } else {
            conversation.setUserOne(userTwo);
            conversation.setUserTwo(userOne);
        }

        conversations.put(conversationId, conversation);
        conversationMessages.put(conversationId, new ArrayList<>());

        String userOneKey = generateUnreadKey(conversationId, conversation.getUserOne().getId().toString());
        String userTwoKey = generateUnreadKey(conversationId, conversation.getUserTwo().getId().toString());
        unreadCounts.put(userOneKey, 0);
        unreadCounts.put(userTwoKey, 0);

        notifyListeners("CONVERSATION_CREATED", conversation);
        publishMessageRealtime(conversationId, "CONVERSATION_CREATED", null);

        return conversation;
    }

    private int getUnreadCount(String conversationId, Integer userId) {
        if (conversationId == null || conversationId.isBlank() || userId == null) {
            return 0;
        }
        return unreadCounts.getOrDefault(generateUnreadKey(conversationId, userId.toString()), 0);
    }

    public Message sendMessage(Conversation conversation, User sender, User recipient, String content) {
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation is required");
        }
        validateMessagingPair(sender, recipient);

        if (!conversation.hasUser(sender)) {
            throw new IllegalArgumentException("Sender is not part of this conversation");
        }
        if (!conversation.hasUser(recipient)) {
            throw new IllegalArgumentException("Recipient is not part of this conversation");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        if (conversation.getId() != null) {
            String insertSql = "INSERT INTO messages (conversation_id, sender_id, recipient_id, content, created_at, is_read) VALUES (?, ?, ?, ?, ?, 0)";
            String updateConversationSql = "UPDATE conversations SET last_message_at = ? WHERE id = ?";
            LocalDateTime now = LocalDateTime.now();

            try (Connection connection = databaseService.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement insertStatement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement updateStatement = connection.prepareStatement(updateConversationSql)) {
                    insertStatement.setInt(1, conversation.getId());
                    insertStatement.setInt(2, sender.getId());
                    insertStatement.setInt(3, recipient.getId());
                    insertStatement.setString(4, content);
                    insertStatement.setTimestamp(5, Timestamp.valueOf(now));
                    insertStatement.executeUpdate();

                    int messageId = 0;
                    try (ResultSet keys = insertStatement.getGeneratedKeys()) {
                        if (keys.next()) {
                            messageId = keys.getInt(1);
                        }
                    }

                    updateStatement.setTimestamp(1, Timestamp.valueOf(now));
                    updateStatement.setInt(2, conversation.getId());
                    updateStatement.executeUpdate();

                    connection.commit();

                    Message message = new Message();
                    message.setId(messageId);
                    message.setConversation(conversation);
                    message.setSender(sender);
                    message.setRecipient(recipient);
                    message.setContent(content);
                    message.setCreatedAt(now);
                    message.setIsRead(false);
                    conversation.setLastMessageAt(now);

                    publishMessageRealtime(String.valueOf(conversation.getId()), "MESSAGE_SENT", message);
                    notifyListeners("MESSAGE_SENT", message);
                    return message;
                } catch (SQLException exception) {
                    connection.rollback();
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException ignored) {
            }
        }

        Message message = new Message();
        message.setId(UUID.randomUUID().hashCode());
        message.setConversation(conversation);
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        message.setIsRead(false);

        String convKey = generateConversationId(conversation.getUserOne().getId().toString(), conversation.getUserTwo().getId().toString());
        conversationMessages.computeIfAbsent(convKey, key -> new ArrayList<>()).add(message);

        conversation.setLastMessageAt(LocalDateTime.now());
        String unreadKey = generateUnreadKey(convKey, recipient.getId().toString());
        unreadCounts.put(unreadKey, unreadCounts.getOrDefault(unreadKey, 0) + 1);

        publishMessageRealtime(convKey, "MESSAGE_SENT", message);
        notifyListeners("MESSAGE_SENT", message);
        return message;
    }

    public void markAllAsRead(User user, Conversation conversation) {
        if (user == null || user.getId() == null || conversation == null || !conversation.hasUser(user)) {
            return;
        }

        if (conversation.getId() != null) {
            String sql = "UPDATE messages SET is_read = 1, read_at = ? WHERE conversation_id = ? AND recipient_id = ? AND is_read = 0";
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                statement.setInt(2, conversation.getId());
                statement.setInt(3, user.getId());
                statement.executeUpdate();

                notifyListeners("MESSAGES_READ", conversation);
                publishMessageRealtime(String.valueOf(conversation.getId()), "MESSAGES_READ", null);
                return;
            } catch (SQLException ignored) {
            }
        }

        String convKey = generateConversationId(conversation.getUserOne().getId().toString(), conversation.getUserTwo().getId().toString());

        List<Message> messages = conversationMessages.get(convKey);
        if (messages != null) {
            messages.stream()
                .filter(m -> !m.getIsRead() && m.getRecipient().getId().equals(user.getId()))
                .forEach(m -> {
                    m.setIsRead(true);
                    m.setReadAt(LocalDateTime.now());
                });
        }

        String unreadKey = generateUnreadKey(convKey, user.getId().toString());
        unreadCounts.put(unreadKey, 0);

        notifyListeners("MESSAGES_READ", conversation);
        publishMessageRealtime(convKey, "MESSAGES_READ", null);
    }

    public List<Message> getConversationMessages(Conversation conversation, Integer limit) {
        if (conversation == null || conversation.getUserOne() == null || conversation.getUserTwo() == null
            || conversation.getUserOne().getId() == null || conversation.getUserTwo().getId() == null) {
            return List.of();
        }

        if (conversation.getId() != null) {
            String sql = "SELECT id, sender_id, recipient_id, content, created_at, read_at, is_read FROM messages WHERE conversation_id = ? ORDER BY created_at ASC";
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, conversation.getId());
                List<Message> messages = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        Message message = mapMessage(resultSet, conversation);
                        if (message != null) {
                            messages.add(message);
                        }
                    }
                }
                if (limit != null && limit > 0 && messages.size() > limit) {
                    return new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
                }
                return messages;
            } catch (SQLException ignored) {
            }
        }

        String convKey = generateConversationId(conversation.getUserOne().getId().toString(), conversation.getUserTwo().getId().toString());
        List<Message> messages = conversationMessages.getOrDefault(convKey, new ArrayList<>());

        if (limit != null && limit > 0) {
            int startIndex = Math.max(0, messages.size() - limit);
            return new ArrayList<>(messages.subList(startIndex, messages.size()));
        }

        return new ArrayList<>(messages);
    }

    public void deleteMessage(User user, Message message) {
        if (user == null || user.getId() == null || message == null || message.getSender() == null) {
            throw new IllegalArgumentException("Invalid message delete request");
        }
        if (!user.getId().equals(message.getSender().getId())) {
            throw new IllegalArgumentException("Only the sender can delete their messages");
        }

        if (message.getId() != null) {
            String sql = "DELETE FROM messages WHERE id = ? AND sender_id = ?";
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, message.getId());
                statement.setInt(2, user.getId());
                statement.executeUpdate();
                notifyListeners("MESSAGE_DELETED", message);
                return;
            } catch (SQLException ignored) {
            }
        }

        Conversation conversation = message.getConversation();
        String convKey = generateConversationId(conversation.getUserOne().getId().toString(), conversation.getUserTwo().getId().toString());

        List<Message> messages = conversationMessages.get(convKey);
        if (messages != null) {
            messages.remove(message);
        }

        notifyListeners("MESSAGE_DELETED", message);
    }

    public void deleteConversation(User user, Conversation conversation) {
        if (user == null || user.getId() == null || conversation == null || !conversation.hasUser(user)) {
            throw new IllegalArgumentException("Only conversation participants can delete the conversation");
        }

        if (conversation.getId() != null) {
            String sql = "DELETE FROM conversations WHERE id = ? AND (user_one_id = ? OR user_two_id = ?)";
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, conversation.getId());
                statement.setInt(2, user.getId());
                statement.setInt(3, user.getId());
                statement.executeUpdate();
                notifyListeners("CONVERSATION_DELETED", conversation);
                publishMessageRealtime(String.valueOf(conversation.getId()), "CONVERSATION_DELETED", null);
                return;
            } catch (SQLException ignored) {
            }
        }

        String convKey = generateConversationId(conversation.getUserOne().getId().toString(), conversation.getUserTwo().getId().toString());

        conversations.remove(convKey);
        conversationMessages.remove(convKey);
        messageObservers.remove(convKey);
        unreadCounts.remove(generateUnreadKey(convKey, conversation.getUserOne().getId().toString()));
        unreadCounts.remove(generateUnreadKey(convKey, conversation.getUserTwo().getId().toString()));

        notifyListeners("CONVERSATION_DELETED", conversation);
        publishMessageRealtime(convKey, "CONVERSATION_DELETED", null);
    }

    public void addListener(MessageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    private void publishMessageRealtime(String conversationId, String eventType, Message message) {
        List<String> subscribers = messageObservers.get(conversationId);
        if (subscribers != null && !subscribers.isEmpty()) {
            subscribers.forEach(subscriberId -> System.out.println(
                "[MessagingService] Publishing " + eventType + " to subscriber "
                    + subscriberId + " in conversation " + conversationId
            ));
        }
    }

    public void subscribe(String conversationId, String subscriberId) {
        messageObservers.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(subscriberId);
    }

    public void unsubscribe(String conversationId, String subscriberId) {
        List<String> subscribers = messageObservers.get(conversationId);
        if (subscribers != null) {
            subscribers.remove(subscriberId);
        }
    }

    private String generateConversationId(String userOneId, String userTwoId) {
        if (userOneId == null || userTwoId == null) {
            return "";
        }
        Integer id1 = Integer.parseInt(userOneId);
        Integer id2 = Integer.parseInt(userTwoId);
        int min = Math.min(id1, id2);
        int max = Math.max(id1, id2);
        return "conv-" + min + "-" + max;
    }

    private String generateUnreadKey(String conversationId, String userId) {
        return conversationId + "-unread-" + userId;
    }

    private void notifyListeners(String eventType, Object data) {
        listeners.forEach(listener -> listener.onMessagingEvent(eventType, data));
    }

    private Conversation mapConversation(ResultSet resultSet) throws SQLException {
        int userOneId = resultSet.getInt("user_one_id");
        int userTwoId = resultSet.getInt("user_two_id");
        Optional<User> userOne = userRepository.findById(userOneId);
        Optional<User> userTwo = userRepository.findById(userTwoId);
        if (userOne.isEmpty() || userTwo.isEmpty()) {
            return null;
        }

        Conversation conversation = new Conversation();
        conversation.setId(resultSet.getInt("id"));
        conversation.setUserOne(userOne.get());
        conversation.setUserTwo(userTwo.get());

        Timestamp createdAt = resultSet.getTimestamp("created_at");
        if (createdAt != null) {
            conversation.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp lastMessageAt = resultSet.getTimestamp("last_message_at");
        if (lastMessageAt != null) {
            conversation.setLastMessageAt(lastMessageAt.toLocalDateTime());
        }

        return conversation;
    }

    private Message mapMessage(ResultSet resultSet, Conversation conversation) throws SQLException {
        int senderId = resultSet.getInt("sender_id");
        int recipientId = resultSet.getInt("recipient_id");

        Optional<User> sender = userRepository.findById(senderId);
        Optional<User> recipient = userRepository.findById(recipientId);
        if (sender.isEmpty() || recipient.isEmpty()) {
            return null;
        }

        Message message = new Message();
        message.setId(resultSet.getInt("id"));
        message.setConversation(conversation);
        message.setSender(sender.get());
        message.setRecipient(recipient.get());
        message.setContent(resultSet.getString("content"));

        Timestamp createdAt = resultSet.getTimestamp("created_at");
        if (createdAt != null) {
            message.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp readAt = resultSet.getTimestamp("read_at");
        if (readAt != null) {
            message.setReadAt(readAt.toLocalDateTime());
        }
        message.setIsRead(resultSet.getBoolean("is_read"));

        return message;
    }

    public interface MessageListener {
        void onMessagingEvent(String eventType, Object data);
    }
}
