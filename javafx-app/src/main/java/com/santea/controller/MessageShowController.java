package com.santea.controller;

import com.santea.model.Conversation;
import com.santea.model.Message;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.navigation.ModuleContext;
import com.santea.service.AuthSession;
import com.santea.service.MessagingService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MessageShowController extends AppBaseViewController {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    @FXML private Label conversationTitleLabel;
    @FXML private Label onlineStatusLabel;
    @FXML private Label chatHeaderTitleLabel;
    @FXML private Label chatHeaderSubtitleLabel;
    @FXML private Label avatarLabel;
    @FXML private Button backButton;
    @FXML private Button deleteConversationButton;
    @FXML private Button sendMessageButton;
    @FXML private ScrollPane messageScrollPane;
    @FXML private VBox messageDisplayArea;
    @FXML private TextArea messageInputArea;

    private final MessagingService messagingService = new MessagingService();
    private Conversation conversation;
    private Timer refreshTimer;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        Integer id = ModuleContext.getConversationId();
        if (id == null) {
            throw new IllegalStateException("Aucune conversation sélectionnée");
        }
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            throw new IllegalStateException("Utilisateur non connecté");
        }
        conversation = messagingService.findUserConversations(currentUser).stream()
            .filter(item -> item.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Conversation introuvable"));
        backButton.setOnAction(event -> {
            stopRefreshTimer();
            AppNavigator.showMessaging();
        });
        sendMessageButton.setOnAction(event -> sendMessage());
        deleteConversationButton.setOnAction(event -> deleteConversation());
        render();
        startRefreshTimer();
    }

    private void render() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        messagingService.markAllAsRead(currentUser, conversation);
        User otherUser = conversation.getOtherUser(currentUser);
        String title = displayUser(otherUser);
        conversationTitleLabel.setText(title);
        onlineStatusLabel.setText(otherUser == null ? "" : safe(otherUser.getEmail()));
        chatHeaderTitleLabel.setText(title);
        chatHeaderSubtitleLabel.setText(otherUser == null ? "" : buildRoleLine(otherUser) + " • " + safe(otherUser.getEmail()));
        avatarLabel.setText(computeInitials(otherUser));
        renderMessages();
    }

    private void renderMessages() {
        messageDisplayArea.getChildren().clear();
        List<Message> messages = messagingService.getConversationMessages(conversation, null);
        User currentUser = AuthSession.getCurrentUser();
        if (messages.isEmpty()) {
            Label label = new Label("Aucun message");
            label.getStyleClass().add("tele-empty-title");
            messageDisplayArea.getChildren().add(label);
            return;
        }
        for (Message message : messages) {
            boolean own = currentUser != null && message.getSender() != null && currentUser.getId().equals(message.getSender().getId());
            VBox bubble = new VBox(4);
            bubble.getStyleClass().addAll("msg-bubble", own ? "msg-bubble-own" : "msg-bubble-other");
            bubble.setPadding(new Insets(10, 14, 10, 14));
            Label content = new Label(safe(message.getContent()));
            content.setWrapText(true);
            content.getStyleClass().add("msg-bubble-content");
            Label meta = new Label(formatDate(message.getCreatedAt()) + (own && Boolean.TRUE.equals(message.getIsRead()) ? " • Lu" : ""));
            meta.getStyleClass().add("msg-bubble-meta");
            bubble.getChildren().addAll(content, meta);
            HBox row = new HBox(bubble);
            row.setAlignment(own ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messageDisplayArea.getChildren().add(row);
        }
        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    private void sendMessage() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        String content = safe(messageInputArea.getText());
        if (content.isBlank()) {
            showAlert("Erreur", "Veuillez saisir un message.");
            return;
        }
        if (content.length() > 5000) {
            showAlert("Erreur", "Message trop long (max 5000 caractères).");
            return;
        }
        try {
            messagingService.sendMessage(conversation, currentUser, conversation.getOtherUser(currentUser), content);
            messageInputArea.clear();
            render();
        } catch (Exception exception) {
            showAlert("Erreur", exception.getMessage());
        }
    }

    private void deleteConversation() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Supprimer la conversation");
        alert.setHeaderText("Supprimer cette conversation ?");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                messagingService.deleteConversation(currentUser, conversation);
                stopRefreshTimer();
                AppNavigator.showMessaging();
            }
        });
    }

    private void startRefreshTimer() {
        stopRefreshTimer();
        refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> render());
            }
        }, 0, 2000);
    }

    private void stopRefreshTimer() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer.purge();
            refreshTimer = null;
        }
    }

    private String displayUser(User user) {
        if (user == null) {
            return "Utilisateur";
        }
        String full = (safe(user.getPrenom()) + " " + safe(user.getNom())).trim();
        return full.isBlank() ? safe(user.getEmail()) : full;
    }

    private String computeInitials(User user) {
        if (user == null) {
            return "US";
        }
        String prenom = safe(user.getPrenom());
        String nom = safe(user.getNom());
        String initials = (prenom.isBlank() ? "" : prenom.substring(0, 1).toUpperCase())
            + (nom.isBlank() ? "" : nom.substring(0, 1).toUpperCase());
        if (!initials.isBlank()) {
            return initials;
        }
        String email = safe(user.getEmail());
        return email.isBlank() ? "US" : email.substring(0, 1).toUpperCase();
    }

    private String buildRoleLine(User user) {
        String role = user.getSubscriptionType() != null && !user.getSubscriptionType().isBlank()
            ? user.getSubscriptionType() : user.getRole();
        return role == null ? "" : role;
    }

    private String formatDate(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.toLocalDate().equals(LocalDate.now()) ? value.format(TIME_FORMAT) : value.format(DAY_TIME_FORMAT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
