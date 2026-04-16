package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.PharmacyService;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

public class PharmacyViewController implements Initializable {
	private static final int GRID_PAGE_SIZE = 6;
	private static final int FEEDBACK_AUTO_HIDE_SECONDS = 10;

	private final PharmacyService pharmacyService = new PharmacyService();

	private User currentUser;
	private PharmacyService.ModuleData moduleData = PharmacyService.ModuleData.empty();

	private boolean canView;
	private boolean canReserve;
	private boolean canManage;
	private boolean canStock;
	private boolean canOrders;

	private enum Section {
		PHARMACIES,
		MEDICAMENTS,
		RESERVATIONS
	}

	@FXML
	private VBox root;

	@FXML
	private ScrollPane pharmacyScrollPane;

	@FXML
	private VBox blockedCard;

	@FXML
	private Label blockedMessageLabel;

	@FXML
	private VBox moduleContent;

	@FXML
	private Label heroChipLabel;

	@FXML
	private Label heroTitleLabel;

	@FXML
	private Label heroSubtitleLabel;

	@FXML
	private Label heroStatusLabel;

	@FXML
	private Button pharmaciesSectionButton;

	@FXML
	private Button medicamentsSectionButton;

	@FXML
	private Button reservationsSectionButton;

	@FXML
	private VBox pharmaciesSection;

	@FXML
	private VBox medicamentsSection;

	@FXML
	private VBox reservationsSection;

	@FXML
	private TextField pharmacySearchField;

	@FXML
	private TextField medicamentSearchField;

	@FXML
	private FlowPane publicPharmaciesPane;

	@FXML
	private VBox managedPharmacyToolsCard;

	@FXML
	private FlowPane managedPharmaciesBox;

	@FXML
	private VBox pharmacyFormBox;

	@FXML
	private Button openPharmacyFormButton;

	@FXML
	private TextField pharmacyContactFilterField;

	@FXML
	private ComboBox<String> pharmacyActiveFilterCombo;

	@FXML
	private Pagination publicPharmaciesPagination;

	@FXML
	private Pagination managedPharmaciesPagination;

	@FXML
	private TextField pharmacyNomInput;

	@FXML
	private TextField pharmacyAdresseInput;

	@FXML
	private TextField pharmacyTelephoneInput;

	@FXML
	private TextField pharmacyEmailInput;

	@FXML
	private TextField pharmacyHorairesInput;

	@FXML
	private FlowPane medicamentsPane;

	@FXML
	private VBox managedMedicamentToolsCard;

	@FXML
	private FlowPane managedMedicamentsBox;

	@FXML
	private VBox medicamentFormBox;

	@FXML
	private Button openMedicamentFormButton;

	@FXML
	private TextField medicamentTypeFilterField;

	@FXML
	private Pagination medicamentsPagination;

	@FXML
	private Pagination managedMedicamentsPagination;

	@FXML
	private TextField medicamentNomInput;

	@FXML
	private TextField medicamentTypeInput;

	@FXML
	private TextField medicamentFormeInput;

	@FXML
	private TextField medicamentDosageInput;

	@FXML
	private TextField medicamentPrixInput;

	@FXML
	private TextField medicamentCodeBarreInput;

	@FXML
	private ComboBox<PharmacyService.PharmacyRow> stockPharmacyCombo;

	@FXML
	private ComboBox<PharmacyService.MedicamentRow> stockMedicamentCombo;

	@FXML
	private TextField stockQuantiteInput;

	@FXML
	private TextField stockPrixInput;

	@FXML
	private FlowPane managedStocksBox;

	@FXML
	private Pagination managedStocksPagination;

	@FXML
	private VBox myReservationsCard;

	@FXML
	private FlowPane myReservationsBox;

	@FXML
	private Pagination myReservationsPagination;

	@FXML
	private VBox availableStocksCard;

	@FXML
	private FlowPane availableStocksBox;

	@FXML
	private Pagination availableStocksPagination;

	@FXML
	private VBox incomingReservationsCard;

	@FXML
	private FlowPane incomingReservationsBox;

	@FXML
	private Pagination incomingReservationsPagination;

	@FXML
	private ComboBox<String> reservationStatusFilterCombo;

	@FXML
	private Label reservationsHintLabel;

	@FXML
	private Label feedbackLabel;

	@FXML
	private Button pharmacySubmitButton;

	@FXML
	private Button medicamentSubmitButton;

	private Integer editingPharmacyId;
	private Integer editingMedicamentId;
	private Integer selectedPharmacyId;
	private String selectedPharmacyName;

	private boolean pharmacyFormVisible;
	private boolean medicamentFormVisible;

	private int publicPharmaciesPage;
	private int managedPharmaciesPage;
	private int medicamentsPage;
	private int managedMedicamentsPage;
	private int managedStocksPage;
	private int availableStocksPage;
	private int myReservationsPage;
	private int incomingReservationsPage;

	private List<PharmacyService.PharmacyRow> filteredPublicPharmacies = List.of();
	private List<PharmacyService.PharmacyRow> filteredManagedPharmacies = List.of();
	private List<PharmacyService.MedicamentRow> filteredMedicaments = List.of();
	private List<PharmacyService.MedicamentRow> filteredManagedMedicaments = List.of();
	private List<PharmacyService.StockRow> filteredManagedStocks = List.of();
	private List<PharmacyService.StockRow> filteredAvailableStocks = List.of();
	private List<PharmacyService.ReservationRow> filteredMyReservations = List.of();
	private List<PharmacyService.ReservationRow> filteredIncomingReservations = List.of();
	private PauseTransition feedbackHideTimer;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		currentUser = AuthSession.getCurrentUser();
		if (currentUser == null || currentUser.getId() == null) {
			AppNavigator.showLogin();
			return;
		}

		PharmacyService.AccessDecision accessDecision = pharmacyService.canAccessModule(currentUser);
		if (!accessDecision.allowed()) {
			moduleContent.setVisible(false);
			moduleContent.setManaged(false);
			blockedCard.setVisible(true);
			blockedCard.setManaged(true);
			blockedMessageLabel.setText(accessDecision.message());
			return;
		}

		blockedCard.setVisible(false);
		blockedCard.setManaged(false);
		moduleContent.setVisible(true);
		moduleContent.setManaged(true);

		canView = pharmacyService.can(currentUser, PharmacyService.Permission.VIEW);
		canReserve = pharmacyService.can(currentUser, PharmacyService.Permission.RESERVE);
		canManage = pharmacyService.can(currentUser, PharmacyService.Permission.MANAGE);
		canStock = pharmacyService.can(currentUser, PharmacyService.Permission.STOCK);
		canOrders = pharmacyService.can(currentUser, PharmacyService.Permission.ORDERS);

