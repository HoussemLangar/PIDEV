package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.StripeCheckoutService;
import com.santea.service.SubscriptionService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class SubscriptionViewController implements Initializable {
    @FXML
    private VBox root;

    @FXML
    private VBox paymentSection;

    @FXML
    private VBox stripeWebSection;

    @FXML
    private VBox overviewSection;

    @FXML
    private Label bannerStatus;

    @FXML
    private Label statusText;

    @FXML
    private VBox bannerBox;

    @FXML
    private FlowPane plansContainer;

    @FXML
    private Label chosenPlanLabel;

    @FXML
    private Label chosenPriceLabel;

    @FXML
    private Label checkoutPlanValue;

    @FXML
    private Label paymentFeedback;

    @FXML
    private Label paymentModalFeedback;

    @FXML
    private WebView stripeWebView;

    @FXML
    private Label ovPlan;

    @FXML
    private Label ovType;

    @FXML
    private Label ovPrice;

    @FXML
    private Label ovStart;

    @FXML
    private Label ovEnd;

    @FXML
    private Label ovStatus;

    @FXML
    private Button payBtn;

    private final SubscriptionService subscriptionService = new SubscriptionService();
    private final StripeCheckoutService stripeCheckoutService = new StripeCheckoutService();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SubscriptionService.PlanCard selectedPlan;
    private VBox selectedPlanCard;
    private WebEngine stripeWebEngine;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initStripeWebView();
        renderPlans();
        refreshState();
    }

    @FXML
    private void goHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleNavHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleNavServices() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleNavSettings() {
        AppNavigator.showProfileSettings();
    }

    @FXML
    private void handleNavMfa() {
        AppNavigator.showProfileMfa();
    }

    @FXML
    private void handleNavSubscription() {
        AppNavigator.showSubscriptionPage();
    }

    @FXML
    private void skipForNow() {
        User user = AuthSession.getCurrentUser();
        SubscriptionService.ActionResult result = subscriptionService.skip(user);
        paymentFeedback.setText(result.message());
        refreshState();
    }

    @FXML
    private void startPayment() {
        if (selectedPlan == null) {
            showPaymentFeedback("Selectionnez un plan avant de continuer.");
            return;
        }

        chosenPlanLabel.setText("Abonnement selectionne : " + selectedPlan.label() + " - " + selectedPlan.price() + " DT / mois");
        checkoutPlanValue.setText(selectedPlan.label());
        chosenPriceLabel.setText(selectedPlan.price() + " DT");
        payBtn.setText("Payer " + selectedPlan.price() + " DT");

        plansContainer.setVisible(false);
        plansContainer.setManaged(false);
        overviewSection.setVisible(false);
        overviewSection.setManaged(false);
        stripeWebSection.setVisible(false);
        stripeWebSection.setManaged(false);
        paymentSection.setVisible(true);
        paymentSection.setManaged(true);
        showPaymentFeedback("");
    }

    @FXML
    private void backToChoice() {
        stripeWebSection.setVisible(false);
        stripeWebSection.setManaged(false);
        paymentSection.setVisible(false);
        paymentSection.setManaged(false);
        plansContainer.setVisible(true);
        plansContainer.setManaged(true);
        overviewSection.setVisible(false);
        overviewSection.setManaged(false);
    }

    @FXML
    private void payNow() {
        if (selectedPlan == null) {
            showPaymentFeedback("Selectionnez un plan avant paiement.");
            return;
        }

        User user = AuthSession.getCurrentUser();
        BigDecimal amount = new BigDecimal(selectedPlan.price());

        payBtn.setDisable(true);
        showPaymentFeedback("Creation de la session Stripe Checkout...");

        Task<StripeCheckoutService.HostedCheckoutResult> createSessionTask = new Task<>() {
            @Override
            protected StripeCheckoutService.HostedCheckoutResult call() {
                return stripeCheckoutService.createHostedCheckoutSession(
                        user,
                        selectedPlan.role(),
                        amount,
                        "TND"
                );
            }
        };

        createSessionTask.setOnSucceeded(event -> {
            StripeCheckoutService.HostedCheckoutResult result = createSessionTask.getValue();
            payBtn.setDisable(false);
            if (!result.success()) {
                showPaymentFeedback(result.message());
                return;
            }

            showPaymentFeedback("Redirection vers Stripe Checkout...");
            openStripeWebView(result.checkoutUrl());
        });

        createSessionTask.setOnFailed(event -> {
            Throwable ex = createSessionTask.getException();
            showPaymentFeedback("Erreur creation session Stripe: " + (ex == null ? "inconnue" : ex.getMessage()));
            payBtn.setDisable(false);
        });

        Thread thread = new Thread(createSessionTask, "stripe-session-task");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void cancelSubscription() {
        User user = AuthSession.getCurrentUser();
        SubscriptionService.ActionResult result = subscriptionService.cancel(user);
        paymentFeedback.setText(result.message());
        refreshState();
    }

    @FXML
    private void handleCloseStripeWebView() {
        stripeWebSection.setVisible(false);
        stripeWebSection.setManaged(false);
        paymentSection.setVisible(true);
        paymentSection.setManaged(true);
    }

    private void initStripeWebView() {
        stripeWebEngine = stripeWebView.getEngine();
        stripeWebEngine.locationProperty().addListener((obs, oldLocation, newLocation) -> {
            if (newLocation == null) {
                return;
            }

            if (newLocation.contains("/stripe/success")) {
                String sessionId = extractQueryParam(newLocation, "session_id");
                if (sessionId.isBlank()) {
                    showPaymentFeedback("Paiement retourne sans session Stripe. Reessayez.");
                    return;
                }
                finalizeAfterStripeSuccess(sessionId);
                return;
            }

            if (newLocation.contains("/stripe/cancel")) {
                showPaymentFeedback("Paiement annule.");
                stripeWebSection.setVisible(false);
                stripeWebSection.setManaged(false);
                paymentSection.setVisible(true);
                paymentSection.setManaged(true);
            }
        });
    }

    private void renderPlans() {
        plansContainer.getChildren().clear();

        for (SubscriptionService.PlanCard plan : subscriptionService.getPlans()) {
            if ("AI_TOOLS".equalsIgnoreCase(plan.role())) {
                continue;
            }

            VBox card = new VBox();
            card.getStyleClass().addAll("sub-plan-card", planStyle(plan.role()));
            card.setPrefWidth(280);

            VBox header = new VBox(8);
            header.getStyleClass().add("sub-plan-header");

            Label icon = new Label(planIcon(plan.role()));
            icon.getStyleClass().addAll("sub-plan-icon-box", planIconStyle(plan.role()));

            Label name = new Label(plan.label());
            name.getStyleClass().add("sub-plan-name");

            Label price = new Label(plan.price() + " DT / mois");
            price.getStyleClass().add("sub-plan-price");

            header.getChildren().addAll(icon, name, price);

            VBox featuresBox = new VBox(7);
            featuresBox.getStyleClass().add("sub-plan-features-box");

            for (String feature : splitFeatures(plan.features())) {
                Label item = new Label("✓  " + feature);
                item.getStyleClass().add("sub-plan-feature-item");
                featuresBox.getChildren().add(item);
            }

            Region spacer = new Region();
            spacer.setMinHeight(8);
            VBox.setVgrow(spacer, Priority.ALWAYS);

            Button choose = new Button("S'abonner");
            choose.getStyleClass().addAll("sub-primary-button", "sub-plan-cta");
            choose.setMaxWidth(Double.MAX_VALUE);
            choose.setOnAction(event -> {
                if (selectedPlanCard != null) {
                    selectedPlanCard.getStyleClass().remove("sub-plan-selected");
                }
                selectedPlanCard = card;
                if (!selectedPlanCard.getStyleClass().contains("sub-plan-selected")) {
                    selectedPlanCard.getStyleClass().add("sub-plan-selected");
                }

                selectedPlan = plan;
                paymentFeedback.setText("Plan selectionne: " + plan.label());
                startPayment();
            });

            card.getChildren().addAll(header, featuresBox, spacer, choose);
            plansContainer.getChildren().add(card);
        }
    }

    private void refreshState() {
        User user = AuthSession.getCurrentUser();
        SubscriptionService.SubscriptionOverview overview = subscriptionService.loadOverview(user);

        if (overview.active()) {
            showOverview(overview);
            return;
        }

        bannerStatus.setText("Aucun abonnement actif");
        bannerStatus.getStyleClass().remove("sub-banner-active");
        if (!bannerStatus.getStyleClass().contains("sub-banner-inactive")) {
            bannerStatus.getStyleClass().add("sub-banner-inactive");
        }

        String status = overview.currentStatus() == null || overview.currentStatus().isBlank() ? "INACTIF" : overview.currentStatus();
        String type = overview.currentType() == null || overview.currentType().isBlank() ? "Aucun" : overview.currentType();
        statusText.setText("Statut: " + status + "  |  Type: " + type);

        bannerBox.setVisible(false);
        bannerBox.setManaged(false);
        stripeWebSection.setVisible(false);
        stripeWebSection.setManaged(false);
        paymentSection.setVisible(false);
        paymentSection.setManaged(false);
        overviewSection.setVisible(false);
        overviewSection.setManaged(false);
        plansContainer.setVisible(true);
        plansContainer.setManaged(true);
    }

    private void showOverview(SubscriptionService.SubscriptionOverview overview) {
        bannerStatus.setText("Abonnement actif");
        bannerStatus.getStyleClass().remove("sub-banner-inactive");
        if (!bannerStatus.getStyleClass().contains("sub-banner-active")) {
            bannerStatus.getStyleClass().add("sub-banner-active");
        }

        statusText.setText("Votre abonnement est valide jusqu'au " + dateFormatter.format(overview.dateFin()));
        bannerBox.setVisible(true);
        bannerBox.setManaged(true);

        ovPlan.setText(overview.planName());
        ovType.setText(overview.type());
        ovPrice.setText(overview.price() + " DT");
        ovStart.setText(overview.dateDebut() == null ? "-" : dateFormatter.format(overview.dateDebut()));
        ovEnd.setText(overview.dateFin() == null ? "-" : dateFormatter.format(overview.dateFin()));
        ovStatus.setText(overview.status());

        stripeWebSection.setVisible(false);
        stripeWebSection.setManaged(false);
        paymentSection.setVisible(false);
        paymentSection.setManaged(false);
        plansContainer.setVisible(true);
        plansContainer.setManaged(true);
        overviewSection.setVisible(true);
        overviewSection.setManaged(true);
    }

    private void openStripeWebView(String checkoutUrl) {
        paymentSection.setVisible(false);
        paymentSection.setManaged(false);
        stripeWebSection.setVisible(true);
        stripeWebSection.setManaged(true);
        stripeWebEngine.load(checkoutUrl);
    }

    private void finalizeAfterStripeSuccess(String sessionId) {
        String invoiceOutputDirectory = askInvoiceSaveDirectory();
        if (invoiceOutputDirectory.isBlank()) {
            showPaymentFeedback("Enregistrement facture annule. Paiement non finalise.");
            return;
        }

        showPaymentFeedback("Validation du paiement en cours...");

        Task<StripeCheckoutService.WebhookResult> finalizeTask = new Task<>() {
            @Override
            protected StripeCheckoutService.WebhookResult call() {
                return stripeCheckoutService.finalizePaidSession(sessionId, invoiceOutputDirectory);
            }
        };

        finalizeTask.setOnSucceeded(event -> {
            StripeCheckoutService.WebhookResult webhook = finalizeTask.getValue();
            showPaymentFeedback(webhook.message() + (webhook.invoicePdfPath().isBlank() ? "" : " Facture: " + webhook.invoicePdfPath()));
            if (webhook.success()) {
                stripeWebSection.setVisible(false);
                stripeWebSection.setManaged(false);
                paymentSection.setVisible(false);
                paymentSection.setManaged(false);
                refreshState();
            }
        });

        finalizeTask.setOnFailed(event -> {
            Throwable ex = finalizeTask.getException();
            showPaymentFeedback("Erreur confirmation paiement: " + (ex == null ? "inconnue" : ex.getMessage()));
        });

        Thread thread = new Thread(finalizeTask, "stripe-finalize-task");
        thread.setDaemon(true);
        thread.start();
    }

    private String askInvoiceSaveDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choisir l'emplacement de sauvegarde de la facture");

        File home = new File(System.getProperty("user.home", "."));
        if (home.exists() && home.isDirectory()) {
            chooser.setInitialDirectory(home);
        }

        Window owner = root != null && root.getScene() != null ? root.getScene().getWindow() : null;
        File selected = chooser.showDialog(owner);
        return selected == null ? "" : selected.getAbsolutePath();
    }

    private void showPaymentFeedback(String message) {
        String normalized = message == null ? "" : message;
        if (paymentFeedback != null) {
            paymentFeedback.setText(normalized);
        }
        if (paymentModalFeedback != null) {
            paymentModalFeedback.setText(normalized);
        }
    }

    private String planStyle(String role) {
        return switch (role) {
            case "ROLE_MEDECIN" -> "sub-plan-medecin";
            case "ROLE_PHARMACIEN" -> "sub-plan-pharmacien";
            case "ROLE_COACH" -> "sub-plan-coach";
            case "ROLE_NUTRITIONNISTE" -> "sub-plan-nutri";
            case "ROLE_PATIENT" -> "sub-plan-patient";
            default -> "sub-plan-medecin";
        };
    }

    private String planIcon(String role) {
        return switch (role) {
            case "ROLE_MEDECIN" -> "⚕";
            case "ROLE_PHARMACIEN" -> "⚗";
            case "ROLE_COACH" -> "🏋";
            case "ROLE_NUTRITIONNISTE" -> "🍎";
            case "ROLE_PATIENT" -> "❤";
            default -> "★";
        };
    }

    private String planIconStyle(String role) {
        return switch (role) {
            case "ROLE_MEDECIN" -> "sub-icon-medecin";
            case "ROLE_PHARMACIEN" -> "sub-icon-pharmacien";
            case "ROLE_COACH" -> "sub-icon-coach";
            case "ROLE_NUTRITIONNISTE" -> "sub-icon-nutri";
            case "ROLE_PATIENT" -> "sub-icon-patient";
            default -> "sub-icon-medecin";
        };
    }

    private java.util.List<String> splitFeatures(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.List.of("Acces premium");
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String token : raw.split(",")) {
            String item = token == null ? "" : token.trim();
            if (!item.isBlank()) {
                parts.add(capitalize(item));
            }
        }
        return parts.isEmpty() ? java.util.List.of("Acces premium") : parts;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private String extractQueryParam(String url, String key) {
        if (url == null || url.isBlank() || key == null || key.isBlank()) {
            return "";
        }

        int question = url.indexOf('?');
        if (question < 0 || question == url.length() - 1) {
            return "";
        }

        String query = url.substring(question + 1);
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0])) {
                return parts[1];
            }
        }
        return "";
    }
}
