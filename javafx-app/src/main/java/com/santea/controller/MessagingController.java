package com.santea.controller;

import com.santea.config.DatabaseConfig;
import com.santea.model.Conversation;
import com.santea.model.Message;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.repository.UserRepository;
import com.santea.service.AuthSession;
import com.santea.service.DatabaseService;
import com.santea.service.MessagingService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MessagingController extends AppBaseViewController {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    @FXML private ListView<Conversation> conversationListView;
    @FXML private ComboBox<User> recipientCombo;
    @FXML private Button newConversationButton;
    @FXML private Label unreadCountLabel;

    private final MessagingService messagingService = new MessagingService();
    private final UserRepository userRepository = new UserRepository(new DatabaseService(DatabaseConfig.fromEnvironment()));
    private final List<User> allRecipients = new ArrayList<>();
    private FilteredList<User> filteredRecipients;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        setupRecipients();
        setupConversationList();
        newConversationButton.setOnAction(event -> startConversation());
        loadConversations();
    }

    private void setupRecipients() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        allRecipients.clear();
        allRecipients.addAll(loadRecipients(currentUser));
        ObservableList<User> baseRecipients = FXCollections.observableArrayList(allRecipients);
        filteredRecipients = new FilteredList<>(baseRecipients, user -> true);
        recipientCombo.setItems(filteredRecipients);
        recipientCombo.setCellFactory(param -> buildUserCell());
        recipientCombo.setButtonCell(buildUserCell());
        recipientCombo.setEditable(true);
        recipientCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(User user) {
                return user == null ? "" : displayUser(user) + " • " + safe(user.getEmail());
            }

            @Override
            public User fromString(String text) {
                return findRecipientFromText(text);
            }
        });

        recipientCombo.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            applyRecipientFilter(newValue);
            if (recipientCombo.getEditor().isFocused()) {
                Platform.runLater(recipientCombo::show);
            }
        });
        recipientCombo.setOnShowing(event -> applyRecipientFilter(recipientCombo.getEditor().getText()));
        recipientCombo.setOnAction(event -> {
            User selected = recipientCombo.getValue();
            if (selected != null) {
                recipientCombo.getEditor().setText(displayUser(selected) + " • " + safe(selected.getEmail()));
            }
        });

        recipientCombo.getStyleClass().add("msg-recipient-combo");
    }

    private void setupConversationList() {
        conversationListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Conversation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                User otherUser = getOtherUser(item);
                HBox row = new HBox(14);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(16, 18, 16, 18));
                row.getStyleClass().add("msg-conversation-row");

                VBox avatar = new VBox();
                avatar.setAlignment(Pos.CENTER);
                avatar.getStyleClass().add("msg-conversation-avatar");
                Label avatarText = new Label(initialsFor(otherUser));
                avatarText.getStyleClass().add("msg-conversation-avatar-text");
                avatar.getChildren().add(avatarText);

                VBox content = new VBox(5);
                Label title = new Label(displayUser(otherUser));
                title.getStyleClass().add("msg-conversation-title");
                Label meta = new Label(buildPreview(item));
                meta.getStyleClass().add("msg-conversation-preview");
                Label role = new Label(roleLabel(otherUser));
                role.getStyleClass().add("msg-conversation-role");
                content.getChildren().addAll(title, meta, role);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                VBox trailing = new VBox(8);
                trailing.setAlignment(Pos.CENTER_RIGHT);
                Label time = new Label(buildConversationTime(item));
                time.getStyleClass().add("msg-conversation-time");
                Button openButton = buildActionButton(">", "Ouvrir", "btn-info");
                openButton.getStyleClass().add("btn-info");
                openButton.setOnAction(event -> {
                    event.consume();
                    AppNavigator.showMessageShow(item.getId());
                });
                Label arrow = new Label(">");
                arrow.getStyleClass().add("msg-conversation-arrow");
                trailing.getChildren().addAll(time, openButton, arrow);

                row.getChildren().addAll(avatar, content, spacer, trailing);
                setGraphic(row);
            }
        });
        conversationListView.setPlaceholder(buildConversationPlaceholder());
        conversationListView.setOnMouseClicked(event -> {
            Conversation selected = conversationListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                AppNavigator.showMessageShow(selected.getId());
            }
        });
    }

    private void loadConversations() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        List<Conversation> conversations = messagingService.findUserConversations(currentUser);
        conversationListView.setItems(FXCollections.observableArrayList(conversations));
        int unreadCount = messagingService.getUnreadCount(currentUser);
        boolean hasUnread = unreadCount > 0;
        unreadCountLabel.setText(hasUnread ? unreadCount + " non lu(s)" : "");
        unreadCountLabel.setManaged(hasUnread);
        unreadCountLabel.setVisible(hasUnread);
    }

    private void startConversation() {
        User currentUser = AuthSession.getCurrentUser();
        User recipient = recipientCombo.getValue();
        if (recipient == null) {
            recipient = findRecipientFromText(recipientCombo.getEditor().getText());
        }
        if (currentUser == null || recipient == null) {
            showAlert("Erreur", "Veuillez sélectionner un destinataire.");
            return;
        }
        try {
            Conversation conversation = messagingService.findOrCreateConversation(currentUser, recipient);
            AppNavigator.showMessageShow(conversation.getId());
        } catch (Exception exception) {
            showAlert("Erreur", exception.getMessage());
        }
    }

    private List<User> loadRecipients(User currentUser) {
        String role = currentUser.getSubscriptionType() != null && !currentUser.getSubscriptionType().isBlank()
            ? currentUser.getSubscriptionType() : currentUser.getRole();
        if ("ROLE_PATIENT".equals(role)) {
            List<User> recipients = new ArrayList<>();
            recipients.addAll(userRepository.findByRoles(List.of("ROLE_MEDECIN", "ROLE_PHARMACIEN", "ROLE_COACH", "ROLE_NUTRITIONNISTE")));
            return recipients;
        }
        return userRepository.findByRole("ROLE_PATIENT");
    }

    private ListCell<User> buildUserCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayUser(item) + " • " + safe(item.getEmail()));
            }
        };
    }

    private void applyRecipientFilter(String searchText) {
        String query = safe(searchText).toLowerCase();
        if (filteredRecipients != null) {
            filteredRecipients.setPredicate(user -> query.isBlank() || recipientMatchesQuery(user, query));
        }
    }

    private boolean recipientMatchesQuery(User user, String query) {
        if (user == null || query.isBlank()) {
            return true;
        }
        String name = displayUser(user).toLowerCase();
        String email = safe(user.getEmail()).toLowerCase();
        String role = roleLabel(user).toLowerCase();
        return name.contains(query) || email.contains(query) || role.contains(query);
    }

    private User findRecipientFromText(String text) {
        String query = safe(text).toLowerCase();
        if (query.isBlank()) {
            return null;
        }

        for (User user : allRecipients) {
            String candidate = (displayUser(user) + " • " + safe(user.getEmail())).toLowerCase();
            if (candidate.equals(query)) {
                return user;
            }
        }

        for (User user : allRecipients) {
            if (recipientMatchesQuery(user, query)) {
                return user;
            }
        }
        return null;
    }

    private User getOtherUser(Conversation conversation) {
        User currentUser = AuthSession.getCurrentUser();
        return currentUser == null ? null : conversation.getOtherUser(currentUser);
    }

    private String buildPreview(Conversation conversation) {
        List<Message> messages = messagingService.getConversationMessages(conversation, 1);
        if (messages.isEmpty()) {
            return "Aucun message";
        }
        LocalDateTime createdAt = messages.get(0).getCreatedAt();
        String when = createdAt == null ? "" : (createdAt.toLocalDate().equals(LocalDate.now()) ? createdAt.format(TIME_FORMAT) : createdAt.format(DAY_TIME_FORMAT));
        String content = safe(messages.get(0).getContent());
        if (content.length() > 60) {
            content = content.substring(0, 57) + "...";
        }
        return content + (when.isBlank() ? "" : " • " + when);
    }

    private String buildConversationTime(Conversation conversation) {
        LocalDateTime lastMessageAt = conversation.getLastMessageAt();
        if (lastMessageAt == null) {
            return "";
        }
        return lastMessageAt.toLocalDate().equals(LocalDate.now())
            ? lastMessageAt.format(TIME_FORMAT)
            : lastMessageAt.format(DAY_TIME_FORMAT);
    }

    private String displayUser(User user) {
        if (user == null) {
            return "Utilisateur";
        }
        String fullName = (safe(user.getPrenom()) + " " + safe(user.getNom())).trim();
        return fullName.isBlank() ? safe(user.getEmail()) : fullName;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String initialsFor(User user) {
        if (user == null) {
            return "US";
        }
        String first = safe(user.getPrenom());
        String last = safe(user.getNom());
        String initials = (first.isEmpty() ? "" : first.substring(0, 1)) + (last.isEmpty() ? "" : last.substring(0, 1));
        if (initials.isBlank()) {
            String email = safe(user.getEmail());
            return email.length() >= 2 ? email.substring(0, 2).toUpperCase() : "US";
        }
        return initials.toUpperCase();
    }

    private String roleLabel(User user) {
        String role = user == null ? "" : safe(user.getSubscriptionType()).isBlank() ? safe(user.getRole()) : safe(user.getSubscriptionType());
        return switch (role) {
            case "ROLE_MEDECIN" -> "Medecin";
            case "ROLE_PHARMACIEN" -> "Pharmacien";
            case "ROLE_COACH" -> "Coach";
            case "ROLE_NUTRITIONNISTE" -> "Nutritionniste";
            case "ROLE_PATIENT" -> "Patient";
            default -> role.isBlank() ? "Utilisateur" : role;
        };
    }

    private VBox buildConversationPlaceholder() {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.getStyleClass().add("msg-empty-state");
        Label title = new Label("Aucune conversation");
        title.getStyleClass().add("tele-empty-title");
        Label subtitle = new Label("Commencez une nouvelle discussion avec un professionnel de sante.");
        subtitle.getStyleClass().add("tele-empty-subtitle");
        box.getChildren().addAll(title, subtitle);
        if (recipientCombo != null && !recipientCombo.getItems().isEmpty()) {
            Button actionButton = new Button("Démarrer une discussion");
            actionButton.getStyleClass().addAll("btn-info", "empty-state-button");
            actionButton.setOnAction(event -> {
                if (recipientCombo.getValue() == null) {
                    recipientCombo.getSelectionModel().selectFirst();
                }
                startConversation();
            });
            box.getChildren().add(actionButton);
        }
        return box;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Button buildActionButton(String symbol, String text, String... styleClasses) {
        Button button = new Button();
        button.getStyleClass().add("row-action-button");
        button.getStyleClass().addAll(styleClasses);
        HBox graphic = new HBox(6);
        graphic.getStyleClass().add("row-action-graphic");
        Label icon = new Label(symbol);
        icon.getStyleClass().add("row-action-icon");
        Label label = new Label(text);
        label.getStyleClass().add("row-action-label");
        graphic.getChildren().addAll(icon, label);
        button.setGraphic(graphic);
        return button;
    }
}