		configureFilters();
		configurePaginationListeners();
		setupCombos();
		configureScrollPane();
		hideFeedback();
		applyAccessVisibility();
		setHeroText();
		setPharmacyFormVisible(false);
		setMedicamentFormVisible(false);
		setActiveSection(Section.PHARMACIES);
		refreshData();
	}

	private void configureFilters() {
		if (pharmacyActiveFilterCombo != null) {
			pharmacyActiveFilterCombo.getItems().setAll("Tous", "Actives", "Inactives");
			pharmacyActiveFilterCombo.setValue("Tous");
		}

		if (reservationStatusFilterCombo != null) {
			reservationStatusFilterCombo.getItems().setAll("Tous", "en_attente", "confirmee", "refusee", "annulee", "expiree");
			reservationStatusFilterCombo.setValue("Tous");
		}
	}

	private void configurePaginationListeners() {
		if (publicPharmaciesPagination != null) {
			publicPharmaciesPagination.currentPageIndexProperty().addListener((obs, oldValue, newValue) -> {
				publicPharmaciesPage = newValue.intValue();
				renderPublicPharmacies(filteredPublicPharmacies);
			});
		}
		if (managedPharmaciesPagination != null) {
			managedPharmaciesPagination.currentPageIndexProperty().addListener((obs, oldValue, newValue) -> {
				managedPharmaciesPage = newValue.intValue();
				renderManagedPharmacies(filteredManagedPharmacies);
			});
		}
		if (medicamentsPagination != null) {
			medicamentsPagination.currentPageIndexProperty().addListener((obs, oldValue, newValue) -> {
				medicamentsPage = newValue.intValue();
				renderMedicaments(filteredMedicaments);
			});
		}
		if (managedMedicamentsPagination != null) {
			managedMedicamentsPagination.currentPageIndexProperty().addListener((obs, oldValue, newValue) -> {
				managedMedicamentsPage = newValue.intValue();
				renderManagedMedicaments(filteredManagedMedicaments);
			});
		}
		if (managedStocksPagination != null) {
			managedStocksPagination.currentPageIndexProperty().addListener((obs, oldValue, newValue) -> {
				managedStocksPage = newValue.intValue();
				renderManagedStocks(filteredManagedStocks);
			});
		}
		if (availableStocksPagination != null) {
			availableStocksPagination.currentPageIndexProperty().addListener((obs, oldValue, newValue) -> {
				availableStocksPage = newValue.intValue();
				renderAvailableStocks(filteredAvailableStocks);
			});
		}
		if (myReservationsPagination != null) {
			myReservationsPagination.currentPageIndexProperty().addListener((obs, oldValue, newValue) -> {
				myReservationsPage = newValue.intValue();
				renderMyReservations(filteredMyReservations);
			});
		}
		if (incomingReservationsPagination != null) {
			incomingReservationsPagination.currentPageIndexProperty().addListener((obs, oldValue, newValue) -> {
				incomingReservationsPage = newValue.intValue();
				renderIncomingReservations(filteredIncomingReservations);
			});
		}
	}

	@FXML
	private void handleOpenSubscription() {
		AppNavigator.showSubscriptionPage();
	}

	@FXML
	private void handleOpenAiAssistant() {
		List<String> lines = List.of(
				"Priorites pharmacie: surveiller le stock faible, traiter les reservations en attente.",
				"Meme logique Symfony: en_attente -> confirmee/refusee, patient peut annuler.",
				"Pensez a verifier les droits d'abonnement et le role effectif."
		);
		AppNavigator.showFeaturePage("Assistant IA Pharmacie", "Aide operationnelle", lines);
	}

	@FXML
	private void handleFocusReservations() {
		setActiveSection(Section.RESERVATIONS);
	}

	@FXML
	private void handleSectionPharmacies() {
		setActiveSection(Section.PHARMACIES);
	}

	@FXML
	private void handleSectionMedicaments() {
		setActiveSection(Section.MEDICAMENTS);
	}

	@FXML
	private void handleSectionReservations() {
		setActiveSection(Section.RESERVATIONS);
	}

	@FXML
	private void handleSearchPharmacies() {
		refreshData();
	}

	@FXML
	private void handleResetPharmacyFilters() {
		pharmacySearchField.clear();
		selectedPharmacyId = null;
		selectedPharmacyName = null;
		if (pharmacyContactFilterField != null) {
			pharmacyContactFilterField.clear();
		}
		if (pharmacyActiveFilterCombo != null) {
			pharmacyActiveFilterCombo.setValue("Tous");
		}
		publicPharmaciesPage = 0;
		managedPharmaciesPage = 0;
		refreshData();
	}

	@FXML
	private void handleSearchMedicaments() {
		refreshData();
	}

	@FXML
	private void handleResetMedicamentFilters() {
		medicamentSearchField.clear();
		if (medicamentTypeFilterField != null) {
			medicamentTypeFilterField.clear();
		}
		medicamentsPage = 0;
		managedMedicamentsPage = 0;
		refreshData();
	}

	@FXML
	private void handleResetReservationFilters() {
		if (reservationStatusFilterCombo != null) {
			reservationStatusFilterCombo.setValue("Tous");
		}
		availableStocksPage = 0;
		myReservationsPage = 0;
		incomingReservationsPage = 0;
		refreshData();
	}

	@FXML
	private void handleApplyReservationFilters() {
		refreshData();
	}

	@FXML
	private void handleTogglePharmacyForm() {
		setPharmacyFormVisible(!pharmacyFormVisible);
	}

	@FXML
	private void handleToggleMedicamentForm() {
		setMedicamentFormVisible(!medicamentFormVisible);
	}

	@FXML
	private void handleCreatePharmacy() {
		PharmacyService.PharmacyDraft draft = new PharmacyService.PharmacyDraft(
				pharmacyNomInput.getText(),
				pharmacyAdresseInput.getText(),
				pharmacyTelephoneInput.getText(),
				pharmacyEmailInput.getText(),
				pharmacyHorairesInput.getText(),
				"",
				"",
				true
		);

		PharmacyService.ActionResult result = editingPharmacyId == null
				? pharmacyService.createPharmacy(currentUser, draft)
				: pharmacyService.updatePharmacy(currentUser, editingPharmacyId, draft);
		showFeedback(result.success(), result.message());
		if (result.success()) {
			clearPharmacyForm();
			editingPharmacyId = null;
			pharmacySubmitButton.setText("Creer pharmacie");
			setPharmacyFormVisible(false);
			refreshData();
		}
	}

	@FXML
	private void handleCreateMedicament() {
		PharmacyService.MedicamentDraft draft = new PharmacyService.MedicamentDraft(
				medicamentNomInput.getText(),
				medicamentTypeInput.getText(),
				"",
				medicamentFormeInput.getText(),
				medicamentDosageInput.getText(),
				medicamentPrixInput.getText(),
				0,
				"",
				medicamentCodeBarreInput.getText()
		);

		PharmacyService.ActionResult result = editingMedicamentId == null
				? pharmacyService.createMedicament(currentUser, draft)
				: pharmacyService.updateMedicament(currentUser, editingMedicamentId, draft);
		showFeedback(result.success(), result.message());
		if (result.success()) {
			clearMedicamentForm();
			editingMedicamentId = null;
			medicamentSubmitButton.setText("Ajouter medicament");
			setMedicamentFormVisible(false);
			refreshData();
		}
	}

	@FXML
	private void handleResetPharmacyForm() {
		editingPharmacyId = null;
		pharmacySubmitButton.setText("Creer pharmacie");
		clearPharmacyForm();
		setPharmacyFormVisible(false);
	}

	@FXML
	private void handleResetMedicamentForm() {
		editingMedicamentId = null;
		medicamentSubmitButton.setText("Ajouter medicament");
		clearMedicamentForm();
		setMedicamentFormVisible(false);
	}

	@FXML
	private void handleCreateStock() {
		PharmacyService.PharmacyRow selectedPharmacy = stockPharmacyCombo.getValue();
		PharmacyService.MedicamentRow selectedMedicament = stockMedicamentCombo.getValue();
		int quantity = parsePositiveInt(stockQuantiteInput.getText(), 0);
		if (selectedPharmacy == null || selectedMedicament == null || quantity <= 0) {
			showFeedback(false, "Selection pharmacie/medicament et quantite valide requises.");
			return;
		}

		PharmacyService.StockDraft draft = new PharmacyService.StockDraft(
				selectedPharmacy.id(),
				selectedMedicament.id(),
				quantity,
				stockPrixInput.getText()
		);

		PharmacyService.ActionResult result = pharmacyService.createStock(currentUser, draft);
		showFeedback(result.success(), result.message());
		if (result.success()) {
			stockQuantiteInput.clear();
			stockPrixInput.clear();
			refreshData();
		}
	}

	private void setupCombos() {
		stockPharmacyCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(PharmacyService.PharmacyRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? "" : item.nom());
			}
		});
		stockPharmacyCombo.setCellFactory(listView -> new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(PharmacyService.PharmacyRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? "" : item.nom() + " - " + item.adresse());
			}
		});

		stockMedicamentCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(PharmacyService.MedicamentRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? "" : item.nom());
			}
		});
		stockMedicamentCombo.setCellFactory(listView -> new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(PharmacyService.MedicamentRow item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText("");
				} else {
					String type = item.type().isBlank() ? "" : " [" + item.type() + "]";
					setText(item.nom() + type);
				}
			}
		});
	}

	private void configureScrollPane() {
		if (pharmacyScrollPane == null) {
			return;
		}
		pharmacyScrollPane.setFitToWidth(true);
		pharmacyScrollPane.setPannable(true);
	}

	private void applyAccessVisibility() {
		setVisibleManaged(managedPharmacyToolsCard, canManage);
		setVisibleManaged(managedMedicamentToolsCard, canManage || canStock);
		setVisibleManaged(openPharmacyFormButton, canManage);
		setVisibleManaged(openMedicamentFormButton, canManage);
		setVisibleManaged(myReservationsCard, canReserve);
		setVisibleManaged(incomingReservationsCard, canOrders);
		setVisibleManaged(availableStocksCard, canView);
		if (!canManage) {
			setPharmacyFormVisible(false);
			setMedicamentFormVisible(false);
		}

		String roleHint = computeRoleHint();
		reservationsHintLabel.setText(roleHint);
	}

	private void setHeroText() {
		String role = computeRoleLabel();
		heroChipLabel.setText(role);
		heroTitleLabel.setText(canManage ? "Dashboard pharmacie" : "Recherche et comparaison");
		heroSubtitleLabel.setText(canManage
				? "Gerez vos pharmacies, vos medicaments, vos stocks et vos reservations."
				: "Trouvez une pharmacie, comparez les prix et gerez vos reservations.");

		if (!pharmacyService.canConnect()) {
			heroStatusLabel.setText("Base indisponible");
		} else {
			heroStatusLabel.setText("Temps reel");
		}
	}

	private String computeRoleLabel() {
		if (canManage) {
			return "Espace Pharmacien";
		}
		if (canReserve) {
			return "Espace Patient/Medecin";
		}
		return "Module Pharmacie";
	}

	private String computeRoleHint() {
		if (canManage) {
			return "Vous pouvez confirmer/refuser les reservations recues.";
		}
		if (canReserve) {
			return "Vous pouvez reserver et annuler vos reservations.";
		}
		return "Consultation uniquement: gestion des reservations non autorisee.";
	}

	private void setActiveSection(Section section) {
		setVisibleManaged(pharmaciesSection, section == Section.PHARMACIES);
		setVisibleManaged(medicamentsSection, section == Section.MEDICAMENTS);
		setVisibleManaged(reservationsSection, section == Section.RESERVATIONS);

		updateTabState(pharmaciesSectionButton, section == Section.PHARMACIES);
		updateTabState(medicamentsSectionButton, section == Section.MEDICAMENTS);
		updateTabState(reservationsSectionButton, section == Section.RESERVATIONS);
	}

	private void updateTabState(Button button, boolean active) {
		button.getStyleClass().remove("pharm-switch-active");
		if (active) {
			button.getStyleClass().add("pharm-switch-active");
		}
	}

	private void refreshData() {
		if (!canView) {
			return;
		}

		moduleData = pharmacyService.loadModuleData(currentUser, pharmacySearchField.getText(), medicamentSearchField.getText());
		String reservationStatusFilter = reservationStatusFilterCombo == null ? "Tous" : safe(reservationStatusFilterCombo.getValue());
		validateSelectedPharmacy(moduleData.publicPharmacies(), moduleData.managedPharmacies());

		filteredPublicPharmacies = applyPharmacyFilters(moduleData.publicPharmacies());
		filteredManagedPharmacies = applyPharmacyFilters(moduleData.managedPharmacies());
		filteredMedicaments = applySelectedPharmacyToMedicaments(applyMedicamentFilters(moduleData.medicaments()));
		filteredManagedMedicaments = filteredMedicaments;
		filteredAvailableStocks = applySelectedPharmacyToStocks(moduleData.availableStocks());
		filteredManagedStocks = applySelectedPharmacyToStocks(moduleData.managedStocks());
		filteredMyReservations = applyReservationFilters(moduleData.myReservations(), reservationStatusFilter);
		filteredIncomingReservations = applyReservationFilters(moduleData.incomingReservations(), reservationStatusFilter);

		renderPublicPharmacies(filteredPublicPharmacies);
		renderManagedPharmacies(filteredManagedPharmacies);
		renderMedicaments(filteredMedicaments);
		renderManagedMedicaments(filteredManagedMedicaments);
		renderAvailableStocks(filteredAvailableStocks);
		renderManagedStocks(filteredManagedStocks);
		renderMyReservations(filteredMyReservations);
		renderIncomingReservations(filteredIncomingReservations);

		stockPharmacyCombo.getItems().setAll(moduleData.managedPharmacies());
		stockMedicamentCombo.getItems().setAll(moduleData.medicaments());
		syncSelectedPharmacyInCombo();
	}

	private List<PharmacyService.PharmacyRow> applyPharmacyFilters(List<PharmacyService.PharmacyRow> input) {
		String contactFilter = safe(pharmacyContactFilterField == null ? "" : pharmacyContactFilterField.getText()).toLowerCase(Locale.ROOT);
		String activeFilter = pharmacyActiveFilterCombo == null ? "Tous" : safe(pharmacyActiveFilterCombo.getValue());

		List<PharmacyService.PharmacyRow> output = new ArrayList<>();
		for (PharmacyService.PharmacyRow row : input) {
			boolean contactMatch = contactFilter.isBlank()
					|| safe(row.email()).toLowerCase(Locale.ROOT).contains(contactFilter)
					|| safe(row.telephone()).toLowerCase(Locale.ROOT).contains(contactFilter)
					|| safe(row.horaires()).toLowerCase(Locale.ROOT).contains(contactFilter);

			boolean activeMatch = "Tous".equalsIgnoreCase(activeFilter)
					|| ("Actives".equalsIgnoreCase(activeFilter) && row.active())
					|| ("Inactives".equalsIgnoreCase(activeFilter) && !row.active());

			if (contactMatch && activeMatch) {
				output.add(row);
			}
		}
		return output;
	}

	private List<PharmacyService.MedicamentRow> applyMedicamentFilters(List<PharmacyService.MedicamentRow> input) {
		String typeFilter = safe(medicamentTypeFilterField == null ? "" : medicamentTypeFilterField.getText()).toLowerCase(Locale.ROOT);
		if (typeFilter.isBlank()) {
			return input;
		}

		List<PharmacyService.MedicamentRow> output = new ArrayList<>();
		for (PharmacyService.MedicamentRow row : input) {
			if (safe(row.type()).toLowerCase(Locale.ROOT).contains(typeFilter)) {
				output.add(row);
			}
		}
		return output;
	}

	private List<PharmacyService.ReservationRow> applyReservationFilters(List<PharmacyService.ReservationRow> input, String statusFilter) {
		if (statusFilter == null || statusFilter.isBlank() || "Tous".equalsIgnoreCase(statusFilter)) {
			return input;
		}

		List<PharmacyService.ReservationRow> output = new ArrayList<>();
		for (PharmacyService.ReservationRow row : input) {
			if (statusFilter.equalsIgnoreCase(safe(row.statut()))) {
				output.add(row);
			}
		}
		return output;
	}

	private void validateSelectedPharmacy(List<PharmacyService.PharmacyRow> publicPharmacies, List<PharmacyService.PharmacyRow> managedPharmacies) {
		if (selectedPharmacyId == null) {
			return;
		}

		boolean exists = containsPharmacy(publicPharmacies, selectedPharmacyId)
				|| containsPharmacy(managedPharmacies, selectedPharmacyId);
		if (!exists) {
			selectedPharmacyId = null;
			selectedPharmacyName = null;
		}
	}

	private boolean containsPharmacy(List<PharmacyService.PharmacyRow> rows, int pharmacyId) {
		if (rows == null || rows.isEmpty()) {
			return false;
		}
		for (PharmacyService.PharmacyRow row : rows) {
			if (row.id() == pharmacyId) {
				return true;
			}
		}
		return false;
	}

	private List<PharmacyService.StockRow> applySelectedPharmacyToStocks(List<PharmacyService.StockRow> source) {
		if (source == null || source.isEmpty()) {
			return List.of();
		}
		if (selectedPharmacyId == null) {
			return source;
		}

		List<PharmacyService.StockRow> filtered = new ArrayList<>();
		for (PharmacyService.StockRow row : source) {
			if (row.pharmacyId() == selectedPharmacyId) {
				filtered.add(row);
			}
		}
		return filtered;
	}

	private List<PharmacyService.MedicamentRow> applySelectedPharmacyToMedicaments(List<PharmacyService.MedicamentRow> source) {
		if (source == null || source.isEmpty()) {
			return List.of();
		}
		if (selectedPharmacyId == null) {
			return source;
		}

		Set<Integer> selectedMedicamentIds = new HashSet<>();
		for (PharmacyService.StockRow stock : moduleData.availableStocks()) {
			if (stock.pharmacyId() == selectedPharmacyId) {
				selectedMedicamentIds.add(stock.medicamentId());
			}
		}
		for (PharmacyService.StockRow stock : moduleData.managedStocks()) {
			if (stock.pharmacyId() == selectedPharmacyId) {
				selectedMedicamentIds.add(stock.medicamentId());
			}
		}

		if (selectedMedicamentIds.isEmpty()) {
			return List.of();
		}

		List<PharmacyService.MedicamentRow> filtered = new ArrayList<>();
		for (PharmacyService.MedicamentRow row : source) {
			if (selectedMedicamentIds.contains(row.id())) {
				filtered.add(row);
			}
		}
		return filtered;
	}

	private void syncSelectedPharmacyInCombo() {
		if (stockPharmacyCombo == null) {
			return;
		}
		if (selectedPharmacyId == null) {
			stockPharmacyCombo.setValue(null);
			return;
		}

		for (PharmacyService.PharmacyRow pharmacy : stockPharmacyCombo.getItems()) {
			if (pharmacy.id() == selectedPharmacyId) {
				stockPharmacyCombo.setValue(pharmacy);
				return;
			}
		}
	}

	private int syncPagination(Pagination pagination, int totalItems, int pageSize, int currentPage) {
		if (pagination == null) {
			return Math.max(0, currentPage);
		}

		int pageCount = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
		int normalized = Math.max(0, Math.min(currentPage, pageCount - 1));
		pagination.setPageCount(pageCount);
		if (pagination.getCurrentPageIndex() != normalized) {
			pagination.setCurrentPageIndex(normalized);
		}
		boolean visible = totalItems > pageSize;
		pagination.setVisible(visible);
		pagination.setManaged(visible);
		return normalized;
	}

	private <T> List<T> pageSlice(List<T> source, int pageIndex, int pageSize) {
		if (source == null || source.isEmpty()) {
			return List.of();
		}
		int start = Math.max(0, pageIndex * pageSize);
		if (start >= source.size()) {
			return List.of();
		}
		int end = Math.min(source.size(), start + pageSize);
		return source.subList(start, end);
	}

	private void renderPublicPharmacies(List<PharmacyService.PharmacyRow> pharmacies) {
		publicPharmaciesPane.getChildren().clear();
		publicPharmaciesPage = syncPagination(publicPharmaciesPagination, pharmacies.size(), GRID_PAGE_SIZE, publicPharmaciesPage);
		List<PharmacyService.PharmacyRow> pageRows = pageSlice(pharmacies, publicPharmaciesPage, GRID_PAGE_SIZE);
		if (pharmacies.isEmpty()) {
			publicPharmaciesPane.getChildren().add(createEmptyCard("Aucune pharmacie active pour le filtre actuel."));
			return;
		}

		for (PharmacyService.PharmacyRow pharmacy : pageRows) {
			VBox card = new VBox(8);
			card.getStyleClass().addAll("pharm-card", "pharm-clickable");
			if (selectedPharmacyId != null && selectedPharmacyId == pharmacy.id()) {
				card.getStyleClass().add("pharm-selected-pharmacy");
			}
			card.setOnMouseClicked(event -> handleSelectPharmacy(pharmacy));

			HBox top = new HBox(8);
			top.setAlignment(Pos.CENTER_LEFT);
			Label status = new Label(pharmacy.active() ? "Active" : "Inactive");
			status.getStyleClass().addAll("pharm-pill", pharmacy.active() ? "pharm-pill-active" : "pharm-pill-muted");
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			top.getChildren().addAll(status, spacer);

			Label title = new Label(pharmacy.nom());
			title.getStyleClass().add("pharm-card-title");

			Label address = new Label(pharmacy.adresse());
			address.getStyleClass().add("pharm-card-text");
			address.setWrapText(true);

			Label meta = new Label(compactMeta(pharmacy.telephone(), pharmacy.email(), pharmacy.horaires()));
			meta.getStyleClass().add("pharm-card-meta");
			meta.setWrapText(true);

			card.getChildren().addAll(top, title, address, meta);
			publicPharmaciesPane.getChildren().add(card);
		}
	}

	private void renderManagedPharmacies(List<PharmacyService.PharmacyRow> pharmacies) {
		managedPharmaciesBox.getChildren().clear();
		managedPharmaciesPage = syncPagination(managedPharmaciesPagination, pharmacies.size(), GRID_PAGE_SIZE, managedPharmaciesPage);
		List<PharmacyService.PharmacyRow> pageRows = pageSlice(pharmacies, managedPharmaciesPage, GRID_PAGE_SIZE);
		if (pharmacies.isEmpty()) {
			managedPharmaciesBox.getChildren().add(createEmptyRow("Aucune pharmacie geree."));
			return;
		}

		for (PharmacyService.PharmacyRow pharmacy : pageRows) {
			HBox row = new HBox(10);
			row.getStyleClass().addAll("pharm-row", "pharm-clickable");
			if (selectedPharmacyId != null && selectedPharmacyId == pharmacy.id()) {
				row.getStyleClass().add("pharm-selected-pharmacy");
			}
			row.setAlignment(Pos.CENTER_LEFT);
			row.setOnMouseClicked(event -> handleSelectPharmacy(pharmacy));

			VBox labels = new VBox(2);
			Label name = new Label(pharmacy.nom());
			name.getStyleClass().add("pharm-row-title");
			Label info = new Label(pharmacy.adresse());
			info.getStyleClass().add("pharm-row-text");
			labels.getChildren().addAll(name, info);

			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);

			Label status = new Label(pharmacy.active() ? "Active" : "Inactive");
			status.getStyleClass().addAll("pharm-pill", pharmacy.active() ? "pharm-pill-active" : "pharm-pill-muted");

			HBox actions = new HBox(8);
			actions.setAlignment(Pos.CENTER_RIGHT);
			actions.getStyleClass().add("pharm-inline-actions");

			Button edit = new Button("Modifier");
			edit.getStyleClass().addAll("pharm-btn", "pharm-btn-secondary");
			edit.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
			edit.setOnAction(event -> {
				setPharmacyFormVisible(true);
				editingPharmacyId = pharmacy.id();
				pharmacyNomInput.setText(pharmacy.nom());
				pharmacyAdresseInput.setText(pharmacy.adresse());
				pharmacyTelephoneInput.setText(pharmacy.telephone());
				pharmacyEmailInput.setText(pharmacy.email());
				pharmacyHorairesInput.setText(pharmacy.horaires());
				pharmacySubmitButton.setText("Enregistrer pharmacie");
				setActiveSection(Section.PHARMACIES);
			});

			Button delete = new Button("Supprimer");
			delete.getStyleClass().addAll("pharm-btn", "pharm-btn-danger");
			delete.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
			delete.setOnAction(event -> {
				if (!confirmAction("Suppression pharmacie", "Supprimer cette pharmacie ?")) {
					return;
				}
				PharmacyService.ActionResult result = pharmacyService.deletePharmacy(currentUser, pharmacy.id());
				showFeedback(result.success(), result.message());
				if (result.success()) {
					if (editingPharmacyId != null && editingPharmacyId == pharmacy.id()) {
						handleResetPharmacyForm();
					}
					refreshData();
				}
			});

			actions.getChildren().addAll(edit, delete);
			row.getChildren().addAll(labels, spacer, status, actions);
			managedPharmaciesBox.getChildren().add(row);
		}
	}

	private void renderMedicaments(List<PharmacyService.MedicamentRow> medicaments) {
		medicamentsPane.getChildren().clear();
		medicamentsPage = syncPagination(medicamentsPagination, medicaments.size(), GRID_PAGE_SIZE, medicamentsPage);
		List<PharmacyService.MedicamentRow> pageRows = pageSlice(medicaments, medicamentsPage, GRID_PAGE_SIZE);
		if (medicaments.isEmpty()) {
			medicamentsPane.getChildren().add(createEmptyCard("Aucun medicament trouve."));
			return;
		}

		for (PharmacyService.MedicamentRow medicament : pageRows) {
			VBox card = new VBox(8);
			card.getStyleClass().add("pharm-card");

			HBox top = new HBox(8);
			top.setAlignment(Pos.CENTER_LEFT);
			Label type = new Label(medicament.type().isBlank() ? "Medicament" : medicament.type());
			type.getStyleClass().addAll("pharm-pill", "pharm-pill-info");
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			Label price = new Label(medicament.prix().isBlank() ? "-" : medicament.prix());
			price.getStyleClass().add("pharm-card-meta");
			top.getChildren().addAll(type, spacer, price);

			Label title = new Label(medicament.nom());
			title.getStyleClass().add("pharm-card-title");

			String details = compactMeta(medicament.forme(), medicament.dosage(), "Code: " + fallback(medicament.codeBarre(), "N/A"));
			Label meta = new Label(details);
			meta.getStyleClass().add("pharm-card-text");
			meta.setWrapText(true);

			HBox actions = new HBox(8);
			actions.getStyleClass().add("pharm-inline-actions");
			Button compare = new Button("Comparer prix");
			compare.getStyleClass().addAll("pharm-btn", "pharm-btn-primary");
			compare.setOnAction(event -> {
				medicamentSearchField.setText(medicament.nom());
				setActiveSection(Section.RESERVATIONS);
				refreshData();
			});
			actions.getChildren().add(compare);

			card.getChildren().addAll(top, title, meta, actions);
			medicamentsPane.getChildren().add(card);
		}
	}

	private void renderManagedMedicaments(List<PharmacyService.MedicamentRow> medicaments) {
		managedMedicamentsBox.getChildren().clear();
		if (!canManage) {
			setVisibleManaged(managedMedicamentsPagination, false);
			return;
		}
		managedMedicamentsPage = syncPagination(managedMedicamentsPagination, medicaments.size(), GRID_PAGE_SIZE, managedMedicamentsPage);
		List<PharmacyService.MedicamentRow> pageRows = pageSlice(medicaments, managedMedicamentsPage, GRID_PAGE_SIZE);
		if (medicaments.isEmpty()) {
			managedMedicamentsBox.getChildren().add(createEmptyRow("Aucun medicament pour la gestion."));
			return;
		}

		for (PharmacyService.MedicamentRow medicament : pageRows) {
			HBox row = new HBox(10);
			row.getStyleClass().add("pharm-row");
			row.setAlignment(Pos.CENTER_LEFT);

			VBox info = new VBox(2);
			Label title = new Label(medicament.nom());
			title.getStyleClass().add("pharm-row-title");
			Label meta = new Label(compactMeta(medicament.type(), medicament.forme(), medicament.dosage()));
			meta.getStyleClass().add("pharm-row-text");
			info.getChildren().addAll(title, meta);

			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);

			HBox actions = new HBox(8);
			actions.getStyleClass().add("pharm-inline-actions");
			actions.setAlignment(Pos.CENTER_RIGHT);

			Button edit = new Button("Modifier");
			edit.getStyleClass().addAll("pharm-btn", "pharm-btn-secondary");
			edit.setOnAction(event -> {
				setMedicamentFormVisible(true);
				editingMedicamentId = medicament.id();
				medicamentNomInput.setText(medicament.nom());
				medicamentTypeInput.setText(medicament.type());
				medicamentFormeInput.setText(medicament.forme());
				medicamentDosageInput.setText(medicament.dosage());
				medicamentPrixInput.setText(medicament.prix());
				medicamentCodeBarreInput.setText(medicament.codeBarre());
				medicamentSubmitButton.setText("Enregistrer medicament");
				setActiveSection(Section.MEDICAMENTS);
			});

			Button delete = new Button("Supprimer");
			delete.getStyleClass().addAll("pharm-btn", "pharm-btn-danger");
			delete.setOnAction(event -> {
				if (!confirmAction("Suppression medicament", "Supprimer ce medicament ?")) {
					return;
				}
				PharmacyService.ActionResult result = pharmacyService.deleteMedicament(currentUser, medicament.id());
				showFeedback(result.success(), result.message());
				if (result.success()) {
					if (editingMedicamentId != null && editingMedicamentId == medicament.id()) {
						handleResetMedicamentForm();
					}
					refreshData();
				}
			});

			actions.getChildren().addAll(edit, delete);
			row.getChildren().addAll(info, spacer, actions);
			managedMedicamentsBox.getChildren().add(row);
		}
	}

	private void renderAvailableStocks(List<PharmacyService.StockRow> stocks) {
		availableStocksBox.getChildren().clear();
		availableStocksPage = syncPagination(availableStocksPagination, stocks.size(), GRID_PAGE_SIZE, availableStocksPage);
		List<PharmacyService.StockRow> pageRows = pageSlice(stocks, availableStocksPage, GRID_PAGE_SIZE);
		if (stocks.isEmpty()) {
			availableStocksBox.getChildren().add(createEmptyRow("Aucun stock disponible."));
			return;
		}

		for (PharmacyService.StockRow stock : pageRows) {
			HBox row = new HBox(10);
			row.getStyleClass().add("pharm-row");
			row.setAlignment(Pos.CENTER_LEFT);

			VBox info = new VBox(3);
			Label title = new Label(stock.medicamentNom() + " - " + stock.pharmacyNom());
			title.getStyleClass().add("pharm-row-title");
			Label details = new Label("Qté: " + stock.quantite() + " | Prix: " + fallback(stock.prixVente(), "-") + " | " + stock.pharmacyAdresse());
			details.getStyleClass().add("pharm-row-text");
			details.setWrapText(true);
			info.getChildren().addAll(title, details);

			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);

			row.getChildren().addAll(info, spacer);

			if (canReserve) {
				TextField qtyField = new TextField("1");
				qtyField.setPrefWidth(70);
				qtyField.setPromptText("Qté");
				qtyField.getStyleClass().add("pharm-input");

				Button reserve = new Button("Reserver");
				reserve.getStyleClass().addAll("pharm-btn", "pharm-btn-primary");
				reserve.setOnAction(event -> {
					int qty = parsePositiveInt(qtyField.getText(), 0);
					handleReserveStock(stock, qty);
				});
				row.getChildren().addAll(qtyField, reserve);
			}

			availableStocksBox.getChildren().add(row);
		}
	}

	private void renderManagedStocks(List<PharmacyService.StockRow> stocks) {
		managedStocksBox.getChildren().clear();
		managedStocksPage = syncPagination(managedStocksPagination, stocks.size(), GRID_PAGE_SIZE, managedStocksPage);
		List<PharmacyService.StockRow> pageRows = pageSlice(stocks, managedStocksPage, GRID_PAGE_SIZE);
		if (stocks.isEmpty()) {
			String emptyText = selectedPharmacyId == null
					? "Aucune ligne de stock."
					: "Aucune ligne de stock pour la pharmacie selectionnee.";
			managedStocksBox.getChildren().add(createEmptyRow(emptyText));
			return;
		}

		for (PharmacyService.StockRow stock : pageRows) {
			VBox card = new VBox(8);
			card.getStyleClass().add("pharm-row");

			Label title = new Label(stock.medicamentNom() + " - " + stock.pharmacyNom());
			title.getStyleClass().add("pharm-row-title");

			Label details = new Label("Quantite actuelle: " + stock.quantite() + " | Prix: " + fallback(stock.prixVente(), "-") + " | " + stock.pharmacyAdresse());
			details.getStyleClass().add("pharm-row-text");
			details.setWrapText(true);

			HBox actions = new HBox(8);
			actions.setAlignment(Pos.CENTER_LEFT);
			TextField qtyField = new TextField(String.valueOf(stock.quantite()));
			qtyField.setPromptText("Quantite");
			qtyField.getStyleClass().add("pharm-input");
			qtyField.setPrefWidth(90);

			TextField priceField = new TextField(stock.prixVente());
			priceField.setPromptText("Prix");
			priceField.getStyleClass().add("pharm-input");
			priceField.setPrefWidth(120);

			Button save = new Button("Mettre a jour");
			save.getStyleClass().addAll("pharm-btn", "pharm-btn-secondary", "pharm-save-btn");
			save.setOnAction(event -> {
				int qty = parsePositiveInt(qtyField.getText(), -1);
				if (qty < 0) {
					showFeedback(false, "Quantite invalide.");
					return;
				}

				PharmacyService.ActionResult result = pharmacyService.updateStock(currentUser, stock.id(), qty, priceField.getText());
				showFeedback(result.success(), result.message());
				if (result.success()) {
					refreshData();
				}
			});

			Button delete = new Button("Supprimer");
			delete.getStyleClass().addAll("pharm-btn", "pharm-btn-danger");
			delete.setOnAction(event -> {
				if (!confirmAction("Suppression stock", "Supprimer cette ligne de stock ?")) {
					return;
				}
				PharmacyService.ActionResult result = pharmacyService.deleteStock(currentUser, stock.id());
				showFeedback(result.success(), result.message());
				if (result.success()) {
					refreshData();
				}
			});

			actions.getChildren().addAll(qtyField, priceField, save, delete);
			card.getChildren().addAll(title, details, actions);
			managedStocksBox.getChildren().add(card);
		}
	}

	private void renderMyReservations(List<PharmacyService.ReservationRow> rows) {
		myReservationsBox.getChildren().clear();
		myReservationsPage = syncPagination(myReservationsPagination, rows.size(), GRID_PAGE_SIZE, myReservationsPage);
		List<PharmacyService.ReservationRow> pageRows = pageSlice(rows, myReservationsPage, GRID_PAGE_SIZE);
		if (rows.isEmpty()) {
			myReservationsBox.getChildren().add(createEmptyRow("Aucune reservation personnelle."));
			return;
		}

		for (PharmacyService.ReservationRow reservation : pageRows) {
			HBox row = new HBox(10);
			row.getStyleClass().add("pharm-row");
			row.setAlignment(Pos.CENTER_LEFT);

			VBox info = new VBox(3);
			Label title = new Label(reservation.medicamentNom() + " - " + reservation.pharmacyNom());
			title.getStyleClass().add("pharm-row-title");
			Label meta = new Label("Qté: " + reservation.quantite() + " | " + reservation.statusLabel() + " | "
					+ PharmacyService.formatDateTime(reservation.createdAt()));
			meta.getStyleClass().add("pharm-row-text");
			meta.setWrapText(true);
			info.getChildren().addAll(title, meta);

			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);

			Label status = new Label(reservation.statusLabel());
			status.getStyleClass().addAll("pharm-pill", statusStyleClass(reservation.statut()));

			row.getChildren().addAll(info, spacer, status);

			if (canReserve && isCancelable(reservation.statut())) {
				Button cancel = new Button("Annuler");
				cancel.getStyleClass().addAll("pharm-btn", "pharm-btn-danger");
				cancel.setOnAction(event -> {
					if (!confirmAction("Annulation reservation", "Annuler cette reservation ?")) {
						return;
					}
					PharmacyService.ActionResult result = pharmacyService.cancelReservation(currentUser, reservation.id());
					showFeedback(result.success(), result.message());
					if (result.success()) {
						refreshData();
					}
				});
				row.getChildren().add(cancel);
			}

			myReservationsBox.getChildren().add(row);
		}
	}

	private void renderIncomingReservations(List<PharmacyService.ReservationRow> rows) {
		incomingReservationsBox.getChildren().clear();
		incomingReservationsPage = syncPagination(incomingReservationsPagination, rows.size(), GRID_PAGE_SIZE, incomingReservationsPage);
		List<PharmacyService.ReservationRow> pageRows = pageSlice(rows, incomingReservationsPage, GRID_PAGE_SIZE);
		if (rows.isEmpty()) {
			incomingReservationsBox.getChildren().add(createEmptyRow("Aucune reservation entrante."));
			return;
		}

		for (PharmacyService.ReservationRow reservation : pageRows) {
			HBox row = new HBox(10);
			row.getStyleClass().add("pharm-row");
			row.setAlignment(Pos.CENTER_LEFT);

			VBox info = new VBox(3);
			Label title = new Label(reservation.medicamentNom() + " - " + reservation.patientDisplay());
			title.getStyleClass().add("pharm-row-title");
			Label meta = new Label("Pharmacie: " + reservation.pharmacyNom() + " | Qté: " + reservation.quantite()
					+ " | " + PharmacyService.formatDateTime(reservation.createdAt()));
			meta.getStyleClass().add("pharm-row-text");
			meta.setWrapText(true);
			info.getChildren().addAll(title, meta);

			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);

			Label status = new Label(reservation.statusLabel());
			status.getStyleClass().addAll("pharm-pill", statusStyleClass(reservation.statut()));
			row.getChildren().addAll(info, spacer, status);

			if (canOrders && "en_attente".equalsIgnoreCase(reservation.statut())) {
				Button confirm = new Button("Confirmer");
				confirm.getStyleClass().addAll("pharm-btn", "pharm-btn-primary");
				confirm.setOnAction(event -> {
					if (!confirmAction("Confirmer reservation", "Confirmer cette reservation ?")) {
						return;
					}
					PharmacyService.ActionResult result = pharmacyService.confirmReservation(currentUser, reservation.id());
					showFeedback(result.success(), result.message());
					if (result.success()) {
						refreshData();
					}
				});

				Button reject = new Button("Refuser");
				reject.getStyleClass().addAll("pharm-btn", "pharm-btn-danger");
				reject.setOnAction(event -> {
					if (!confirmAction("Refus reservation", "Refuser cette reservation ?")) {
						return;
					}
					PharmacyService.ActionResult result = pharmacyService.rejectReservation(currentUser, reservation.id());
					showFeedback(result.success(), result.message());
					if (result.success()) {
						refreshData();
					}
				});

				row.getChildren().addAll(confirm, reject);
			}

			incomingReservationsBox.getChildren().add(row);
		}
	}

	private void handleSelectPharmacy(PharmacyService.PharmacyRow pharmacy) {
		if (pharmacy == null) {
			return;
		}

		if (selectedPharmacyId != null && selectedPharmacyId == pharmacy.id()) {
			selectedPharmacyId = null;
			selectedPharmacyName = null;
			showFeedback(true, "Filtre pharmacie retire.");
			refreshData();
			return;
		}

		selectedPharmacyId = pharmacy.id();
		selectedPharmacyName = safe(pharmacy.nom());
		medicamentsPage = 0;
		managedMedicamentsPage = 0;
		availableStocksPage = 0;
		managedStocksPage = 0;

		refreshData();
		if (canManage || canStock) {
			setActiveSection(Section.MEDICAMENTS);
		} else {
			setActiveSection(Section.RESERVATIONS);
		}
		showFeedback(true, "Pharmacie selectionnee: " + selectedPharmacyName + ". Medicaments et stocks filtres.");
	}

	private void handleReserveStock(PharmacyService.StockRow stock, int qty) {
		if (qty <= 0) {
			showFeedback(false, "Quantite de reservation invalide.");
			return;
		}
		PharmacyService.ActionResult result = pharmacyService.createReservation(currentUser, stock.id(), qty);
		showFeedback(result.success(), result.message());
		if (result.success()) {
			refreshData();
			setActiveSection(Section.RESERVATIONS);
		}
	}

	private VBox createEmptyCard(String text) {
		VBox box = new VBox();
		box.getStyleClass().addAll("pharm-card", "pharm-empty-card");
		Label label = new Label(text);
		label.getStyleClass().add("pharm-empty-text");
		label.setWrapText(true);
		box.getChildren().add(label);
		return box;
	}

	private HBox createEmptyRow(String text) {
		HBox row = new HBox();
		row.getStyleClass().addAll("pharm-row", "pharm-empty-card");
		Label label = new Label(text);
		label.getStyleClass().add("pharm-empty-text");
		label.setWrapText(true);
		row.getChildren().add(label);
		return row;
	}

	private void showFeedback(boolean success, String message) {
		String normalizedMessage = message == null ? "" : message.trim();
		if (normalizedMessage.isEmpty()) {
			hideFeedback();
			return;
		}

		feedbackLabel.setText(normalizedMessage);
		feedbackLabel.getStyleClass().removeAll("pharm-feedback-success", "pharm-feedback-error");
		feedbackLabel.getStyleClass().add(success ? "pharm-feedback-success" : "pharm-feedback-error");
		feedbackLabel.setVisible(true);
		feedbackLabel.setManaged(true);
		scheduleFeedbackAutoHide();
	}

	private void scheduleFeedbackAutoHide() {
		if (feedbackHideTimer == null) {
			feedbackHideTimer = new PauseTransition(Duration.seconds(FEEDBACK_AUTO_HIDE_SECONDS));
			feedbackHideTimer.setOnFinished(event -> hideFeedback());
		}
		feedbackHideTimer.stop();
		feedbackHideTimer.playFromStart();
	}

	private void hideFeedback() {
		if (feedbackHideTimer != null) {
			feedbackHideTimer.stop();
		}
		if (feedbackLabel == null) {
			return;
		}
		feedbackLabel.setText("");
		feedbackLabel.setVisible(false);
		feedbackLabel.setManaged(false);
	}

	private String statusStyleClass(String status) {
		String normalized = safe(status).toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "confirmee" -> "pharm-pill-confirmed";
			case "refusee", "annulee", "expiree" -> "pharm-pill-danger";
			default -> "pharm-pill-pending";
		};
	}

	private boolean isCancelable(String status) {
		String normalized = safe(status).toLowerCase(Locale.ROOT);
		return "en_attente".equals(normalized) || "confirmee".equals(normalized);
	}

	private String compactMeta(String first, String second, String third) {
		StringBuilder builder = new StringBuilder();
		appendPart(builder, first);
		appendPart(builder, second);
		appendPart(builder, third);
		return builder.toString();
	}

	private void appendPart(StringBuilder builder, String value) {
		String safe = safe(value);
		if (safe.isBlank()) {
			return;
		}
		if (!builder.isEmpty()) {
			builder.append(" | ");
		}
		builder.append(safe);
	}

	private String fallback(String value, String fallback) {
		String normalized = safe(value);
		return normalized.isBlank() ? fallback : normalized;
	}

	private int parsePositiveInt(String raw, int fallback) {
		try {
			return Math.max(0, Integer.parseInt(raw == null ? "" : raw.trim()));
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}

	private void setPharmacyFormVisible(boolean visible) {
		pharmacyFormVisible = visible && canManage;
		setVisibleManaged(pharmacyFormBox, pharmacyFormVisible);
		if (openPharmacyFormButton != null) {
			openPharmacyFormButton.setText(pharmacyFormVisible ? "Fermer ajout pharmacie" : "Ajouter pharmacie");
		}
	}

	private void setMedicamentFormVisible(boolean visible) {
		medicamentFormVisible = visible && canManage;
		setVisibleManaged(medicamentFormBox, medicamentFormVisible);
		if (openMedicamentFormButton != null) {
			openMedicamentFormButton.setText(medicamentFormVisible ? "Fermer ajout medicament" : "Ajouter medicament");
		}
	}

	private boolean confirmAction(String title, String message) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
		alert.setTitle(title);
		alert.setHeaderText(title);
		Optional<ButtonType> result = alert.showAndWait();
		return result.isPresent() && result.get() == ButtonType.OK;
	}

	private void clearPharmacyForm() {
		pharmacyNomInput.clear();
		pharmacyAdresseInput.clear();
		pharmacyTelephoneInput.clear();
		pharmacyEmailInput.clear();
		pharmacyHorairesInput.clear();
	}

	private void clearMedicamentForm() {
		medicamentNomInput.clear();
		medicamentTypeInput.clear();
		medicamentFormeInput.clear();
		medicamentDosageInput.clear();
		medicamentPrixInput.clear();
		medicamentCodeBarreInput.clear();
	}

	private void setVisibleManaged(Node node, boolean visible) {
		if (node == null) {
			return;
		}
		node.setVisible(visible);
		node.setManaged(visible);
	}
}
