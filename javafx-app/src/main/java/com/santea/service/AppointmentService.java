package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class AppointmentService {
	private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter TIME_FR = DateTimeFormatter.ofPattern("HH:mm");

	public static final String VOICE_MODEL_HINT = "الموديل الصوتي لازم. عيّن VOICE_MODEL_PATH.";
	public static final String VOICE_WELCOME = "أهلا، نجم نعملك رانديفو. مثال: نحب رانديفو غدوة 10:00 مع الدكتور علي.";
	public static final String VOICE_STATUS_ONLINE = "على الخط";
	public static final String VOICE_STATUS_LISTENING = "نسمع...";
	public static final String VOICE_STATUS_PROCESSING = "نحلّل...";
	public static final String VOICE_STATUS_READY = "جاهز للسمع";
	public static final String VOICE_STATUS_BOOKED = "تم تسجيل الموعد";
	public static final String VOICE_HINT_READY = "أنا حاضر - احكيلي طلبك.";
	public static final String VOICE_HEARD_PREFIX = "سمعت: ";
	public static final String VOICE_ERROR_MIC_PREFIX = "مشكل في الميكرو: ";
	public static final String VOICE_USER_LABEL = "إنت";
	public static final String VOICE_ASSISTANT_LABEL = "المساعد";
	public static final String VOICE_SUCCESS_MESSAGE = "تم تسجيل الرانديفو. نستنّاو التأكيد.";
	public static final String VOICE_ERR_REMOTE_UNAVAILABLE = "التفريغ الصوتي عن بعد موش متاح.";
	public static final String VOICE_ERR_MODEL_MISSING = "الموديل الصوتي Vosk موش موجود. عيّن VOICE_MODEL_PATH.";
	public static final String VOICE_ERR_MODEL_LOAD = "ما نجمتش نحمل موديل Vosk.";
	public static final String VOICE_ERR_INIT = "ما نجمتش نهيّأ التعرّف الصوتي.";
	public static final String VOICE_ERR_NO_AUDIO = "ما تسجّل حتى صوت.";
	public static final String VOICE_ERR_MIC = "مشكل في الميكرو.";
	public static final String VOICE_ERR_MIC_UNAVAILABLE = "الميكرو موش متاح.";
	public static final String VOICE_ERR_TRANSCRIPTION_EMPTY = "التفريغ الصوتي فارغ.";
	public static final String VOICE_PROMPT_MISSING_DATE = "شنو التاريخ؟ مثال: غدوة ولا 24/02/2026.";
	public static final String VOICE_PROMPT_MISSING_DOCTOR = "شنو اسم الطبيب ولا الاختصاص؟";

	private final DatabaseService databaseService;
	private final AiGatewayService aiGatewayService;
	private final EmailService emailService;

	public AppointmentService() {
		this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
		this.aiGatewayService = new AiGatewayService();
		this.emailService = new EmailService();
		ensureAppointmentTables();
	}

	public boolean canConnect() {
		return databaseService.canConnect();
	}

	public String getLastConnectionError() {
		return databaseService.getLastConnectionError();
	}

	public AccessScope resolveAccess(User user) {
		if (user == null || user.getId() == null) {
			return AccessScope.DENIED;
		}

		String status = safe(user.getSubscriptionStatus()).toUpperCase(Locale.ROOT);
		String role = effectiveRole(user);

		if (!"ACTIVE".equals(status)) {
			return AccessScope.DENIED;
		}
		if ("ROLE_PATIENT".equals(role)) {
			return AccessScope.PATIENT;
		}
		if ("ROLE_MEDECIN".equals(role)) {
			return AccessScope.MEDECIN;
		}
		return AccessScope.DENIED;
	}

	public List<DoctorRow> findDoctors(String city, String query) {
		if (!canConnect()) {
			return List.of();
		}

		StringBuilder sql = new StringBuilder();
		sql.append("SELECT m.id, m.specialite, m.cabinet_ville, m.cabinet_lat, m.cabinet_lng, ")
				.append("u.nom, u.prenom ")
				.append("FROM medecins m ")
				.append("INNER JOIN users u ON u.id = m.user_id ")
				.append("WHERE 1=1 ");

		List<String> params = new ArrayList<>();
		if (!safe(city).isBlank()) {
			sql.append("AND LOWER(COALESCE(m.cabinet_ville, '')) LIKE ? ");
			params.add("%" + city.trim().toLowerCase(Locale.ROOT) + "%");
		}
		if (!safe(query).isBlank()) {
			sql.append("AND LOWER(CONCAT(COALESCE(u.nom,''),' ',COALESCE(u.prenom,''),' ',COALESCE(m.specialite,''))) LIKE ? ");
			params.add("%" + query.trim().toLowerCase(Locale.ROOT) + "%");
		}
		sql.append("ORDER BY u.nom ASC, u.prenom ASC");

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql.toString())) {
			for (int i = 0; i < params.size(); i++) {
				statement.setString(i + 1, params.get(i));
			}

			List<DoctorRow> rows = new ArrayList<>();
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					rows.add(new DoctorRow(
							resultSet.getInt("id"),
							safe(resultSet.getString("nom")),
							safe(resultSet.getString("prenom")),
							safe(resultSet.getString("specialite")),
							safe(resultSet.getString("cabinet_ville")),
							nullableDouble(resultSet.getString("cabinet_lat")),
							nullableDouble(resultSet.getString("cabinet_lng"))
					));
				}
			}
			return rows;
		} catch (SQLException exception) {
			return List.of();
		}
	}

	public List<SlotRow> findAvailableSlots(int medecinId, LocalDate date) {
		if (medecinId <= 0 || date == null || !canConnect()) {
			return List.of();
		}

		ensureDefaultDisponibilites(medecinId, date);

		String sql = "SELECT id, heure_debut, heure_fin FROM disponibilites "
				+ "WHERE medecin_id = ? AND date = ? AND statut = 'disponible' "
				+ "ORDER BY heure_debut ASC";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, medecinId);
			statement.setDate(2, Date.valueOf(date));

			List<SlotRow> rows = new ArrayList<>();
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					LocalTime start = toLocalTime(resultSet.getTime("heure_debut"));
					LocalTime end = toLocalTime(resultSet.getTime("heure_fin"));
					rows.add(new SlotRow(
							resultSet.getInt("id"),
							start,
							end,
							formatSlot(start, end)
					));
				}
			}
			return rows;
		} catch (SQLException exception) {
			return List.of();
		}
	}

	public ActionResult book(User user, int medecinId, int disponibiliteId, String motif) {
		if (resolveAccess(user) != AccessScope.PATIENT) {
			return ActionResult.failure("Abonnement patient requis.");
		}
		if (medecinId <= 0 || disponibiliteId <= 0) {
			return ActionResult.failure("Medecin et creneau obligatoires.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		Integer patientId = ensurePatientForUser(user.getId());
		if (patientId == null) {
			return ActionResult.failure("Acces patient requis.");
		}

		String lockSlotSql = "SELECT id, medecin_id, date, heure_debut, statut FROM disponibilites WHERE id = ? FOR UPDATE";
		String insertRdvSql = "INSERT INTO rendez_vous (patient_id, medecin_id, disponibilite_id, date_rdv, heure_rdv, motif, statut, created_at, updated_at) "
				+ "VALUES (?, ?, ?, ?, ?, ?, 'en_attente', ?, ?)";
		String reserveSlotSql = "UPDATE disponibilites SET statut = 'reserve', updated_at = ? WHERE id = ?";

		try (Connection connection = databaseService.getConnection()) {
			connection.setAutoCommit(false);

			int realMedecinId;
			LocalDate slotDate;
			LocalTime slotStart;
			String slotStatus;

			try (PreparedStatement lock = connection.prepareStatement(lockSlotSql)) {
				lock.setInt(1, disponibiliteId);
				try (ResultSet rs = lock.executeQuery()) {
					if (!rs.next()) {
						connection.rollback();
						return ActionResult.failure("Creneau introuvable.");
					}

					realMedecinId = rs.getInt("medecin_id");
					slotDate = toLocalDate(rs.getDate("date"));
					slotStart = toLocalTime(rs.getTime("heure_debut"));
					slotStatus = safe(rs.getString("statut"));
				}
			}

			if (realMedecinId != medecinId) {
				connection.rollback();
				return ActionResult.failure("Creneau invalide pour ce medecin.");
			}
			if (!"disponible".equalsIgnoreCase(slotStatus)) {
				connection.rollback();
				return ActionResult.failure("Creneau deja reserve.");
			}

			int appointmentId = 0;
			Timestamp now = Timestamp.valueOf(LocalDateTime.now());
			try (PreparedStatement insert = connection.prepareStatement(insertRdvSql, Statement.RETURN_GENERATED_KEYS)) {
				insert.setInt(1, patientId);
				insert.setInt(2, medecinId);
				insert.setInt(3, disponibiliteId);
				insert.setDate(4, Date.valueOf(slotDate));
				insert.setTime(5, Time.valueOf(slotStart));
				insert.setString(6, emptyToNull(motif));
				insert.setTimestamp(7, now);
				insert.setTimestamp(8, now);
				insert.executeUpdate();

				try (ResultSet keys = insert.getGeneratedKeys()) {
					if (keys.next()) {
						appointmentId = keys.getInt(1);
					}
				}
			}

			try (PreparedStatement reserve = connection.prepareStatement(reserveSlotSql)) {
				reserve.setTimestamp(1, now);
				reserve.setInt(2, disponibiliteId);
				reserve.executeUpdate();
			}

			notifyByAppointment(connection, appointmentId, "Rendez-vous en attente");
			connection.commit();
			sendAppointmentRequestEmail(appointmentId);

			return ActionResult.success("Rendez-vous cree en attente de confirmation.", appointmentId);
		} catch (SQLException exception) {
			return ActionResult.failure("Reservation impossible: " + exception.getMessage());
		}
	}

	public List<PatientAppointmentRow> findMyAppointments(User user) {
		if (resolveAccess(user) != AccessScope.PATIENT || !canConnect()) {
			return List.of();
		}

		Integer patientId = findPatientIdByUser(user.getId());
		if (patientId == null) {
			return List.of();
		}

		String sql = "SELECT r.id, r.date_rdv, r.heure_rdv, r.statut, r.motif, "
				+ "u.nom AS med_nom, u.prenom AS med_prenom "
				+ "FROM rendez_vous r "
				+ "INNER JOIN medecins m ON m.id = r.medecin_id "
				+ "INNER JOIN users u ON u.id = m.user_id "
				+ "WHERE r.patient_id = ? "
				+ "ORDER BY r.date_rdv ASC, r.heure_rdv ASC";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, patientId);
			List<PatientAppointmentRow> rows = new ArrayList<>();
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					LocalDate date = toLocalDate(resultSet.getDate("date_rdv"));
					LocalTime time = toLocalTime(resultSet.getTime("heure_rdv"));
					rows.add(new PatientAppointmentRow(
							resultSet.getInt("id"),
							humanName(resultSet.getString("med_nom"), resultSet.getString("med_prenom"), "Medecin"),
							date,
							time,
							safe(resultSet.getString("statut")),
							safe(resultSet.getString("motif"))
					));
				}
			}
			return rows;
		} catch (SQLException exception) {
			return List.of();
		}
	}

	public ActionResult cancelByPatient(User user, int appointmentId) {
		if (resolveAccess(user) != AccessScope.PATIENT) {
			return ActionResult.failure("Abonnement patient requis.");
		}
		if (appointmentId <= 0 || !canConnect()) {
			return ActionResult.failure("Rendez-vous introuvable.");
		}

		Integer patientId = findPatientIdByUser(user.getId());
		if (patientId == null) {
			return ActionResult.failure("Acces patient requis.");
		}

		return cancelInternal(patientId, appointmentId, true);
	}

	public ActionResult requestPatientReschedule(User user, int appointmentId, LocalDate date, LocalTime time) {
		if (resolveAccess(user) != AccessScope.PATIENT) {
			return ActionResult.failure("Abonnement patient requis.");
		}
		if (appointmentId <= 0 || date == null || time == null) {
			return ActionResult.failure("Date et heure requises.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		Integer patientId = findPatientIdByUser(user.getId());
		if (patientId == null) {
			return ActionResult.failure("Acces patient requis.");
		}

		String lockRdvSql = "SELECT id, medecin_id, disponibilite_id FROM rendez_vous WHERE id = ? AND patient_id = ? FOR UPDATE";
		try (Connection connection = databaseService.getConnection()) {
			connection.setAutoCommit(false);

			Integer medecinId = null;
			Integer oldDisponibiliteId = null;
			try (PreparedStatement lock = connection.prepareStatement(lockRdvSql)) {
				lock.setInt(1, appointmentId);
				lock.setInt(2, patientId);
				try (ResultSet rs = lock.executeQuery()) {
					if (!rs.next()) {
						connection.rollback();
						return ActionResult.failure("Rendez-vous introuvable.");
					}
					medecinId = rs.getInt("medecin_id");
					oldDisponibiliteId = nullableInt(rs, "disponibilite_id");
				}
			}

			Integer newDisponibiliteId = findOrCreateDisponibilite(connection, medecinId, date, time);
			if (newDisponibiliteId == null) {
				connection.rollback();
				return ActionResult.failure("Impossible de creer le nouveau creneau.");
			}

			ActionResult move = moveAppointmentToSlot(connection, appointmentId, oldDisponibiliteId, newDisponibiliteId, date, time, "en_attente");
			if (!move.success()) {
				connection.rollback();
				return move;
			}

			notifyByAppointment(connection, appointmentId, "Demande de replanification");
			connection.commit();
			return ActionResult.success("Demande de replanification envoyee.", appointmentId);
		} catch (SQLException exception) {
			return ActionResult.failure("Replanification impossible: " + exception.getMessage());
		}
	}

	public List<DoctorAppointmentRow> findDoctorAppointments(User user) {
		if (resolveAccess(user) != AccessScope.MEDECIN || !canConnect()) {
			return List.of();
		}

		Integer medecinId = findMedecinIdByUser(user.getId());
		if (medecinId == null) {
			return List.of();
		}

		String sql = "SELECT r.id, r.date_rdv, r.heure_rdv, r.statut, r.motif, "
				+ "u.nom AS pat_nom, u.prenom AS pat_prenom "
				+ "FROM rendez_vous r "
				+ "INNER JOIN patients p ON p.id = r.patient_id "
				+ "INNER JOIN users u ON u.id = p.user_id "
				+ "WHERE r.medecin_id = ? "
				+ "ORDER BY r.date_rdv ASC, r.heure_rdv ASC";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, medecinId);
			List<DoctorAppointmentRow> rows = new ArrayList<>();
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					rows.add(new DoctorAppointmentRow(
							resultSet.getInt("id"),
							humanName(resultSet.getString("pat_nom"), resultSet.getString("pat_prenom"), "Patient"),
							toLocalDate(resultSet.getDate("date_rdv")),
							toLocalTime(resultSet.getTime("heure_rdv")),
							safe(resultSet.getString("statut")),
							safe(resultSet.getString("motif"))
					));
				}
			}
			return rows;
		} catch (SQLException exception) {
			return List.of();
		}
	}

	public ActionResult updateDoctorStatus(User user, int appointmentId, String status) {
		if (resolveAccess(user) != AccessScope.MEDECIN) {
			return ActionResult.failure("Abonnement medecin requis.");
		}
		String normalizedStatus = safe(status).toLowerCase(Locale.ROOT);
		if (!List.of("confirme", "refuse", "annule").contains(normalizedStatus)) {
			return ActionResult.failure("Statut invalide.");
		}
		if (appointmentId <= 0 || !canConnect()) {
			return ActionResult.failure("Rendez-vous introuvable.");
		}

		Integer medecinId = findMedecinIdByUser(user.getId());
		if (medecinId == null) {
			return ActionResult.failure("Acces medecin requis.");
		}

		String lockSql = "SELECT disponibilite_id FROM rendez_vous WHERE id = ? AND medecin_id = ? FOR UPDATE";
		String updateSql = "UPDATE rendez_vous SET statut = ?, updated_at = ? WHERE id = ?";
		String freeSlotSql = "UPDATE disponibilites SET statut = 'disponible', updated_at = ? WHERE id = ?";

		try (Connection connection = databaseService.getConnection()) {
			connection.setAutoCommit(false);

			Integer disponibiliteId = null;
			try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
				lock.setInt(1, appointmentId);
				lock.setInt(2, medecinId);
				try (ResultSet rs = lock.executeQuery()) {
					if (!rs.next()) {
						connection.rollback();
						return ActionResult.failure("Rendez-vous introuvable.");
					}
					disponibiliteId = nullableInt(rs, "disponibilite_id");
				}
			}

			Timestamp now = Timestamp.valueOf(LocalDateTime.now());
			try (PreparedStatement update = connection.prepareStatement(updateSql)) {
				update.setString(1, normalizedStatus);
				update.setTimestamp(2, now);
				update.setInt(3, appointmentId);
				update.executeUpdate();
			}

			if (("refuse".equals(normalizedStatus) || "annule".equals(normalizedStatus)) && disponibiliteId != null) {
				try (PreparedStatement free = connection.prepareStatement(freeSlotSql)) {
					free.setTimestamp(1, now);
					free.setInt(2, disponibiliteId);
					free.executeUpdate();
				}
			}

			notifyByAppointment(connection, appointmentId, "Mise a jour du rendez-vous");
			connection.commit();
			return ActionResult.success("Statut mis a jour.", appointmentId);
		} catch (SQLException exception) {
			return ActionResult.failure("Mise a jour impossible: " + exception.getMessage());
		}
	}

	public ActionResult rescheduleByDoctor(User user, int appointmentId, LocalDate date, LocalTime time) {
		if (resolveAccess(user) != AccessScope.MEDECIN) {
			return ActionResult.failure("Abonnement medecin requis.");
		}
		if (appointmentId <= 0 || date == null || time == null) {
			return ActionResult.failure("Date et heure requises.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		Integer medecinId = findMedecinIdByUser(user.getId());
		if (medecinId == null) {
			return ActionResult.failure("Acces medecin requis.");
		}

		String lockRdvSql = "SELECT disponibilite_id FROM rendez_vous WHERE id = ? AND medecin_id = ? FOR UPDATE";
		try (Connection connection = databaseService.getConnection()) {
			connection.setAutoCommit(false);

			Integer oldDisponibiliteId = null;
			try (PreparedStatement lock = connection.prepareStatement(lockRdvSql)) {
				lock.setInt(1, appointmentId);
				lock.setInt(2, medecinId);
				try (ResultSet rs = lock.executeQuery()) {
					if (!rs.next()) {
						connection.rollback();
						return ActionResult.failure("Rendez-vous introuvable.");
					}
					oldDisponibiliteId = nullableInt(rs, "disponibilite_id");
				}
			}

			Integer newDisponibiliteId = findOrCreateDisponibilite(connection, medecinId, date, time);
			if (newDisponibiliteId == null) {
				connection.rollback();
				return ActionResult.failure("Impossible de creer le nouveau creneau.");
			}

			ActionResult move = moveAppointmentToSlot(connection, appointmentId, oldDisponibiliteId, newDisponibiliteId, date, time, "confirme");
			if (!move.success()) {
				connection.rollback();
				return move;
			}

			notifyByAppointment(connection, appointmentId, "Rendez-vous replanifie");
			connection.commit();
			return ActionResult.success("Rendez-vous replanifie.", appointmentId);
		} catch (SQLException exception) {
			return ActionResult.failure("Replanification impossible: " + exception.getMessage());
		}
	}

	public VoiceBookingResult bookFromVoice(User user, String rawCommand) {
		if (resolveAccess(user) != AccessScope.PATIENT) {
			return VoiceBookingResult.failure("يلزمك اشتراك مريض.");
		}
		String command = safe(rawCommand);
		if (command.isBlank()) {
			return VoiceBookingResult.failure("ما فماش كلام مسموع.");
		}

		String normalized = normalizeVoiceCommand(command);
		VoiceIntentData intent = new VoiceIntentData(normalized);
		boolean aiConfigured = aiGatewayService.isIntentEndpointConfigured();
		boolean aiStrict = aiConfigured && aiGatewayService.isStrictIntentMode();
		boolean aiFilled = false;
		if (aiConfigured) {
			Optional<AiGatewayService.AppointmentIntent> aiOpt = aiGatewayService.extractAppointmentIntent(command);
			if (aiOpt.isPresent()) {
				AiGatewayService.AppointmentIntent ai = aiOpt.get();
				if (!ai.isEmpty()) {
					intent.doctor = safe(ai.doctor());
					intent.specialty = safe(ai.specialty());
					intent.date = ai.date();
					intent.time = ai.time();
					aiFilled = true;
				}
			}
		}

		if (aiStrict && !aiFilled) {
			// Fallback sur l'analyse locale si l'intent distant est indisponible.
		}

		if (intent.date == null) {
			intent.date = extractDate(normalized);
		}
		if (intent.time == null) {
			intent.time = extractTime(normalized, intent.date);
		}
		if (intent.specialty == null || intent.specialty.isBlank()) {
			intent.specialty = extractSpecialty(normalized);
		}
		if (intent.doctor == null || intent.doctor.isBlank()) {
			intent.doctor = extractDoctorHint(normalized);
		}

		if (intent.date == null) {
			return VoiceBookingResult.failure(VOICE_PROMPT_MISSING_DATE);
		}

		String searchHint = firstNonBlank(intent.doctor, intent.specialty, normalized);
		if (searchHint.isBlank()) {
			return VoiceBookingResult.failure(VOICE_PROMPT_MISSING_DOCTOR);
		}

		DoctorRow doctor = matchDoctorFromVoiceText(searchHint);
		if (doctor == null) {
			List<DoctorRow> doctors = findDoctors("", searchHint);
			if (doctors.isEmpty()) {
				return VoiceBookingResult.failure("الطبيب موش موجود.");
			}
			doctor = doctors.get(0);
		}
		LocalTime requested = intent.time;

		List<SlotRow> slots = findAvailableSlots(doctor.id(), intent.date);
		if (slots.isEmpty()) {
			return VoiceBookingResult.failure("ما فمّاش مواعيد متاحة في النهار هذا.");
		}

		SlotRow selected = pickBestSlot(slots, requested);
		ActionResult booking = book(user, doctor.id(), selected.id(), "أمر صوتي: " + truncate(command, 180));
		if (!booking.success()) {
			return VoiceBookingResult.failure(booking.message());
		}

		return VoiceBookingResult.success(
				VOICE_SUCCESS_MESSAGE,
				booking.appointmentId(),
				doctor,
				selected,
				intent.date,
				requested
		);
	}

	private ActionResult cancelInternal(Integer patientId, int appointmentId, boolean patientFlow) {
		String lockSql = "SELECT disponibilite_id FROM rendez_vous WHERE id = ? AND patient_id = ? FOR UPDATE";
		String updateRdvSql = "UPDATE rendez_vous SET statut = 'annule', updated_at = ? WHERE id = ?";
		String freeSlotSql = "UPDATE disponibilites SET statut = 'disponible', updated_at = ? WHERE id = ?";

		try (Connection connection = databaseService.getConnection()) {
			connection.setAutoCommit(false);

			Integer disponibiliteId = null;
			try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
				lock.setInt(1, appointmentId);
				lock.setInt(2, patientId);
				try (ResultSet rs = lock.executeQuery()) {
					if (!rs.next()) {
						connection.rollback();
						return ActionResult.failure("Rendez-vous introuvable.");
					}
					disponibiliteId = nullableInt(rs, "disponibilite_id");
				}
			}

			Timestamp now = Timestamp.valueOf(LocalDateTime.now());
			try (PreparedStatement update = connection.prepareStatement(updateRdvSql)) {
				update.setTimestamp(1, now);
				update.setInt(2, appointmentId);
				update.executeUpdate();
			}

			if (disponibiliteId != null) {
				try (PreparedStatement free = connection.prepareStatement(freeSlotSql)) {
					free.setTimestamp(1, now);
					free.setInt(2, disponibiliteId);
					free.executeUpdate();
				}
			}

			notifyByAppointment(connection, appointmentId, patientFlow ? "Rendez-vous annule" : "Rendez-vous mis a jour");
			connection.commit();
			return ActionResult.success("Rendez-vous annule.", appointmentId);
		} catch (SQLException exception) {
			return ActionResult.failure("Annulation impossible: " + exception.getMessage());
		}
	}

	private ActionResult moveAppointmentToSlot(
			Connection connection,
			int appointmentId,
			Integer oldDisponibiliteId,
			int newDisponibiliteId,
			LocalDate date,
			LocalTime time,
			String status
	) throws SQLException {
		String lockNewSlotSql = "SELECT statut FROM disponibilites WHERE id = ? FOR UPDATE";
		String reserveSlotSql = "UPDATE disponibilites SET statut = 'reserve', updated_at = ? WHERE id = ?";
		String freeSlotSql = "UPDATE disponibilites SET statut = 'disponible', updated_at = ? WHERE id = ?";
		String updateRdvSql = "UPDATE rendez_vous SET disponibilite_id = ?, date_rdv = ?, heure_rdv = ?, statut = ?, updated_at = ? WHERE id = ?";

		try (PreparedStatement lockNew = connection.prepareStatement(lockNewSlotSql)) {
			lockNew.setInt(1, newDisponibiliteId);
			try (ResultSet rs = lockNew.executeQuery()) {
				if (!rs.next()) {
					return ActionResult.failure("Nouveau creneau introuvable.");
				}
				String slotStatus = safe(rs.getString("statut"));
				if (!"disponible".equalsIgnoreCase(slotStatus)) {
					return ActionResult.failure("Ce creneau est deja reserve.");
				}
			}
		}

		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		if (oldDisponibiliteId != null && !Objects.equals(oldDisponibiliteId, newDisponibiliteId)) {
			try (PreparedStatement freeOld = connection.prepareStatement(freeSlotSql)) {
				freeOld.setTimestamp(1, now);
				freeOld.setInt(2, oldDisponibiliteId);
				freeOld.executeUpdate();
			}
		}

		try (PreparedStatement reserveNew = connection.prepareStatement(reserveSlotSql)) {
			reserveNew.setTimestamp(1, now);
			reserveNew.setInt(2, newDisponibiliteId);
			reserveNew.executeUpdate();
		}

		try (PreparedStatement updateRdv = connection.prepareStatement(updateRdvSql)) {
			updateRdv.setInt(1, newDisponibiliteId);
			updateRdv.setDate(2, Date.valueOf(date));
			updateRdv.setTime(3, Time.valueOf(time));
			updateRdv.setString(4, status);
			updateRdv.setTimestamp(5, now);
			updateRdv.setInt(6, appointmentId);
			updateRdv.executeUpdate();
		}

		return ActionResult.success("Rendez-vous mis a jour.", appointmentId);
	}

	private void ensureDefaultDisponibilites(int medecinId, LocalDate date) {
		String countSql = "SELECT COUNT(*) AS total FROM disponibilites WHERE medecin_id = ? AND date = ?";
		String insertSql = "INSERT INTO disponibilites (medecin_id, date, heure_debut, heure_fin, statut, created_at, updated_at) "
				+ "VALUES (?, ?, ?, ?, 'disponible', ?, ?)";

		try (Connection connection = databaseService.getConnection()) {
			int total = 0;
			try (PreparedStatement count = connection.prepareStatement(countSql)) {
				count.setInt(1, medecinId);
				count.setDate(2, Date.valueOf(date));
				try (ResultSet rs = count.executeQuery()) {
					if (rs.next()) {
						total = rs.getInt("total");
					}
				}
			}
			if (total > 0) {
				return;
			}

			LocalTime start = LocalTime.of(9, 0);
			LocalTime end = LocalTime.of(17, 0);
			Timestamp now = Timestamp.valueOf(LocalDateTime.now());

			try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
				LocalTime cursor = start;
				while (cursor.isBefore(end)) {
					LocalTime slotEnd = cursor.plusMinutes(30);
					insert.setInt(1, medecinId);
					insert.setDate(2, Date.valueOf(date));
					insert.setTime(3, Time.valueOf(cursor));
					insert.setTime(4, Time.valueOf(slotEnd));
					insert.setTimestamp(5, now);
					insert.setTimestamp(6, now);
					insert.addBatch();
					cursor = slotEnd;
				}
				insert.executeBatch();
			}
		} catch (SQLException ignored) {
		}
	}

	private Integer findOrCreateDisponibilite(Connection connection, int medecinId, LocalDate date, LocalTime time) throws SQLException {
		String selectSql = "SELECT id FROM disponibilites WHERE medecin_id = ? AND date = ? AND heure_debut = ? LIMIT 1";
		try (PreparedStatement select = connection.prepareStatement(selectSql)) {
			select.setInt(1, medecinId);
			select.setDate(2, Date.valueOf(date));
			select.setTime(3, Time.valueOf(time));
			try (ResultSet rs = select.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("id");
				}
			}
		}

		String insertSql = "INSERT INTO disponibilites (medecin_id, date, heure_debut, heure_fin, statut, created_at, updated_at) "
				+ "VALUES (?, ?, ?, ?, 'disponible', ?, ?)";
		try (PreparedStatement insert = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
			Timestamp now = Timestamp.valueOf(LocalDateTime.now());
			insert.setInt(1, medecinId);
			insert.setDate(2, Date.valueOf(date));
			insert.setTime(3, Time.valueOf(time));
			insert.setTime(4, Time.valueOf(time.plusMinutes(30)));
			insert.setTimestamp(5, now);
			insert.setTimestamp(6, now);
			insert.executeUpdate();

			try (ResultSet keys = insert.getGeneratedKeys()) {
				if (keys.next()) {
					return keys.getInt(1);
				}
			}
		}
		return null;
	}

	private Integer findPatientIdByUser(Integer userId) {
		if (userId == null) {
			return null;
		}
		String sql = "SELECT id FROM patients WHERE user_id = ? LIMIT 1";
		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					return resultSet.getInt("id");
				}
			}
		} catch (SQLException ignored) {
		}
		return null;
	}

	private Integer ensurePatientForUser(Integer userId) {
		Integer existing = findPatientIdByUser(userId);
		if (existing != null) {
			return existing;
		}
		if (userId == null) {
			return null;
		}

		String sql = "INSERT INTO patients (user_id, created_at, updated_at) VALUES (?, ?, ?)";
		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			Timestamp now = Timestamp.valueOf(LocalDateTime.now());
			statement.setInt(1, userId);
			statement.setTimestamp(2, now);
			statement.setTimestamp(3, now);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (keys.next()) {
					return keys.getInt(1);
				}
			}
		} catch (SQLException ignored) {
		}
		return null;
	}

	private Integer findMedecinIdByUser(Integer userId) {
		if (userId == null) {
			return null;
		}
		String sql = "SELECT id FROM medecins WHERE user_id = ? LIMIT 1";
		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					return resultSet.getInt("id");
				}
			}
		} catch (SQLException ignored) {
		}
		return null;
	}

	private void notifyByAppointment(Connection connection, int appointmentId, String actionLabel) {
		Integer patientUserId = null;
		Integer medecinUserId = null;
		String sql = "SELECT pu.id AS patient_user_id, mu.id AS medecin_user_id "
				+ "FROM rendez_vous r "
				+ "INNER JOIN patients p ON p.id = r.patient_id "
				+ "INNER JOIN users pu ON pu.id = p.user_id "
				+ "INNER JOIN medecins m ON m.id = r.medecin_id "
				+ "INNER JOIN users mu ON mu.id = m.user_id "
				+ "WHERE r.id = ?";

		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, appointmentId);
			try (ResultSet rs = statement.executeQuery()) {
				if (rs.next()) {
					patientUserId = rs.getInt("patient_user_id");
					medecinUserId = rs.getInt("medecin_user_id");
				}
			}
		} catch (SQLException ignored) {
			return;
		}

		String title = actionLabel;
		String message = "Mise a jour de votre rendez-vous.";
		if (patientUserId != null) {
			insertNotification(connection, patientUserId, title, message);
		}
		if (medecinUserId != null) {
			insertNotification(connection, medecinUserId, title, message);
		}
	}

	private void insertNotification(Connection connection, int userId, String title, String message) {
		String sql = "INSERT INTO notifications (titre, message, type, priorite, lu, date_envoi, user_id) "
				+ "VALUES (?, ?, 'rdv', 'normal', 0, ?, ?)";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, title);
			statement.setString(2, message);
			statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
			statement.setInt(4, userId);
			statement.executeUpdate();
		} catch (SQLException ignored) {
		}
	}

	private void sendAppointmentRequestEmail(int appointmentId) {
		if (!emailService.isConfigured()) {
			return;
		}
		String sql = "SELECT r.date_rdv, r.heure_rdv, r.motif, "
				+ "mu.email AS doctor_email, mu.nom AS doctor_nom, mu.prenom AS doctor_prenom, "
				+ "pu.email AS patient_email, pu.nom AS patient_nom, pu.prenom AS patient_prenom, "
				+ "m.specialite AS specialite "
				+ "FROM rendez_vous r "
				+ "INNER JOIN medecins m ON m.id = r.medecin_id "
				+ "INNER JOIN users mu ON mu.id = m.user_id "
				+ "INNER JOIN patients p ON p.id = r.patient_id "
				+ "INNER JOIN users pu ON pu.id = p.user_id "
				+ "WHERE r.id = ?";
		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, appointmentId);
			try (ResultSet rs = statement.executeQuery()) {
				if (!rs.next()) {
					return;
				}
				String doctorEmail = safe(rs.getString("doctor_email"));
				String doctorName = humanName(rs.getString("doctor_nom"), rs.getString("doctor_prenom"), "Medecin");
				String patientName = humanName(rs.getString("patient_nom"), rs.getString("patient_prenom"), "Patient");
				String specialite = safe(rs.getString("specialite"));
				LocalDate date = toLocalDate(rs.getDate("date_rdv"));
				LocalTime time = toLocalTime(rs.getTime("heure_rdv"));
				String motif = safe(rs.getString("motif"));

				if (doctorEmail.isBlank()) {
					return;
				}

				String subject = "Nouvelle demande de rendez-vous";
				String html = buildAppointmentRequestHtml(doctorName, patientName, specialite, date, time, motif);
				String text = buildAppointmentRequestText(doctorName, patientName, specialite, date, time, motif);
				emailService.sendCustomEmail(doctorEmail, subject, html, text);
			}
		} catch (SQLException ignored) {
		}
	}

	private String buildAppointmentRequestHtml(
			String doctorName,
			String patientName,
			String specialite,
			LocalDate date,
			LocalTime time,
			String motif
	) {
		String dateText = date == null ? "" : DATE_FR.format(date);
		String timeText = time == null ? "" : TIME_FR.format(time);
		String specText = safe(specialite).isBlank() ? "Generaliste" : safe(specialite);
		String motifText = safe(motif).isBlank() ? "-" : safe(motif);

		return "<!doctype html><html><head><meta charset=\"utf-8\"></head><body "
				+ "style=\"margin:0;padding:0;background:#f6f9fc;font-family:Arial,sans-serif;color:#1f2937;\">"
				+ "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"padding:30px 0;\">"
				+ "<tr><td align=\"center\">"
				+ "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 12px 30px rgba(15,23,42,0.08);\">"
				+ "<tr><td style=\"background:linear-gradient(135deg,#0d8abc,#00c9a7);padding:24px;\">"
				+ "<h1 style=\"margin:0;color:#ffffff;font-size:22px;\">SANTEA</h1>"
				+ "<p style=\"margin:6px 0 0;color:#e0f2fe;font-size:14px;\">Nouvelle demande</p>"
				+ "</td></tr>"
				+ "<tr><td style=\"padding:24px 28px;\">"
				+ "<h2 style=\"margin:0 0 10px;font-size:18px;\">Bonjour Dr " + escapeHtml(doctorName) + "</h2>"
				+ "<p style=\"margin:0 0 14px;color:#4b5563;\">Une demande de rendez-vous a ete creee.</p>"
				+ "<div style=\"background:#f9fafb;border:1px solid #eef2f6;border-radius:12px;padding:16px;\">"
				+ "<p style=\"margin:0 0 6px;font-weight:bold;\">Details</p>"
				+ "<p style=\"margin:0;color:#6b7280;\">Patient: <strong>" + escapeHtml(patientName) + "</strong></p>"
				+ "<p style=\"margin:0;color:#6b7280;\">Specialite: <strong>" + escapeHtml(specText) + "</strong></p>"
				+ "<p style=\"margin:0;color:#6b7280;\">Date: <strong>" + escapeHtml(dateText + " " + timeText) + "</strong></p>"
				+ "<p style=\"margin:0;color:#6b7280;\">Motif: <strong>" + escapeHtml(motifText) + "</strong></p>"
				+ "</div>"
				+ "<p style=\"margin:16px 0 0;color:#6b7280;font-size:12px;\">Veuillez ouvrir SANTEA pour confirmer ou replanifier.</p>"
				+ "</td></tr>"
				+ "</table></td></tr></table></body></html>";
	}

	private String buildAppointmentRequestText(
			String doctorName,
			String patientName,
			String specialite,
			LocalDate date,
			LocalTime time,
			String motif
	) {
		String dateText = date == null ? "" : DATE_FR.format(date);
		String timeText = time == null ? "" : TIME_FR.format(time);
		String specText = safe(specialite).isBlank() ? "Generaliste" : safe(specialite);
		String motifText = safe(motif).isBlank() ? "-" : safe(motif);
		return "Nouvelle demande de rendez-vous\n\n"
				+ "Bonjour Dr " + safe(doctorName) + "\n"
				+ "Patient: " + safe(patientName) + "\n"
				+ "Specialite: " + specText + "\n"
				+ "Date: " + dateText + " " + timeText + "\n"
				+ "Motif: " + motifText + "\n\n"
				+ "Ouvrez SANTEA pour confirmer ou replanifier.";
	}

	private String escapeHtml(String value) {
		String text = safe(value);
		return text.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}

	private void ensureAppointmentTables() {
		// The JavaFX client can run before Symfony migrations; create minimal compatible tables.
		String[] ddl = new String[] {
				"CREATE TABLE IF NOT EXISTS patients ("
						+ "id INT AUTO_INCREMENT PRIMARY KEY,"
						+ "user_id INT NOT NULL,"
						+ "created_at DATETIME NULL,"
						+ "updated_at DATETIME NULL"
						+ ")",
				"CREATE TABLE IF NOT EXISTS medecins ("
						+ "id INT AUTO_INCREMENT PRIMARY KEY,"
						+ "user_id INT NOT NULL,"
						+ "specialite VARCHAR(120) NULL,"
						+ "cabinet_ville VARCHAR(120) NULL,"
						+ "cabinet_lat DECIMAL(10,6) NULL,"
						+ "cabinet_lng DECIMAL(10,6) NULL"
						+ ")",
				"CREATE TABLE IF NOT EXISTS disponibilites ("
						+ "id INT AUTO_INCREMENT PRIMARY KEY,"
						+ "medecin_id INT NOT NULL,"
						+ "date DATE NOT NULL,"
						+ "heure_debut TIME NOT NULL,"
						+ "heure_fin TIME NOT NULL,"
						+ "statut VARCHAR(20) NOT NULL DEFAULT 'disponible',"
						+ "created_at DATETIME NULL,"
						+ "updated_at DATETIME NULL"
						+ ")",
				"CREATE TABLE IF NOT EXISTS rendez_vous ("
						+ "id INT AUTO_INCREMENT PRIMARY KEY,"
						+ "patient_id INT NOT NULL,"
						+ "medecin_id INT NOT NULL,"
						+ "disponibilite_id INT NULL,"
						+ "date_rdv DATE NOT NULL,"
						+ "heure_rdv TIME NOT NULL,"
						+ "motif TEXT NULL,"
						+ "statut VARCHAR(20) NOT NULL DEFAULT 'en_attente',"
						+ "created_at DATETIME NULL,"
						+ "updated_at DATETIME NULL"
						+ ")"
		};

		for (String sql : ddl) {
			try (Connection connection = databaseService.getConnection();
				 PreparedStatement statement = connection.prepareStatement(sql)) {
				statement.execute();
			} catch (SQLException ignored) {
			}
		}
	}

	private String effectiveRole(User user) {
		String type = safe(user == null ? null : user.getSubscriptionType()).toUpperCase(Locale.ROOT);
		if (!type.isBlank()) {
			return type;
		}
		return safe(user == null ? null : user.getRole()).toUpperCase(Locale.ROOT);
	}

	private String humanName(String nom, String prenom, String fallback) {
		String full = (safe(nom) + " " + safe(prenom)).trim();
		return full.isBlank() ? fallback : full;
	}

	private String normalizeVoiceCommand(String raw) {
		return normalizeVoiceText(raw);
	}

	private String normalizeVoiceText(String text) {
		String normalized = convertArabicDigits(safe(text));
		normalized = normalizeArabicLetters(normalized);
		normalized = normalized.replace("اليومه", "اليوم").replace("اليومة", "اليوم");
		normalized = normalized.replace("توة", "توا");
		normalized = normalized.replace("غدوة", "غدوه");
		normalized = normalized.replace("غدوى", "غدوه");
		normalized = normalized.replace("غدوا", "غدوه");
		normalized = normalized.replace("دكتورة", "دكتور").replace("دكتوره", "دكتور");
		normalized = convertArabicNumberWords(normalized);
		normalized = normalized.replaceAll("\\s+", " ").trim();
		return normalized.toLowerCase(Locale.ROOT);
	}

	private String convertArabicNumberWords(String value) {
		String text = safe(value);
		if (text.isBlank()) {
			return text;
		}
		String normalized = " " + text + " ";
		normalized = normalized.replaceAll("(?iu)\\bو(عشرين|ثلاثين|اربعين|خمسين|ستين|سبعين|ثمانين|تسعين)\\b", "و $1");
		normalized = replaceToken(normalized, "صفر", "0");
		normalized = replaceToken(normalized, "واحد", "1");
		normalized = replaceToken(normalized, "وحد", "1");
		normalized = replaceToken(normalized, "وحده", "1");
		normalized = replaceToken(normalized, "وحدو", "1");
		normalized = replaceToken(normalized, "اثنين", "2");
		normalized = replaceToken(normalized, "ثنين", "2");
		normalized = replaceToken(normalized, "زوز", "2");
		normalized = replaceToken(normalized, "زوج", "2");
		normalized = replaceToken(normalized, "ثلاثه", "3");
		normalized = replaceToken(normalized, "ثلاثة", "3");
		normalized = replaceToken(normalized, "تلاثه", "3");
		normalized = replaceToken(normalized, "تلاثة", "3");
		normalized = replaceToken(normalized, "اربعه", "4");
		normalized = replaceToken(normalized, "اربعة", "4");
		normalized = replaceToken(normalized, "ربعه", "4");
		normalized = replaceToken(normalized, "ربعة", "4");
		normalized = replaceToken(normalized, "خمسه", "5");
		normalized = replaceToken(normalized, "خمسة", "5");
		normalized = replaceToken(normalized, "ستة", "6");
		normalized = replaceToken(normalized, "سته", "6");
		normalized = replaceToken(normalized, "سبعه", "7");
		normalized = replaceToken(normalized, "سبعة", "7");
		normalized = replaceToken(normalized, "ثمانيه", "8");
		normalized = replaceToken(normalized, "ثمانية", "8");
		normalized = replaceToken(normalized, "تسعه", "9");
		normalized = replaceToken(normalized, "تسعة", "9");
		normalized = replaceToken(normalized, "عشره", "10");
		normalized = replaceToken(normalized, "عشرة", "10");
		normalized = replaceToken(normalized, "حداش", "11");
		normalized = replaceToken(normalized, "احداش", "11");
		normalized = replaceToken(normalized, "اثناش", "12");
		normalized = replaceToken(normalized, "اثنعش", "12");
		normalized = replaceToken(normalized, "اثناعش", "12");
		normalized = replaceToken(normalized, "تلتاش", "13");
		normalized = replaceToken(normalized, "تلتعش", "13");
		normalized = replaceToken(normalized, "ثلاثتاش", "13");
		normalized = replaceToken(normalized, "ثلاثطاش", "13");
		normalized = replaceToken(normalized, "اربعتاش", "14");
		normalized = replaceToken(normalized, "ربعتاش", "14");
		normalized = replaceToken(normalized, "خمستاش", "15");
		normalized = replaceToken(normalized, "خمستعش", "15");
		normalized = replaceToken(normalized, "ستاش", "16");
		normalized = replaceToken(normalized, "ستعش", "16");
		normalized = replaceToken(normalized, "سبعتاش", "17");
		normalized = replaceToken(normalized, "سبعتعش", "17");
		normalized = replaceToken(normalized, "ثمانتاش", "18");
		normalized = replaceToken(normalized, "ثمانتعش", "18");
		normalized = replaceToken(normalized, "تسعتاش", "19");
		normalized = replaceToken(normalized, "تسعتعش", "19");
		normalized = replaceToken(normalized, "عشرين", "20");
		normalized = replaceToken(normalized, "ثلاثين", "30");
		normalized = replaceToken(normalized, "اربعين", "40");
		normalized = replaceToken(normalized, "خمسين", "50");
		normalized = replaceToken(normalized, "ستين", "60");
		normalized = replaceToken(normalized, "سبعين", "70");
		normalized = replaceToken(normalized, "ثمانين", "80");
		normalized = replaceToken(normalized, "تسعين", "90");
		normalized = combineArabicNumberTokens(normalized);
		return normalized;
	}

	private String replaceToken(String text, String token, String replacement) {
		String pattern = "(?iu)(^|\\s)" + java.util.regex.Pattern.quote(token) + "(?=\\s|$)";
		return text.replaceAll(pattern, "$1" + replacement);
	}

	private String combineArabicNumberTokens(String text) {
		String normalized = text;
		java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{1,2})\\s*و\\s*(\\d{1,2})\\b");
		boolean changed;
		do {
			changed = false;
			java.util.regex.Matcher matcher = pattern.matcher(normalized);
			StringBuffer buffer = new StringBuffer();
			while (matcher.find()) {
				int first = Integer.parseInt(matcher.group(1));
				int second = Integer.parseInt(matcher.group(2));
				Integer combined = combineArabicNumberPair(first, second);
				if (combined == null) {
					matcher.appendReplacement(buffer, matcher.group(0));
					continue;
				}
				matcher.appendReplacement(buffer, String.valueOf(combined));
				changed = true;
			}
			matcher.appendTail(buffer);
			normalized = buffer.toString();
		} while (changed);
		return normalized;
	}

	private Integer combineArabicNumberPair(int first, int second) {
		if (first % 10 == 0 && second >= 1 && second <= 9) {
			return first + second;
		}
		if (second % 10 == 0 && first >= 1 && first <= 9) {
			return second + first;
		}
		if (first == 10 && second >= 1 && second <= 9) {
			return first + second;
		}
		if (second == 10 && first >= 1 && first <= 9) {
			return second + first;
		}
		return null;
	}

	private String normalizeArabicLetters(String value) {
		String text = safe(value);
		text = text.replaceAll("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "");
		text = text.replaceAll("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]", "");
		text = text.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا');
		text = text.replace('ؤ', 'و').replace('ئ', 'ي');
		text = text.replace('ى', 'ي').replace('ة', 'ه');
		text = text.replace('ـ', ' ');
		return text;
	}

	private boolean containsKeyword(String text, String pattern) {
		if (text == null || text.isBlank()) {
			return false;
		}
		return java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.UNICODE_CASE).matcher(text).find();
	}

	private LocalDate extractDate(String value) {
		String text = normalizeVoiceText(value);
		LocalDate today = LocalDate.now();
		if (containsKeyword(text, "(?<!\\p{L})(lyoum|today|aujourd'hui|اليوم|توا|توه)(?!\\p{L})")) {
			return today;
		}
		if (containsKeyword(text, "(?<!\\p{L})(ghodwa|ghodwaa|demain|tomorrow|غدوه|غدا|بكرا|بكره)(?!\\p{L})")) {
			return today.plusDays(1);
		}
		if (containsKeyword(text, "(?<!\\p{L})(baad\\s*ghodwa|apres\\s*demain|after\\s*tomorrow|بعد\\s*غدوه|بعد\\s*بكرا|بعد\\s*بكره)(?!\\p{L})")) {
			return today.plusDays(2);
		}

		java.util.regex.Matcher fr = java.util.regex.Pattern
				.compile("\\b(\\d{1,2})[\\/\\-.](\\d{1,2})[\\/\\-.](\\d{4})\\b")
				.matcher(text);
		if (fr.find()) {
			int d = Integer.parseInt(fr.group(1));
			int m = Integer.parseInt(fr.group(2));
			int y = Integer.parseInt(fr.group(3));
			if (isValidDate(y, m, d)) {
				return LocalDate.of(y, m, d);
			}
		}

		java.util.regex.Matcher iso = java.util.regex.Pattern
				.compile("\\b(\\d{4})[\\/\\-.](\\d{1,2})[\\/\\-.](\\d{1,2})\\b")
				.matcher(text);
		if (iso.find()) {
			int y = Integer.parseInt(iso.group(1));
			int m = Integer.parseInt(iso.group(2));
			int d = Integer.parseInt(iso.group(3));
			if (isValidDate(y, m, d)) {
				return LocalDate.of(y, m, d);
			}
		}

		java.util.regex.Matcher shortMatcher = java.util.regex.Pattern
				.compile("\\b(\\d{1,2})[\\/\\-.](\\d{1,2})\\b")
				.matcher(text);
		if (shortMatcher.find()) {
			int d = Integer.parseInt(shortMatcher.group(1));
			int m = Integer.parseInt(shortMatcher.group(2));
			int y = today.getYear();
			if (isValidDate(y, m, d)) {
				LocalDate candidate = LocalDate.of(y, m, d);
				if (candidate.isBefore(today)) {
					int nextYear = y + 1;
					if (isValidDate(nextYear, m, d)) {
						return LocalDate.of(nextYear, m, d);
					}
				}
				return candidate;
			}
		}

		java.util.Map<String, String> ordinals = new java.util.LinkedHashMap<>();
		ordinals.put("الحادي\\s+والثلاثين", "31");
		ordinals.put("الثلاثين", "30");
		ordinals.put("الثلاثون", "30");
		ordinals.put("التاسع\\s+والعشرين", "29");
		ordinals.put("الثامن\\s+والعشرين", "28");
		ordinals.put("السابع\\s+والعشرين", "27");
		ordinals.put("السادس\\s+والعشرين", "26");
		ordinals.put("الخامس\\s+والعشرين", "25");
		ordinals.put("الرابع\\s+والعشرين", "24");
		ordinals.put("الثالث\\s+والعشرين", "23");
		ordinals.put("الثاني\\s+والعشرين", "22");
		ordinals.put("الحادي\\s+والعشرين", "21");
		ordinals.put("العشرين", "20");
		ordinals.put("العشرون", "20");
		ordinals.put("التاسع\\s+عشر", "19");
		ordinals.put("الثامن\\s+عشر", "18");
		ordinals.put("السابع\\s+عشر", "17");
		ordinals.put("السادس\\s+عشر", "16");
		ordinals.put("الخامس\\s+عشر", "15");
		ordinals.put("الرابع\\s+عشر", "14");
		ordinals.put("الثالث\\s+عشر", "13");
		ordinals.put("الثاني\\s+عشر", "12");
		ordinals.put("الحادي\\s+عشر", "11");
		ordinals.put("العاشر", "10");
		ordinals.put("التاسع", "9");
		ordinals.put("الثامن", "8");
		ordinals.put("السابع", "7");
		ordinals.put("السادس", "6");
		ordinals.put("الخامس", "5");
		ordinals.put("الرابع", "4");
		ordinals.put("الثالث", "3");
		ordinals.put("الثاني", "2");
		ordinals.put("الأول", "1");
		ordinals.put("الاول", "1");
		for (java.util.Map.Entry<String, String> entry : ordinals.entrySet()) {
			text = text.replaceAll("(?iu)\\b" + entry.getKey() + "\\b", entry.getValue());
		}

		java.util.Map<String, Integer> monthNames = new java.util.HashMap<>();
		monthNames.put("janvier", 1); monthNames.put("janv", 1); monthNames.put("january", 1); monthNames.put("jan", 1);
		monthNames.put("جانفي", 1); monthNames.put("جانف", 1);
		monthNames.put("fevrier", 2); monthNames.put("février", 2); monthNames.put("fev", 2); monthNames.put("fév", 2);
		monthNames.put("february", 2); monthNames.put("feb", 2);
		monthNames.put("fivri", 2); monthNames.put("fivry", 2); monthNames.put("fevri", 2); monthNames.put("fivrih", 2);
		monthNames.put("fevriy", 2); monthNames.put("fivriy", 2); monthNames.put("fefri", 2); monthNames.put("febre", 2);
		monthNames.put("فيفري", 2); monthNames.put("فيفرى", 2); monthNames.put("فيفريه", 2);
		monthNames.put("فيفي", 2); monthNames.put("فيفا", 2); monthNames.put("فيفر", 2); monthNames.put("فيف", 2);
		monthNames.put("mars", 3); monthNames.put("march", 3); monthNames.put("mar", 3);
		monthNames.put("مارس", 3); monthNames.put("مارص", 3);
		monthNames.put("avril", 4); monthNames.put("april", 4); monthNames.put("avr", 4);
		monthNames.put("افريل", 4); monthNames.put("أفريل", 4); monthNames.put("افريلا", 4);
		monthNames.put("mai", 5); monthNames.put("may", 5);
		monthNames.put("ماي", 5); monthNames.put("ماييو", 5);
		monthNames.put("juin", 6); monthNames.put("june", 6); monthNames.put("jun", 6);
		monthNames.put("جوان", 6); monthNames.put("جون", 6);
		monthNames.put("juillet", 7); monthNames.put("july", 7); monthNames.put("juil", 7);
		monthNames.put("جويلية", 7); monthNames.put("جوليه", 7); monthNames.put("جويليه", 7); monthNames.put("جويلي", 7);
		monthNames.put("aout", 8); monthNames.put("août", 8); monthNames.put("august", 8); monthNames.put("aug", 8);
		monthNames.put("اوت", 8); monthNames.put("أوت", 8);
		monthNames.put("septembre", 9); monthNames.put("september", 9); monthNames.put("sep", 9); monthNames.put("sept", 9);
		monthNames.put("سبتمبر", 9); monthNames.put("سبتمبار", 9);
		monthNames.put("octobre", 10); monthNames.put("october", 10); monthNames.put("oct", 10);
		monthNames.put("اكتوبر", 10); monthNames.put("أكتوبر", 10);
		monthNames.put("novembre", 11); monthNames.put("november", 11); monthNames.put("nov", 11);
		monthNames.put("نوفمبر", 11); monthNames.put("نوفمبار", 11);
		monthNames.put("decembre", 12); monthNames.put("décembre", 12); monthNames.put("december", 12);
		monthNames.put("dec", 12); monthNames.put("déc", 12);
		monthNames.put("ديسمبر", 12); monthNames.put("ديسمبار", 12);

		java.util.regex.Matcher monthFirst = java.util.regex.Pattern
				.compile("(?:^|\\s|[\\p{Punct}،؛])(\\d{1,2})\\s+([\\p{L}]+)(?:\\s+(\\d{4}))?(?=\\s|$|[\\p{Punct}،؛])", java.util.regex.Pattern.UNICODE_CASE)
				.matcher(text);
		if (monthFirst.find()) {
			int day = Integer.parseInt(monthFirst.group(1));
			String rawLabel = normalizeVoiceText(monthFirst.group(2));
			String simpleLabel = simplifyForMatch(monthFirst.group(2));
			int year = monthFirst.group(3) != null ? Integer.parseInt(monthFirst.group(3)) : today.getYear();
			Integer month = resolveMonthFromWord(monthNames, rawLabel, simpleLabel);
			if (month != null && isValidDate(year, month, day)) {
				return LocalDate.of(year, month, day);
			}
		}

		java.util.regex.Matcher monthSecond = java.util.regex.Pattern
				.compile("(?:^|\\s|[\\p{Punct}،؛])([\\p{L}]+)\\s+(\\d{1,2})(?:\\s+(\\d{4}))?(?=\\s|$|[\\p{Punct}،؛])", java.util.regex.Pattern.UNICODE_CASE)
				.matcher(text);
		if (monthSecond.find()) {
			String rawLabel = normalizeVoiceText(monthSecond.group(1));
			String simpleLabel = simplifyForMatch(monthSecond.group(1));
			int day = Integer.parseInt(monthSecond.group(2));
			int year = monthSecond.group(3) != null ? Integer.parseInt(monthSecond.group(3)) : today.getYear();
			Integer month = resolveMonthFromWord(monthNames, rawLabel, simpleLabel);
			if (month != null && isValidDate(year, month, day)) {
				return LocalDate.of(year, month, day);
			}
		}

		return null;
	}

	private LocalTime extractTime(String value, LocalDate detectedDate) {
		String text = normalizeVoiceText(value);
		Integer hour = null;
		int minute = 0;
		java.util.regex.Matcher noon = java.util.regex.Pattern
				.compile("(?<!\\p{L})(نص\\s*النهار\\s*(?:و\\s*نص|ونص)?)(?!\\p{L})", java.util.regex.Pattern.UNICODE_CASE)
				.matcher(text);
		if (noon.find()) {
			return noon.group(1).contains("ونص") || noon.group(1).contains("و نص") ? LocalTime.of(12, 30) : LocalTime.of(12, 0);
		}
		java.util.regex.Matcher midnight = java.util.regex.Pattern
				.compile("(?<!\\p{L})(نص\\s*الليل\\s*(?:و\\s*نص|ونص)?)(?!\\p{L})", java.util.regex.Pattern.UNICODE_CASE)
				.matcher(text);
		if (midnight.find()) {
			return midnight.group(1).contains("ونص") || midnight.group(1).contains("و نص") ? LocalTime.of(0, 30) : LocalTime.of(0, 0);
		}
		java.util.regex.Matcher h24 = java.util.regex.Pattern
				.compile("\\b(\\d{1,2})\\s*[:h]\\s*(\\d{2})\\b", java.util.regex.Pattern.UNICODE_CASE)
				.matcher(text);
		if (h24.find()) {
			hour = Integer.parseInt(h24.group(1));
			minute = Integer.parseInt(h24.group(2));
		} else {
			java.util.regex.Matcher h12 = java.util.regex.Pattern
					.compile("\\b(\\d{1,2})\\s*[:h]\\s*(\\d{2})\\s*(am|pm|matin|morning|soir|apres\\s*midi|after\\s*noon|صباح|صباحا|مساء|العشيه|العشية)(?=\\s|$|[\\p{Punct}،؛])", java.util.regex.Pattern.UNICODE_CASE)
					.matcher(text);
			if (h12.find()) {
				hour = Integer.parseInt(h12.group(1));
				minute = Integer.parseInt(h12.group(2));
			} else {
				java.util.regex.Matcher shortHour = java.util.regex.Pattern
						.compile("\\b(\\d{1,2})\\s*(am|pm|matin|morning|soir|apres\\s*midi|after\\s*noon|صباح|صباحا|مساء|العشيه|العشية)(?=\\s|$|[\\p{Punct}،؛])", java.util.regex.Pattern.UNICODE_CASE)
						.matcher(text);
				if (shortHour.find()) {
					hour = Integer.parseInt(shortHour.group(1));
					minute = 0;
				} else {
					java.util.regex.Matcher half = java.util.regex.Pattern
							.compile("\\b(\\d{1,2})\\s*(?:و\\s*نص|ونص|et\\s*demi|half)(?=\\s|$|[\\p{Punct}،؛])", java.util.regex.Pattern.UNICODE_CASE)
							.matcher(text);
					if (half.find()) {
						hour = Integer.parseInt(half.group(1));
						minute = 30;
					} else {
						java.util.regex.Matcher saidHour = java.util.regex.Pattern
								.compile("(?:^|\\s|[\\p{Punct}،؛])(?:heure|sa3a|saa|clock|الساعة|الساعه)\\s*(\\d{1,2})(?::(\\d{2}))?(?=\\s|$|[\\p{Punct}،؛])", java.util.regex.Pattern.UNICODE_CASE)
								.matcher(text);
						if (saidHour.find()) {
							hour = Integer.parseInt(saidHour.group(1));
							minute = saidHour.group(2) != null ? Integer.parseInt(saidHour.group(2)) : 0;
						} else if (detectedDate != null) {
							java.util.regex.Matcher mix = java.util.regex.Pattern
									.compile("\\b(\\d{1,2})\\s+(\\d{1,2})\\s+([\\p{L}]+)\\b", java.util.regex.Pattern.UNICODE_CASE)
									.matcher(text);
							if (mix.find()) {
								int candidateHour = Integer.parseInt(mix.group(1));
								int candidateDay = Integer.parseInt(mix.group(2));
								if (candidateDay == detectedDate.getDayOfMonth()) {
									hour = candidateHour;
									minute = 0;
								}
							}
						}
					}
				}
			}
		}
		if (hour == null) {
			return null;
		}
		if (hour > 24 || minute > 59) {
			return null;
		}
		boolean isPm = text.matches(".*\\b(pm|soir|apres\\s*midi|after\\s*noon|مساء|العشيه|العشية|ليل|بالليل)\\b.*");
		boolean isAm = text.matches(".*\\b(am|matin|morning|صباح|صباحا)\\b.*");
		if (isPm && hour >= 1 && hour <= 11) {
			hour += 12;
		}
		if (isAm && hour == 12) {
			hour = 0;
		}
		if (hour == 24) {
			hour = 0;
		}
		if (hour < 0 || hour > 23) {
			return null;
		}
		return LocalTime.of(hour, minute);
	}

	private String extractDoctorHint(String value) {
		String hint = extractDoctorNameFromVoiceText(value);
		return hint == null ? "" : hint;
	}

	private String extractSpecialty(String value) {
		String text = safe(value);
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("(?i)(?:specialite|sp[eé]cialit[eé]|تخصص|اختصاص)\\s*[:\\-]?\\s*([^\\d,.;]+)")
				.matcher(text);
		if (matcher.find()) {
			return safe(matcher.group(1));
		}
		return "";
	}

	private String extractDoctorNameFromVoiceText(String text) {
		String normalized = normalizeVoiceText(text);
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("(?:docteur|doctor|dr\\.?|medecin|médecin|الدكتور|دكتور|طبيب)\\s+([^\\d,،.;]+)", java.util.regex.Pattern.UNICODE_CASE)
				.matcher(normalized);
		if (!matcher.find()) {
			java.util.regex.Matcher alt = java.util.regex.Pattern
					.compile("(?:avec|ma3|m3a|مع)\\s+([^\\d,،.;]+)", java.util.regex.Pattern.UNICODE_CASE)
					.matcher(normalized);
			if (!alt.find()) {
				return null;
			}
			matcher = alt;
		}
		String value = safe(matcher.group(1));
		value = value.replaceAll("(?iu)\\b(le|la|el|fi|avec|ma3|m3a|مع|نهار|بتاريخ|date|nhar|fel|fil)\\b", " ");
		value = value.replaceAll("\\s+", " ").trim();
		return value.isBlank() ? null : value;
	}

	private DoctorRow matchDoctorFromVoiceText(String hint) {
		String needleRaw = normalizeVoiceText(hint);
		if (needleRaw.isBlank()) {
			return null;
		}
		String needleSimple = simplifyForMatch(needleRaw);
		String needleTransliterated = transliterateArabicToLatin(needleRaw);
		java.util.List<String> needleCandidates = new java.util.ArrayList<>();
		needleCandidates.add(needleRaw);
		if (!needleSimple.isBlank()) {
			needleCandidates.add(needleSimple);
		}
		if (!needleTransliterated.isBlank()) {
			needleCandidates.add(needleTransliterated);
		}
		needleCandidates.addAll(expandDoctorAliases(needleCandidates));
		needleCandidates = needleCandidates.stream().filter(v -> v != null && !v.isBlank()).distinct().toList();

		List<DoctorRow> doctors = findDoctors("", "");
		DoctorRow best = null;
		int bestScore = -1;

		for (DoctorRow doctor : doctors) {
			String fullName = (safe(doctor.nom()) + " " + safe(doctor.prenom())).trim();
			String haystackRaw = normalizeVoiceText((fullName + " " + safe(doctor.specialite())).trim());
			String haystackSimple = simplifyForMatch(haystackRaw);
			String haystackTransliterated = transliterateArabicToLatin(haystackRaw);
			int score = 0;
			for (String candidate : needleCandidates) {
				String candidateSimple = simplifyForMatch(candidate);
				if (candidate.equals(haystackRaw) || (!candidateSimple.isBlank() && candidateSimple.equals(haystackSimple))) {
					score += 120;
				}
				if (haystackRaw.contains(candidate)) {
					score += 70;
				}
				if (!candidateSimple.isBlank() && haystackSimple.contains(candidateSimple)) {
					score += 70;
				}
				if (!candidateSimple.isBlank() && haystackTransliterated.contains(candidateSimple)) {
					score += 55;
				}
			}

			String[] rawTokens = needleRaw.replaceAll("\\s+", " ").split(" ");
			for (String token : rawTokens) {
				if (token.length() < 2) {
					continue;
				}
				if (haystackRaw.contains(token)) {
					score += 10;
				}
			}

			java.util.List<String> simpleTokens = new java.util.ArrayList<>();
			for (String candidate : needleCandidates) {
				String candidateSimple = simplifyForMatch(candidate);
				if (candidateSimple.isBlank()) {
					continue;
				}
				for (String token : candidateSimple.replaceAll("\\s+", " ").split(" ")) {
					simpleTokens.add(token);
				}
			}
			simpleTokens = simpleTokens.stream().filter(t -> !t.isBlank()).distinct().toList();
			String[] haystackWords = haystackSimple.split(" ");
			for (String token : simpleTokens) {
				if (token.length() < 2) {
					continue;
				}
				if (haystackSimple.contains(token)) {
					score += 10;
					continue;
				}
				if (token.length() < 4) {
					continue;
				}
				for (String word : haystackWords) {
					if (word.length() < 4) {
						continue;
					}
					int distance = levenshteinDistance(token, word);
					if (distance <= 1) {
						score += 7;
						break;
					}
					if (distance == 2) {
						score += 4;
					}
				}
			}

			if (score > bestScore) {
				best = doctor;
				bestScore = score;
			}
		}

		return bestScore > 0 ? best : null;
	}

	private Integer resolveMonthFromWord(java.util.Map<String, Integer> monthNames, String rawLabel, String simpleLabel) {
		Integer exact = monthNames.get(rawLabel);
		if (exact != null) {
			return exact;
		}
		Integer simple = monthNames.get(simpleLabel);
		if (simple != null) {
			return simple;
		}
		Integer best = null;
		int bestDist = 99;
		for (java.util.Map.Entry<String, Integer> entry : monthNames.entrySet()) {
			String keySimple = simplifyForMatch(entry.getKey());
			if (keySimple.length() < 3) {
				continue;
			}
			int dist = levenshteinDistance(simpleLabel, keySimple);
			if (dist < bestDist && dist <= 2) {
				bestDist = dist;
				best = entry.getValue();
			}
		}
		return best;
	}

	private String simplifyForMatch(String value) {
		String normalized = safe(value).toLowerCase(Locale.ROOT);
		try {
			String converted = java.text.Normalizer.normalize(normalized, java.text.Normalizer.Form.NFD);
			normalized = converted.replaceAll("\\p{M}", "");
		} catch (RuntimeException ignored) {
		}
		normalized = normalized.replaceAll("[^a-z0-9\\s]", " ");
		normalized = normalized.replaceAll("\\s+", " ").trim();
		return normalized;
	}

	private int levenshteinDistance(String a, String b) {
		if (a.equals(b)) {
			return 0;
		}
		int[] costs = new int[b.length() + 1];
		for (int j = 0; j < costs.length; j++) {
			costs[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			costs[0] = i;
			int nw = i - 1;
			for (int j = 1; j <= b.length(); j++) {
				int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
				nw = costs[j];
				costs[j] = cj;
			}
		}
		return costs[b.length()];
	}

	private String transliterateArabicToLatin(String value) {
		java.util.Map<Character, String> map = new java.util.HashMap<>();
		map.put('ا', "a"); map.put('ب', "b"); map.put('ت', "t"); map.put('ث', "th"); map.put('ج', "j"); map.put('ح', "h");
		map.put('خ', "kh"); map.put('د', "d"); map.put('ذ', "dh"); map.put('ر', "r"); map.put('ز', "z"); map.put('س', "s");
		map.put('ش', "sh"); map.put('ص', "s"); map.put('ض', "d"); map.put('ط', "t"); map.put('ظ', "z"); map.put('ع', "a");
		map.put('غ', "gh"); map.put('ف', "f"); map.put('ق', "q"); map.put('ك', "k"); map.put('ل', "l"); map.put('م', "m");
		map.put('ن', "n"); map.put('ه', "h"); map.put('و', "w"); map.put('ي', "y"); map.put('ء', ""); map.put('ؤ', "w");
		map.put('ئ', "y"); map.put('ى', "a"); map.put('ة', "a"); map.put(' ', " ");
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			String mapped = map.get(c);
			result.append(mapped == null ? c : mapped);
		}
		return simplifyForMatch(result.toString());
	}

	private java.util.List<String> expandDoctorAliases(java.util.List<String> candidates) {
		java.util.List<String> aliases = new java.util.ArrayList<>();
		String joined = " " + String.join(" ", candidates) + " ";
		java.util.Map<String, java.util.List<String>> rules = new java.util.HashMap<>();
		rules.put(" hsam ", java.util.List.of("houssem", "hossam", "houssam"));
		rules.put(" hsamh ", java.util.List.of("houssem", "hossam", "houssam"));
		rules.put(" hossam ", java.util.List.of("houssem", "houssam"));
		rules.put(" housam ", java.util.List.of("houssem", "hossam"));
		rules.put(" doctor ", java.util.List.of("docteur"));
		rules.put(" docteur ", java.util.List.of("doctor"));
		for (java.util.Map.Entry<String, java.util.List<String>> entry : rules.entrySet()) {
			if (!joined.contains(entry.getKey())) {
				continue;
			}
			aliases.addAll(entry.getValue());
		}
		return aliases.stream().distinct().toList();
	}

	private SlotRow pickBestSlot(List<SlotRow> slots, LocalTime requested) {
		if (slots == null || slots.isEmpty()) {
			return null;
		}
		if (requested == null) {
			return slots.get(0);
		}
		return slots.stream()
				.min(Comparator.comparingInt(s -> Math.abs((int) java.time.Duration.between(requested, s.start()).toMinutes())))
				.orElse(slots.get(0));
	}

	private String formatSlot(LocalTime start, LocalTime end) {
		if (start == null || end == null) {
			return "";
		}
		return TIME_FR.format(start) + " - " + TIME_FR.format(end);
	}

	private LocalDate toLocalDate(Date value) {
		return value == null ? null : value.toLocalDate();
	}

	private LocalTime toLocalTime(Time value) {
		return value == null ? null : value.toLocalTime();
	}

	private Integer nullableInt(ResultSet resultSet, String column) throws SQLException {
		int value = resultSet.getInt(column);
		return resultSet.wasNull() ? null : value;
	}

	private Double nullableDouble(String raw) {
		if (safe(raw).isBlank()) {
			return null;
		}
		try {
			return Double.parseDouble(raw.trim());
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}

	private String emptyToNull(String value) {
		String normalized = safe(value);
		return normalized.isBlank() ? null : normalized;
	}

	private boolean isValidDate(int year, int month, int day) {
		try {
			LocalDate.of(year, month, day);
			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private String truncate(String value, int maxLen) {
		String normalized = safe(value);
		if (normalized.length() <= maxLen) {
			return normalized;
		}
		return normalized.substring(0, Math.max(0, maxLen));
	}

	private String firstNonBlank(String... values) {
		if (values == null) {
			return "";
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private String convertArabicDigits(String value) {
		String text = safe(value);
		StringBuilder builder = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			builder.append(switch (c) {
				case '٠' -> '0';
				case '١' -> '1';
				case '٢' -> '2';
				case '٣' -> '3';
				case '٤' -> '4';
				case '٥' -> '5';
				case '٦' -> '6';
				case '٧' -> '7';
				case '٨' -> '8';
				case '٩' -> '9';
				case '٫' -> '.';
				case '،' -> ',';
				default -> c;
			});
		}
		return builder.toString();
	}

	private static class VoiceIntentData {
		private String doctor;
		private String specialty;
		private LocalDate date;
		private LocalTime time;

		private VoiceIntentData(String normalized) {
		}
	}

	public enum AccessScope {
		PATIENT,
		MEDECIN,
		DENIED
	}

	public record DoctorRow(
			int id,
			String nom,
			String prenom,
			String specialite,
			String ville,
			Double lat,
			Double lng
	) {
		public String displayLabel() {
			String full = (safeName(nom) + " " + safeName(prenom)).trim();
			if (full.isBlank()) {
				full = "Medecin";
			}
			String spec = specialite == null || specialite.isBlank() ? "Generaliste" : specialite;
			return full + " · " + spec;
		}

		private static String safeName(String value) {
			return value == null ? "" : value.trim();
		}
	}

	public record SlotRow(
			int id,
			LocalTime start,
			LocalTime end,
			String label
	) {
	}

	public record PatientAppointmentRow(
			int id,
			String medecin,
			LocalDate date,
			LocalTime heure,
			String statut,
			String motif
	) {
		public String dateFr() {
			return date == null ? "" : DATE_FR.format(date);
		}

		public String heureFr() {
			return heure == null ? "" : TIME_FR.format(heure);
		}
	}

	public record DoctorAppointmentRow(
			int id,
			String patient,
			LocalDate date,
			LocalTime heure,
			String statut,
			String motif
	) {
		public String dateFr() {
			return date == null ? "" : DATE_FR.format(date);
		}

		public String heureFr() {
			return heure == null ? "" : TIME_FR.format(heure);
		}
	}

	public record ActionResult(boolean success, String message, int appointmentId) {
		public static ActionResult success(String message, int appointmentId) {
			return new ActionResult(true, message, appointmentId);
		}

		public static ActionResult failure(String message) {
			return new ActionResult(false, message, 0);
		}
	}

	public record VoiceBookingResult(
			boolean success,
			String message,
			int appointmentId,
			DoctorRow doctor,
			SlotRow slot,
			LocalDate date,
			LocalTime requestedTime
	) {
		public static VoiceBookingResult success(
				String message,
				int appointmentId,
				DoctorRow doctor,
				SlotRow slot,
				LocalDate date,
				LocalTime requestedTime
		) {
			return new VoiceBookingResult(true, message, appointmentId, doctor, slot, date, requestedTime);
		}

		public static VoiceBookingResult failure(String message) {
			return new VoiceBookingResult(false, message, 0, null, null, null, null);
		}

		public String summary() {
			if (!success || doctor == null || slot == null || date == null) {
				return message;
			}
			return message + " (" + doctor.displayLabel() + " - " + DATE_FR.format(date) + " " + slot.label() + ")";
		}
	}
}
