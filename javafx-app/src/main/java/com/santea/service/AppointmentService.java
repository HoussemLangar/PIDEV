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

	private final DatabaseService databaseService;

	public AppointmentService() {
		this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
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
			return VoiceBookingResult.failure("Abonnement patient requis.");
		}
		String command = safe(rawCommand);
		if (command.isBlank()) {
			return VoiceBookingResult.failure("Commande vocale vide.");
		}

		LocalDate date = extractDate(command);
		if (date == null) {
			return VoiceBookingResult.failure("Date non detectee. Exemple: 24/02/2026.");
		}

		String doctorHint = extractDoctorHint(command);
		List<DoctorRow> doctors = findDoctors("", doctorHint);
		if (doctors.isEmpty()) {
			return VoiceBookingResult.failure("Medecin non trouve.");
		}

		DoctorRow doctor = doctors.get(0);
		LocalTime requested = extractTime(command);

		List<SlotRow> slots = findAvailableSlots(doctor.id(), date);
		if (slots.isEmpty()) {
			return VoiceBookingResult.failure("Aucun creneau disponible a cette date.");
		}

		SlotRow selected = pickBestSlot(slots, requested);
		ActionResult booking = book(user, doctor.id(), selected.id(), "Commande vocale: " + truncate(command, 180));
		if (!booking.success()) {
			return VoiceBookingResult.failure(booking.message());
		}

		return VoiceBookingResult.success(
				booking.message(),
				booking.appointmentId(),
				doctor,
				selected,
				date,
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

	private LocalDate extractDate(String value) {
		String text = safe(value).toLowerCase(Locale.ROOT);
		LocalDate today = LocalDate.now();
		if (text.contains("aujourd") || text.contains("today") || text.contains("اليوم")) {
			return today;
		}
		if (text.contains("demain") || text.contains("tomorrow") || text.contains("غد")) {
			return today.plusDays(1);
		}
		if (text.contains("apres demain") || text.contains("بعد غد")) {
			return today.plusDays(2);
		}

		java.util.regex.Matcher fr = java.util.regex.Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b").matcher(text);
		if (fr.find()) {
			int d = Integer.parseInt(fr.group(1));
			int m = Integer.parseInt(fr.group(2));
			int y = Integer.parseInt(fr.group(3));
			if (isValidDate(y, m, d)) {
				return LocalDate.of(y, m, d);
			}
		}

		java.util.regex.Matcher iso = java.util.regex.Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b").matcher(text);
		if (iso.find()) {
			int y = Integer.parseInt(iso.group(1));
			int m = Integer.parseInt(iso.group(2));
			int d = Integer.parseInt(iso.group(3));
			if (isValidDate(y, m, d)) {
				return LocalDate.of(y, m, d);
			}
		}

		return null;
	}

	private LocalTime extractTime(String value) {
		String text = safe(value).toLowerCase(Locale.ROOT);
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(\\d{1,2})[:h](\\d{2})\\b").matcher(text);
		if (!matcher.find()) {
			return null;
		}
		int h = Integer.parseInt(matcher.group(1));
		int m = Integer.parseInt(matcher.group(2));
		if (h < 0 || h > 23 || m < 0 || m > 59) {
			return null;
		}
		return LocalTime.of(h, m);
	}

	private String extractDoctorHint(String value) {
		String text = safe(value);
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("(?i)(?:docteur|doctor|dr\\.?|medecin|médecin|دكتور|طبيب)\\s+([^\\d,.;]+)")
				.matcher(text);
		if (matcher.find()) {
			return safe(matcher.group(1));
		}
		return text;
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
