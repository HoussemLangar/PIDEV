package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AppointmentService;
import com.santea.service.AuthSession;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public class AppointmentViewController implements Initializable {
	private static final String APPOINTMENT_STYLESHEET = "/com/santea/styles/auth.css";

	private final AppointmentService appointmentService = new AppointmentService();

	private final ToggleGroup slotsToggleGroup = new ToggleGroup();

	private User currentUser;
	private AppointmentService.AccessScope accessScope = AppointmentService.AccessScope.DENIED;
	private List<AppointmentService.DoctorRow> doctors = List.of();
	private List<AppointmentService.PatientAppointmentRow> patientAppointments = List.of();
	private List<AppointmentService.DoctorAppointmentRow> doctorAppointments = List.of();

	@FXML
	private VBox root;

	@FXML
	private Label heroTitle;

	@FXML
	private Label heroSubtitle;

	@FXML
	private Label accessBadge;

	@FXML
	private FlowPane heroActions;

	@FXML
	private Button locateButton;

	@FXML
	private TextField cityInput;

	@FXML
	private Button searchCityButton;

	@FXML
	private Button heroVoiceButton;

	@FXML
	private VBox restrictedCard;

	@FXML
	private Label restrictedMessage;

	@FXML
	private VBox patientSection;

	@FXML
	private VBox doctorSection;

	@FXML
	private ComboBox<String> patientStatusFilter;

	@FXML
	private DatePicker patientDateFilter;

	@FXML
	private TextField patientSearchFilter;

	@FXML
	private Button patientResetFilterButton;

	@FXML
	private VBox patientAppointmentsContainer;

	@FXML
	private Button openBookingButton;

	@FXML
	private VBox bookingSection;

	@FXML
	private Button closeBookingButton;

	@FXML
	private Button bookingVoiceButton;

	@FXML
	private WebView doctorsMapView;

	@FXML
	private ComboBox<AppointmentService.DoctorRow> doctorCombo;

	@FXML
	private DatePicker bookingDatePicker;

	@FXML
	private FlowPane slotsPane;

	@FXML
	private TextArea motifInput;

	@FXML
	private Button confirmBookingButton;

	@FXML
	private Label bookingFeedback;

	@FXML
	private ComboBox<String> doctorStatusFilter;

	@FXML
	private DatePicker doctorDateFilter;

	@FXML
	private TextField doctorSearchFilter;

	@FXML
	private Button doctorResetFilterButton;

	@FXML
	private VBox doctorAppointmentsContainer;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		currentUser = AuthSession.getCurrentUser();

		setupFilterChoices();
		setupDoctorCombo();
		setupDefaultDates();
		setupListeners();
		configurePageByAccess();
	}

	@FXML
	private void handleOpenSubscription() {
		AppNavigator.showSubscriptionPage();
	}

	@FXML
	private void handleOpenBooking() {
		bookingSection.setManaged(true);
		bookingSection.setVisible(true);
		refreshSlots();
	}

	@FXML
	private void handleCloseBooking() {
		bookingSection.setManaged(false);
		bookingSection.setVisible(false);
	}

	@FXML
	private void handleSearchCity() {
		if (accessScope != AppointmentService.AccessScope.PATIENT) {
			return;
		}
		loadDoctors(cityInput.getText(), null);
	}

	@FXML
	private void handleLocate() {
		if (accessScope != AppointmentService.AccessScope.PATIENT) {
			return;
		}
		// Desktop JavaFX does not provide browser geolocation in a cross-platform way.
		showInfo("Geolocalisation", "La carte est centree sur Tunis. Utilisez la recherche par ville pour filtrer.");
	}

	@FXML
	private void handleVoiceBooking() {
		if (accessScope != AppointmentService.AccessScope.PATIENT) {
			return;
		}

		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Assistant Vocal");
		dialog.setHeaderText("Commande vocale (ar/fr)\nEx: خذي رونديفو 24/02/2026 مع دكتور حسام 10:00");

		TextArea input = new TextArea();
		input.getStyleClass().add("appt-textarea");
		input.setWrapText(true);
		input.setPrefRowCount(4);
		dialog.getDialogPane().setContent(input);
		dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
		styleDialog(dialog, false);

		dialog.setResultConverter(buttonType -> buttonType == javafx.scene.control.ButtonType.OK ? input.getText() : null);
		Optional<String> command = dialog.showAndWait();
		if (command.isEmpty() || command.get().isBlank()) {
			return;
		}

		AppointmentService.VoiceBookingResult result = appointmentService.bookFromVoice(currentUser, command.get());
		if (!result.success()) {
			showError("Commande vocale", result.message());
			return;
		}

		if (result.doctor() != null) {
			doctorCombo.getSelectionModel().select(result.doctor());
		}
		if (result.date() != null) {
			bookingDatePicker.setValue(result.date());
		}

		showSuccess(result.summary());
		reloadPatientData();
		refreshSlots();
		bookingSection.setManaged(false);
		bookingSection.setVisible(false);
	}

	@FXML
	private void handleConfirmBooking() {
		if (accessScope != AppointmentService.AccessScope.PATIENT) {
			return;
		}

		AppointmentService.DoctorRow doctor = doctorCombo.getSelectionModel().getSelectedItem();
		LocalDate date = bookingDatePicker.getValue();
		Integer selectedSlotId = selectedSlotId();

		if (doctor == null || date == null || selectedSlotId == null) {
			showError("Reservation", "Selectionnez un medecin, une date et un creneau.");
			return;
		}

		AppointmentService.ActionResult result = appointmentService.book(
				currentUser,
				doctor.id(),
				selectedSlotId,
				motifInput.getText()
		);

		if (!result.success()) {
			showError("Reservation", result.message());
			return;
		}

		motifInput.clear();
		showSuccess(result.message());
		reloadPatientData();
		refreshSlots();
		bookingSection.setManaged(false);
		bookingSection.setVisible(false);
	}

	@FXML
	private void handleResetPatientFilters() {
		patientStatusFilter.getSelectionModel().select(0);
		patientDateFilter.setValue(null);
		patientSearchFilter.clear();
		renderPatientAppointments();
	}

	@FXML
	private void handleResetDoctorFilters() {
		doctorStatusFilter.getSelectionModel().select(0);
		doctorDateFilter.setValue(null);
		doctorSearchFilter.clear();
		renderDoctorAppointments();
	}

	private void setupFilterChoices() {
		patientStatusFilter.setItems(FXCollections.observableArrayList(
				"Tous les statuts",
				"en_attente",
				"confirme",
				"refuse",
				"annule"
		));
		patientStatusFilter.getSelectionModel().selectFirst();

		doctorStatusFilter.setItems(FXCollections.observableArrayList(
				"Tous les statuts",
				"en_attente",
				"confirme",
				"refuse",
				"annule"
		));
		doctorStatusFilter.getSelectionModel().selectFirst();
	}

	private void setupDoctorCombo() {
		doctorCombo.setCellFactory(listView -> new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(AppointmentService.DoctorRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? "" : item.displayLabel());
			}
		});
		doctorCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(AppointmentService.DoctorRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? "" : item.displayLabel());
			}
		});
	}

	private void setupDefaultDates() {
		LocalDate now = LocalDate.now();
		bookingDatePicker.setValue(now);
		bookingDatePicker.setDayCellFactory(datePicker -> new javafx.scene.control.DateCell() {
			@Override
			public void updateItem(LocalDate item, boolean empty) {
				super.updateItem(item, empty);
				setDisable(empty || item.isBefore(now));
			}
		});
	}

	private void setupListeners() {
		patientStatusFilter.valueProperty().addListener((obs, oldV, newV) -> renderPatientAppointments());
		patientDateFilter.valueProperty().addListener((obs, oldV, newV) -> renderPatientAppointments());
		patientSearchFilter.textProperty().addListener((obs, oldV, newV) -> renderPatientAppointments());

		doctorStatusFilter.valueProperty().addListener((obs, oldV, newV) -> renderDoctorAppointments());
		doctorDateFilter.valueProperty().addListener((obs, oldV, newV) -> renderDoctorAppointments());
		doctorSearchFilter.textProperty().addListener((obs, oldV, newV) -> renderDoctorAppointments());

		doctorCombo.valueProperty().addListener((obs, oldV, newV) -> refreshSlots());
		bookingDatePicker.valueProperty().addListener((obs, oldV, newV) -> refreshSlots());
	}

	private void configurePageByAccess() {
		if (currentUser == null || currentUser.getId() == null) {
			AppNavigator.showLogin();
			return;
		}

		if (!appointmentService.canConnect()) {
			showError("Base de donnees", "Connexion impossible: " + appointmentService.getLastConnectionError());
		}

		accessScope = appointmentService.resolveAccess(currentUser);

		switch (accessScope) {
			case PATIENT -> configurePatientView();
			case MEDECIN -> configureDoctorView();
			case DENIED -> configureDeniedView();
		}
	}

	private void configurePatientView() {
		heroTitle.setText("Reserver un rendez-vous");
		heroSubtitle.setText("Choisissez un medecin, une date et un creneau disponible en quelques clics.");
		accessBadge.setText("Rendez-vous medicaux");

		heroActions.setVisible(true);
		heroActions.setManaged(true);

		restrictedCard.setVisible(false);
		restrictedCard.setManaged(false);

		patientSection.setVisible(true);
		patientSection.setManaged(true);
		doctorSection.setVisible(false);
		doctorSection.setManaged(false);

		bookingSection.setVisible(false);
		bookingSection.setManaged(false);

		loadDoctors("", "");
		reloadPatientData();
	}

	private void configureDoctorView() {
		heroTitle.setText("Gerer mes rendez-vous");
		heroSubtitle.setText("Consultez et gerez les demandes de vos patients en temps reel.");
		accessBadge.setText("Rendez-vous medicaux");

		heroActions.setVisible(false);
		heroActions.setManaged(false);

		restrictedCard.setVisible(false);
		restrictedCard.setManaged(false);

		patientSection.setVisible(false);
		patientSection.setManaged(false);
		doctorSection.setVisible(true);
		doctorSection.setManaged(true);

		reloadDoctorData();
	}

	private void configureDeniedView() {
		heroTitle.setText("Rendez-vous restreints");
		heroSubtitle.setText("Cette fonctionnalite demande un abonnement actif Patient ou Medecin.");
		accessBadge.setText("Acces limite");

		heroActions.setVisible(false);
		heroActions.setManaged(false);

		patientSection.setVisible(false);
		patientSection.setManaged(false);
		doctorSection.setVisible(false);
		doctorSection.setManaged(false);

		restrictedCard.setVisible(true);
		restrictedCard.setManaged(true);
		restrictedMessage.setText("Abonnement actif requis (ROLE_PATIENT ou ROLE_MEDECIN). Rendez-vous indisponible en mode "
				+ safe(currentUser.getSubscriptionStatus()) + ".");
	}

	private void loadDoctors(String city, String query) {
		doctors = appointmentService.findDoctors(city, query);
		doctorCombo.setItems(FXCollections.observableArrayList(doctors));
		if (!doctors.isEmpty()) {
			doctorCombo.getSelectionModel().selectFirst();
		}
		refreshSlots();
		renderMap();
	}

	private void reloadPatientData() {
		patientAppointments = appointmentService.findMyAppointments(currentUser);
		renderPatientAppointments();
	}

	private void reloadDoctorData() {
		doctorAppointments = appointmentService.findDoctorAppointments(currentUser);
		renderDoctorAppointments();
	}

	private void refreshSlots() {
		slotsPane.getChildren().clear();
		slotsToggleGroup.getToggles().clear();
		slotsPane.setUserData(null);

		AppointmentService.DoctorRow doctor = doctorCombo.getSelectionModel().getSelectedItem();
		LocalDate date = bookingDatePicker.getValue();
		if (doctor == null || date == null) {
			slotsPane.getChildren().add(slotEmpty("Selectionnez un medecin et une date."));
			return;
		}

		List<AppointmentService.SlotRow> slots = appointmentService.findAvailableSlots(doctor.id(), date);
		if (slots.isEmpty()) {
			slotsPane.getChildren().add(slotEmpty("Aucun creneau disponible."));
			return;
		}

		for (AppointmentService.SlotRow slot : slots) {
			ToggleButton slotButton = new ToggleButton(slot.label());
			slotButton.getStyleClass().add("appt-slot-btn");
			slotButton.setToggleGroup(slotsToggleGroup);
			slotButton.setOnAction(event -> slotsPane.setUserData(slot.id()));
			slotsPane.getChildren().add(slotButton);
		}
	}

	private void renderPatientAppointments() {
		patientAppointmentsContainer.getChildren().clear();

		String statusFilter = normalizedStatusFilter(patientStatusFilter.getValue());
		LocalDate dateFilter = patientDateFilter.getValue();
		String searchFilter = safe(patientSearchFilter.getText()).toLowerCase(Locale.ROOT);

		List<AppointmentService.PatientAppointmentRow> visibleRows = patientAppointments.stream()
				.filter(row -> statusFilter.isBlank() || safe(row.statut()).equalsIgnoreCase(statusFilter))
				.filter(row -> dateFilter == null || dateFilter.equals(row.date()))
				.filter(row -> searchFilter.isBlank() || safe(row.medecin()).toLowerCase(Locale.ROOT).contains(searchFilter))
				.toList();

		if (visibleRows.isEmpty()) {
			patientAppointmentsContainer.getChildren().add(emptyRow("Aucun rendez-vous."));
			return;
		}

		for (AppointmentService.PatientAppointmentRow row : visibleRows) {
			patientAppointmentsContainer.getChildren().add(buildPatientRow(row));
		}
	}

	private void renderDoctorAppointments() {
		doctorAppointmentsContainer.getChildren().clear();

		String statusFilter = normalizedStatusFilter(doctorStatusFilter.getValue());
		LocalDate dateFilter = doctorDateFilter.getValue();
		String searchFilter = safe(doctorSearchFilter.getText()).toLowerCase(Locale.ROOT);

		List<AppointmentService.DoctorAppointmentRow> visibleRows = doctorAppointments.stream()
				.filter(row -> statusFilter.isBlank() || safe(row.statut()).equalsIgnoreCase(statusFilter))
				.filter(row -> dateFilter == null || dateFilter.equals(row.date()))
				.filter(row -> searchFilter.isBlank() || safe(row.patient()).toLowerCase(Locale.ROOT).contains(searchFilter))
				.toList();

		if (visibleRows.isEmpty()) {
			doctorAppointmentsContainer.getChildren().add(emptyRow("Aucun rendez-vous."));
			return;
		}

		for (AppointmentService.DoctorAppointmentRow row : visibleRows) {
			doctorAppointmentsContainer.getChildren().add(buildDoctorRow(row));
		}
	}

	private HBox buildPatientRow(AppointmentService.PatientAppointmentRow row) {
		HBox wrapper = new HBox(14);
		wrapper.getStyleClass().add("appt-row");
		wrapper.setAlignment(Pos.CENTER_LEFT);

		VBox meta = new VBox(4);
		Label title = new Label(row.medecin());
		title.getStyleClass().add("appt-row-title");
		Label date = new Label(row.dateFr() + " · " + row.heureFr());
		date.getStyleClass().add("appt-row-meta");
		Label note = new Label(safe(row.motif()).isBlank() ? "-" : row.motif());
		note.getStyleClass().add("appt-row-note");

		Label status = new Label(statusLabel(row.statut()));
		status.getStyleClass().addAll("appt-status-pill", statusClass(row.statut()));
		meta.getChildren().addAll(title, date, note, status);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox actions = new HBox(8);
		Button reschedule = new Button("Replanifier");
		reschedule.getStyleClass().add("appt-btn-secondary");
		reschedule.setOnAction(event -> onPatientReschedule(row));

		Button cancel = new Button("Annuler");
		cancel.getStyleClass().add("appt-btn-danger");
		cancel.setOnAction(event -> onPatientCancel(row));
		actions.getChildren().addAll(reschedule, cancel);

		wrapper.getChildren().addAll(meta, spacer, actions);
		return wrapper;
	}

	private HBox buildDoctorRow(AppointmentService.DoctorAppointmentRow row) {
		HBox wrapper = new HBox(14);
		wrapper.getStyleClass().add("appt-row");
		wrapper.setAlignment(Pos.CENTER_LEFT);

		VBox meta = new VBox(4);
		Label title = new Label(row.patient());
		title.getStyleClass().add("appt-row-title");
		Label date = new Label(row.dateFr() + " · " + row.heureFr());
		date.getStyleClass().add("appt-row-meta");
		Label note = new Label(safe(row.motif()).isBlank() ? "-" : row.motif());
		note.getStyleClass().add("appt-row-note");
		meta.getChildren().addAll(title, date, note);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox actions = new HBox(8);
		String status = safe(row.statut()).toLowerCase(Locale.ROOT);
		if ("en_attente".equals(status)) {
			Button confirm = new Button("Confirmer");
			confirm.getStyleClass().add("appt-btn-primary");
			confirm.setOnAction(event -> onDoctorStatus(row, "confirme"));

			Button refuse = new Button("Refuse");
			refuse.getStyleClass().add("appt-btn-secondary");
			refuse.setOnAction(event -> onDoctorStatus(row, "refuse"));

			Button cancel = new Button("Annuler");
			cancel.getStyleClass().add("appt-btn-danger");
			cancel.setOnAction(event -> onDoctorStatus(row, "annule"));

			actions.getChildren().addAll(confirm, refuse, cancel);
		} else {
			Button reschedule = new Button("Replanifier");
			reschedule.getStyleClass().add("appt-btn-secondary");
			reschedule.setOnAction(event -> onDoctorReschedule(row));

			Label statusPill = new Label(statusLabel(status));
			statusPill.getStyleClass().addAll("appt-status-pill", statusClass(status));

			actions.getChildren().addAll(reschedule, statusPill);
		}

		wrapper.getChildren().addAll(meta, spacer, actions);
		return wrapper;
	}

	private void onPatientCancel(AppointmentService.PatientAppointmentRow row) {
		if (!confirm("Annuler", "Annuler ce rendez-vous ?")) {
			return;
		}
		AppointmentService.ActionResult result = appointmentService.cancelByPatient(currentUser, row.id());
		if (!result.success()) {
			showError("Annulation", result.message());
			return;
		}
		showSuccess(result.message());
		reloadPatientData();
		refreshSlots();
	}

	private void onPatientReschedule(AppointmentService.PatientAppointmentRow row) {
		Optional<RescheduleInput> reschedule = openRescheduleDialog("Replanifier (patient)", true);
		if (reschedule.isEmpty()) {
			return;
		}
		RescheduleInput input = reschedule.get();
		AppointmentService.ActionResult result = appointmentService.requestPatientReschedule(
				currentUser,
				row.id(),
				input.date(),
				input.time()
		);
		if (!result.success()) {
			showError("Replanification", result.message());
			return;
		}
		showSuccess(result.message());
		reloadPatientData();
	}

	private void onDoctorStatus(AppointmentService.DoctorAppointmentRow row, String status) {
		AppointmentService.ActionResult result = appointmentService.updateDoctorStatus(currentUser, row.id(), status);
		if (!result.success()) {
			showError("Statut", result.message());
			return;
		}
		showSuccess(result.message());
		reloadDoctorData();
	}

	private void onDoctorReschedule(AppointmentService.DoctorAppointmentRow row) {
		Optional<RescheduleInput> reschedule = openRescheduleDialog("Replanifier (medecin)", false);
		if (reschedule.isEmpty()) {
			return;
		}
		RescheduleInput input = reschedule.get();
		AppointmentService.ActionResult result = appointmentService.rescheduleByDoctor(
				currentUser,
				row.id(),
				input.date(),
				input.time()
		);
		if (!result.success()) {
			showError("Replanification", result.message());
			return;
		}
		showSuccess(result.message());
		reloadDoctorData();
	}

	private Optional<RescheduleInput> openRescheduleDialog(String title, boolean withPendingHint) {
		Dialog<RescheduleInput> dialog = new Dialog<>();
		dialog.initModality(Modality.NONE);
		dialog.setTitle(title);
		dialog.setHeaderText(withPendingHint
				? "Choisissez une nouvelle date/heure. Le medecin devra approuver."
				: "Choisissez une nouvelle date/heure.");
		dialog.setResizable(false);
		dialog.getDialogPane().setMinWidth(520);
		dialog.getDialogPane().setPrefWidth(560);
		dialog.getDialogPane().setMinHeight(280);
		dialog.getDialogPane().setPrefHeight(300);

		DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(1));
		datePicker.getStyleClass().add("appt-filter-control");
		datePicker.setMaxWidth(Double.MAX_VALUE);
		TextField timeField = new TextField("09:00");
		timeField.getStyleClass().add("appt-filter-control");
		timeField.setMaxWidth(Double.MAX_VALUE);

		VBox content = new VBox(10,
				labeled("Date", datePicker),
				labeled("Heure (HH:mm)", timeField)
		);
		content.getStyleClass().add("appt-card");
		content.setPadding(new Insets(8, 4, 0, 4));
		dialog.getDialogPane().setContent(content);

		ButtonType confirmButton = new ButtonType("Confirmer", ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
		dialog.getDialogPane().getButtonTypes().addAll(confirmButton, cancelButton);
		styleDialog(dialog, false);

		dialog.setResultConverter(buttonType -> {
			if (buttonType != confirmButton) {
				return null;
			}
			LocalDate date = datePicker.getValue();
			LocalTime time = parseTime(timeField.getText());
			if (date == null || time == null) {
				return null;
			}
			return new RescheduleInput(date, time);
		});

		Optional<RescheduleInput> result = dialog.showAndWait();
		if (result.isEmpty()) {
			return Optional.empty();
		}
		if (result.get().date().isBefore(LocalDate.now())) {
			showError("Replanification", "La date doit etre future.");
			return Optional.empty();
		}
		return result;
	}

	private VBox labeled(String label, javafx.scene.Node node) {
		Label l = new Label(label);
		l.getStyleClass().add("appt-field-label");
		VBox box = new VBox(5, l, node);
		return box;
	}

	private LocalTime parseTime(String raw) {
		String normalized = safe(raw).replace('h', ':').replace('H', ':');
		try {
			if (normalized.matches("\\d{1,2}:\\d{2}")) {
				String[] parts = normalized.split(":");
				int h = Integer.parseInt(parts[0]);
				int m = Integer.parseInt(parts[1]);
				if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
					return LocalTime.of(h, m);
				}
			}
		} catch (RuntimeException ignored) {
		}
		return null;
	}

	private void renderMap() {
		WebEngine engine = doctorsMapView.getEngine();
		engine.loadContent(buildMapHtml(doctors));
	}

	private String buildMapHtml(List<AppointmentService.DoctorRow> rows) {
		StringBuilder markers = new StringBuilder();
		for (AppointmentService.DoctorRow row : rows) {
			if (row.lat() == null || row.lng() == null) {
				continue;
			}
			String label = escapeJs(row.displayLabel() + " - " + safe(row.ville()));
			markers.append("L.marker([").append(row.lat()).append(',').append(row.lng()).append("]).addTo(map).bindPopup('")
					.append(label)
					.append("');");
		}

		return "<!doctype html><html><head><meta charset='utf-8'>"
				+ "<meta name='viewport' content='width=device-width,initial-scale=1'>"
				+ "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>"
				+ "<style>html,body,#map{block-size:100%;margin:0}body{background:#eef5fb}</style>"
				+ "</head><body><div id='map'></div>"
				+ "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>"
				+ "<script>const map=L.map('map').setView([36.8065,10.1815],10);"
				+ "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);"
				+ markers
				+ "</script></body></html>";
	}

	private Label slotEmpty(String text) {
		Label empty = new Label(text);
		empty.getStyleClass().add("appt-slot-empty");
		return empty;
	}

	private HBox emptyRow(String text) {
		HBox box = new HBox();
		box.getStyleClass().add("appt-row-empty");
		Label label = new Label(text);
		label.getStyleClass().add("appt-row-empty-label");
		box.getChildren().add(label);
		return box;
	}

	private Integer selectedSlotId() {
		Object value = slotsPane.getUserData();
		if (value instanceof Integer integer) {
			return integer;
		}
		return null;
	}

	private String normalizedStatusFilter(String value) {
		String normalized = safe(value);
		if (normalized.equalsIgnoreCase("Tous les statuts")) {
			return "";
		}
		return normalized;
	}

	private String statusLabel(String status) {
		String normalized = safe(status).toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "confirme" -> "Confirme";
			case "refuse" -> "Refuse";
			case "annule" -> "Annule";
			default -> "En attente";
		};
	}

	private String statusClass(String status) {
		String normalized = safe(status).toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "confirme" -> "appt-status-confirmed";
			case "refuse" -> "appt-status-refused";
			case "annule" -> "appt-status-cancelled";
			default -> "appt-status-pending";
		};
	}

	private void showSuccess(String message) {
		bookingFeedback.setText(message);
		bookingFeedback.getStyleClass().removeAll("appt-feedback-error");
		if (!bookingFeedback.getStyleClass().contains("appt-feedback-success")) {
			bookingFeedback.getStyleClass().add("appt-feedback-success");
		}
	}

	private void showError(String title, String message) {
		bookingFeedback.setText(message);
		bookingFeedback.getStyleClass().removeAll("appt-feedback-success");
		if (!bookingFeedback.getStyleClass().contains("appt-feedback-error")) {
			bookingFeedback.getStyleClass().add("appt-feedback-error");
		}
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);
		styleDialog(alert, true);
		alert.showAndWait();
	}

	private void showInfo(String title, String message) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);
		styleDialog(alert, false);
		alert.showAndWait();
	}

	private boolean confirm(String title, String message) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);

		ButtonType approveButton = new ButtonType("Oui, confirmer", ButtonBar.ButtonData.OK_DONE);
		ButtonType backButton = new ButtonType("Retour", ButtonBar.ButtonData.CANCEL_CLOSE);
		alert.getButtonTypes().setAll(approveButton, backButton);
		styleDialog(alert, true);
		return alert.showAndWait().orElse(backButton) == approveButton;
	}

	private void styleDialog(Dialog<?> dialog, boolean destructive) {
		dialog.getDialogPane().getStylesheets().add(getClass().getResource(APPOINTMENT_STYLESHEET).toExternalForm());
		dialog.getDialogPane().getStyleClass().add("appt-dialog");
		if (destructive) {
			dialog.getDialogPane().getStyleClass().add("appt-dialog-danger");
		}
	}

	private String escapeJs(String value) {
		return safe(value)
			.replace("\\", "\\\\")
			.replace("'", "\\'")
			.replace("\n", " ");
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}

	private record RescheduleInput(LocalDate date, LocalTime time) {
	}
}
