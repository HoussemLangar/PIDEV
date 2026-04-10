package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.StripeCheckoutService;
import com.santea.service.SubscriptionService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class SubscriptionViewController implements Initializable {
    @FXML
    private VBox root;

    @FXML
    private VBox paymentSection;

    @FXML
    private VBox overviewSection;

    @FXML
    private Label bannerStatus;
    @FXML
    private Label statusText;

    @FXML
    private FlowPane plansContainer;
    @FXML
    private Label chosenPlanLabel;
    @FXML
    private Label chosenPriceLabel;
    @FXML
    private Label paymentFeedback;

    @FXML
    private ComboBox<String> cardType;
    @FXML
    private TextField cardNumber;
    @FXML
    private TextField cardExpiry;
    @FXML
    private TextField cardCvc;

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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupPaymentFields();
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
            paymentFeedback.setText("Selectionnez un plan avant de continuer.");
            return;
        }
        chosenPlanLabel.setText(selectedPlan.label());
        chosenPriceLabel.setText(selectedPlan.price() + " DT / mois");
        payBtn.setText("Payer " + selectedPlan.price() + " DT");
        overviewSection.setVisible(false);
        overviewSection.setManaged(false);
        paymentSection.setVisible(true);
        paymentSection.setManaged(true);
        paymentFeedback.setText("");
    }

    @FXML
    private void backToChoice() {
        paymentSection.setVisible(false);
        paymentSection.setManaged(false);
        overviewSection.setVisible(false);
        overviewSection.setManaged(false);
    }

    @FXML
    private void payNow() {
        if (!isCardInputValid()) {
            paymentFeedback.setText("Verifiez les informations de paiement.");
            return;
        }

        if (selectedPlan == null) {
            paymentFeedback.setText("Selectionnez un plan avant paiement.");
            return;
        }

        User user = AuthSession.getCurrentUser();
        BigDecimal amount = new BigDecimal(selectedPlan.price());
        StripeCheckoutService.CheckoutResult checkout = stripeCheckoutService.createCheckoutSession(user, selectedPlan.role(), amount, "TND");
        if (!checkout.success()) {
            paymentFeedback.setText(checkout.message());
            return;
        }

        String signature = stripeCheckoutService.signPayloadForLocalWebhook(checkout.sessionId(), "checkout.session.completed");
        StripeCheckoutService.WebhookResult webhook = stripeCheckoutService.processWebhook(
                checkout.sessionId(),
                "checkout.session.completed",
                signature
        );
        paymentFeedback.setText(webhook.message() + (webhook.invoicePdfPath().isBlank() ? "" : " Facture: " + webhook.invoicePdfPath()));

        if (webhook.success()) {
            paymentSection.setVisible(false);
            paymentSection.setManaged(false);
            refreshState();
        }
    }

    @FXML
    private void cancelSubscription() {
        User user = AuthSession.getCurrentUser();
        SubscriptionService.ActionResult result = subscriptionService.cancel(user);
        paymentFeedback.setText(result.message());
        refreshState();
    }

    private void setupPaymentFields() {
        cardType.getItems().addAll("Visa", "Mastercard", "American Express");
        cardType.getSelectionModel().selectFirst();

        cardNumber.setText("4242 4242 4242 4242");
        cardExpiry.setText("12/30");
        cardCvc.setText("123");
    }

    private void renderPlans() {
        plansContainer.getChildren().clear();

        for (SubscriptionService.PlanCard plan : subscriptionService.getPlans()) {
            VBox card = new VBox();
            card.getStyleClass().addAll("sub-plan-card", planStyle(plan.role()));
            card.setPrefWidth(280);

            Label name = new Label(plan.label());
            name.getStyleClass().add("sub-plan-name");

            Label price = new Label(plan.price() + " DT / mois");
            price.getStyleClass().add("sub-plan-price");

            Label desc = new Label(plan.features());
            desc.setWrapText(true);
            desc.getStyleClass().add("sub-plan-features");

            Button choose = new Button("S'abonner");
            choose.getStyleClass().add("sub-primary-button");
            choose.setOnAction(event -> {
                selectedPlan = plan;
                chosenPlanLabel.setText(plan.label());
                chosenPriceLabel.setText(plan.price() + " DT / mois");
                payBtn.setText("Payer " + plan.price() + " DT");
                paymentFeedback.setText("Plan selectionne: " + plan.label());
                startPayment();
            });

            HBox line = new HBox(12, name, price);
            card.getChildren().addAll(line, desc, choose);
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
        bannerStatus.getStyleClass().add("sub-banner-inactive");

        String status = overview.currentStatus() == null || overview.currentStatus().isBlank() ? "INACTIF" : overview.currentStatus();
        String type = overview.currentType() == null || overview.currentType().isBlank() ? "Aucun" : overview.currentType();
        statusText.setText("Statut: " + status + "  |  Type: " + type);

        paymentSection.setVisible(false);
        paymentSection.setManaged(false);
        overviewSection.setVisible(false);
        overviewSection.setManaged(false);
    }

    private void showOverview(SubscriptionService.SubscriptionOverview overview) {
        bannerStatus.setText("Abonnement actif");
        bannerStatus.getStyleClass().remove("sub-banner-inactive");
        if (!bannerStatus.getStyleClass().contains("sub-banner-active")) {
            bannerStatus.getStyleClass().add("sub-banner-active");
        }

        statusText.setText("Votre abonnement est valide jusqu'au " + dateFormatter.format(overview.dateFin()));

        ovPlan.setText(overview.planName());
        ovType.setText(overview.type());
        ovPrice.setText(overview.price() + " DT");
        ovStart.setText(overview.dateDebut() == null ? "-" : dateFormatter.format(overview.dateDebut()));
        ovEnd.setText(overview.dateFin() == null ? "-" : dateFormatter.format(overview.dateFin()));
        ovStatus.setText(overview.status());

        paymentSection.setVisible(false);
        paymentSection.setManaged(false);
        overviewSection.setVisible(true);
        overviewSection.setManaged(true);
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

    private boolean isCardInputValid() {
        return cardType.getValue() != null && !cardType.getValue().isBlank()
                && cardNumber.getText() != null && cardNumber.getText().replace(" ", "").length() >= 13
                && cardExpiry.getText() != null && cardExpiry.getText().length() >= 4
                && cardCvc.getText() != null && cardCvc.getText().length() >= 3;
    }
}
