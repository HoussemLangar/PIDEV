package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PharmacyService {
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final DatabaseService databaseService;
	private final AuthorizationPolicyService authorizationPolicyService;

	public PharmacyService() {
		this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
		this.authorizationPolicyService = new AuthorizationPolicyService();
		ensureTables();
	}

	public boolean canConnect() {
		return databaseService.canConnect();
	}

	public String getLastConnectionError() {
		return databaseService.getLastConnectionError();
	}

	public AccessDecision canAccessModule(User user) {
		if (user == null || user.getId() == null) {
			return AccessDecision.deny("Session invalide.");
		}

		if (isBannedEffective(user)) {
			return AccessDecision.deny("Compte banni. Acces pharmacie refuse.");
		}

		if (can(user, Permission.VIEW)) {
			return AccessDecision.allow("Acces autorise.");
		}

		AuthorizationPolicyService.AccessDecision policyDecision = authorizationPolicyService.decisionForProtectedFeatures(user);
		if (!policyDecision.allowed()) {
			return AccessDecision.deny(policyDecision.message());
		}

		return AccessDecision.deny("Droits insuffisants pour le module pharmacie.");
	}

	public boolean can(User user, Permission permission) {
		if (user == null || user.getId() == null) {
			return false;
		}

		if (authorizationPolicyService.isAdmin(user)) {
			return true;
		}

		if (isBannedEffective(user)) {
			return false;
		}

		String role = authorizationPolicyService.effectiveRole(user);
		boolean active = authorizationPolicyService.isActive(user);

		return switch (permission) {
			case VIEW -> active;
			case RESERVE -> active && ("ROLE_PATIENT".equals(role) || "ROLE_MEDECIN".equals(role));
			case MANAGE, STOCK, ORDERS -> active && "ROLE_PHARMACIEN".equals(role);
		};
	}

	public ModuleData loadModuleData(User user, String pharmacyQuery, String medicamentQuery) {
		return loadModuleData(user, pharmacyQuery, medicamentQuery, null, null);
	}

	public ModuleData loadModuleData(User user, String pharmacyQuery, String medicamentQuery, Double userLat, Double userLng) {
		if (user == null || user.getId() == null) {
			return ModuleData.empty();
		}
		if (!canConnect()) {
			return ModuleData.empty();
		}

		expirePendingReservations();

		String normalizedPharmacyQuery = normalize(pharmacyQuery).toLowerCase(Locale.ROOT);
		String normalizedMedicamentQuery = normalize(medicamentQuery).toLowerCase(Locale.ROOT);

		List<PharmacyRow> publicPharmacies = can(user, Permission.VIEW)
				? findPublicPharmacies(normalizedPharmacyQuery, userLat, userLng)
				: List.of();

		Integer pharmacienId = findPharmacienIdByUser(user.getId());
		boolean admin = authorizationPolicyService.isAdmin(user);

		List<PharmacyRow> managedPharmacies = can(user, Permission.MANAGE)
				? findManagedPharmacies(pharmacienId, admin, normalizedPharmacyQuery)
				: List.of();

		List<MedicamentRow> medicaments = can(user, Permission.VIEW)
				? findMedicaments(normalizedMedicamentQuery)
				: List.of();

		List<StockRow> availableStocks = can(user, Permission.VIEW)
				? findAvailableStocks(normalizedPharmacyQuery, normalizedMedicamentQuery)
				: List.of();

		List<StockRow> managedStocks = can(user, Permission.STOCK)
				? findManagedStocks(pharmacienId, admin)
				: List.of();

		List<ReservationRow> myReservations = can(user, Permission.RESERVE)
				? findReservationsForPatient(user.getId())
				: List.of();

		List<ReservationRow> incomingReservations = can(user, Permission.ORDERS)
				? findReservationsForPharmacien(pharmacienId, admin)
				: List.of();

		return new ModuleData(
				publicPharmacies,
				managedPharmacies,
				medicaments,
				availableStocks,
				managedStocks,
				myReservations,
				incomingReservations
		);
	}

	public ActionResult createPharmacy(User user, PharmacyDraft draft) {
		if (!can(user, Permission.MANAGE)) {
			return ActionResult.failure("Acces refuse: gestion pharmacie reservee au pharmacien actif.");
		}
		if (draft == null) {
			return ActionResult.failure("Donnees invalides.");
		}

		String nom = normalize(draft.nom());
		String adresse = normalize(draft.adresse());
		if (nom.isBlank() || adresse.isBlank()) {
			return ActionResult.failure("Nom et adresse sont obligatoires.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		boolean admin = authorizationPolicyService.isAdmin(user);
		Integer pharmacienId = findPharmacienIdByUser(user.getId());
		if (!admin && pharmacienId == null) {
			return ActionResult.failure("Profil pharmacien introuvable.");
		}

		String sql = "INSERT INTO pharmacies "
				+ "(pharmacien_id, nom, adresse, telephone, email, horaires, latitude, longitude, is_active, created_at, updated_at) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			LocalDateTime now = LocalDateTime.now();
			if (pharmacienId == null) {
				statement.setNull(1, java.sql.Types.INTEGER);
			} else {
				statement.setInt(1, pharmacienId);
			}
			statement.setString(2, nom);
			statement.setString(3, adresse);
			statement.setString(4, nullIfBlank(draft.telephone()));
			statement.setString(5, nullIfBlank(draft.email()));
			statement.setString(6, nullIfBlank(draft.horaires()));
			statement.setString(7, nullIfBlank(draft.latitude()));
			statement.setString(8, nullIfBlank(draft.longitude()));
			statement.setBoolean(9, draft.active());
			statement.setTimestamp(10, Timestamp.valueOf(now));
			statement.setTimestamp(11, Timestamp.valueOf(now));
			statement.executeUpdate();

			int createdId = generatedId(statement);
			return ActionResult.success("Pharmacie creee avec succes.", createdId);
		} catch (SQLException exception) {
			return ActionResult.failure("Creation pharmacie impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult createMedicament(User user, MedicamentDraft draft) {
		if (!can(user, Permission.MANAGE)) {
			return ActionResult.failure("Acces refuse: gestion medicaments reservee au pharmacien actif.");
		}
		if (draft == null) {
			return ActionResult.failure("Donnees invalides.");
		}

		String nom = normalize(draft.nom());
		if (nom.isBlank()) {
			return ActionResult.failure("Le nom du medicament est obligatoire.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		String sql = "INSERT INTO medicaments "
				+ "(nom, type, description, forme, dosage, prix, stock, laboratoire, code_barre, created_at, updated_at) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			LocalDateTime now = LocalDateTime.now();
			statement.setString(1, nom);
			statement.setString(2, nullIfBlank(draft.type()));
			statement.setString(3, nullIfBlank(draft.description()));
			statement.setString(4, nullIfBlank(draft.forme()));
			statement.setString(5, nullIfBlank(draft.dosage()));
			statement.setString(6, nullIfBlank(draft.prix()));
			statement.setInt(7, Math.max(0, draft.stock()));
			statement.setString(8, nullIfBlank(draft.laboratoire()));
			statement.setString(9, nullIfBlank(draft.codeBarre()));
			statement.setTimestamp(10, Timestamp.valueOf(now));
			statement.setTimestamp(11, Timestamp.valueOf(now));
			statement.executeUpdate();

			int createdId = generatedId(statement);
			return ActionResult.success("Medicament cree avec succes.", createdId);
		} catch (SQLException exception) {
			return ActionResult.failure("Creation medicament impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult createStock(User user, StockDraft draft) {
		if (!can(user, Permission.STOCK)) {
			return ActionResult.failure("Acces refuse: gestion stock reservee au pharmacien actif.");
		}
		if (draft == null || draft.pharmacyId() <= 0 || draft.medicamentId() <= 0) {
			return ActionResult.failure("Pharmacie et medicament sont obligatoires.");
		}
		if (draft.quantite() < 0) {
			return ActionResult.failure("La quantite doit etre positive.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		boolean admin = authorizationPolicyService.isAdmin(user);
		Integer pharmacienId = findPharmacienIdByUser(user.getId());
		if (!admin && (pharmacienId == null || !pharmacyBelongsToPharmacien(draft.pharmacyId(), pharmacienId))) {
			return ActionResult.failure("Acces refuse: cette pharmacie ne vous appartient pas.");
		}

		String sql = "INSERT INTO stock_pharmacies "
				+ "(pharmacie_id, medicament_id, quantite, prix_vente, date_expiration, created_at, updated_at) "
				+ "VALUES (?, ?, ?, ?, NULL, ?, ?)";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			LocalDateTime now = LocalDateTime.now();
			statement.setInt(1, draft.pharmacyId());
			statement.setInt(2, draft.medicamentId());
			statement.setInt(3, draft.quantite());
			statement.setString(4, nullIfBlank(draft.prixVente()));
			statement.setTimestamp(5, Timestamp.valueOf(now));
			statement.setTimestamp(6, Timestamp.valueOf(now));
			statement.executeUpdate();

			int createdId = generatedId(statement);
			return ActionResult.success("Stock ajoute avec succes.", createdId);
		} catch (SQLException exception) {
			if (isUniqueConstraintViolation(exception)) {
				return ActionResult.failure("Cette ligne de stock existe deja pour la pharmacie et le medicament selectionnes.");
			}
			return ActionResult.failure("Creation stock impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult updatePharmacy(User user, int pharmacyId, PharmacyDraft draft) {
		if (!can(user, Permission.MANAGE)) {
			return ActionResult.failure("Acces refuse: gestion pharmacie reservee au pharmacien actif.");
		}
		if (pharmacyId <= 0 || draft == null) {
			return ActionResult.failure("Donnees invalides.");
		}

		String nom = normalize(draft.nom());
		String adresse = normalize(draft.adresse());
		if (nom.isBlank() || adresse.isBlank()) {
			return ActionResult.failure("Nom et adresse sont obligatoires.");
		}

		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		boolean admin = authorizationPolicyService.isAdmin(user);
		Integer pharmacienId = findPharmacienIdByUser(user.getId());
		if (!admin && (pharmacienId == null || !pharmacyBelongsToPharmacien(pharmacyId, pharmacienId))) {
			return ActionResult.failure("Acces refuse: cette pharmacie ne vous appartient pas.");
		}

		String sql = "UPDATE pharmacies "
				+ "SET nom = ?, adresse = ?, telephone = ?, email = ?, horaires = ?, latitude = ?, longitude = ?, is_active = ?, updated_at = ? "
				+ "WHERE id = ?";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, nom);
			statement.setString(2, adresse);
			statement.setString(3, nullIfBlank(draft.telephone()));
			statement.setString(4, nullIfBlank(draft.email()));
			statement.setString(5, nullIfBlank(draft.horaires()));
			statement.setString(6, nullIfBlank(draft.latitude()));
			statement.setString(7, nullIfBlank(draft.longitude()));
			statement.setBoolean(8, draft.active());
			statement.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
			statement.setInt(10, pharmacyId);
			int updated = statement.executeUpdate();
			if (updated == 0) {
				return ActionResult.failure("Pharmacie introuvable.");
			}
			return ActionResult.success("Pharmacie mise a jour.", pharmacyId);
		} catch (SQLException exception) {
			return ActionResult.failure("Mise a jour pharmacie impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult deletePharmacy(User user, int pharmacyId) {
		if (!can(user, Permission.MANAGE)) {
			return ActionResult.failure("Acces refuse.");
		}
		if (pharmacyId <= 0) {
			return ActionResult.failure("Pharmacie invalide.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		boolean admin = authorizationPolicyService.isAdmin(user);
		Integer pharmacienId = findPharmacienIdByUser(user.getId());
		if (!admin && (pharmacienId == null || !pharmacyBelongsToPharmacien(pharmacyId, pharmacienId))) {
			return ActionResult.failure("Acces refuse: cette pharmacie ne vous appartient pas.");
		}

		String sql = "DELETE FROM pharmacies WHERE id = ?";
		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, pharmacyId);
			int deleted = statement.executeUpdate();
			if (deleted == 0) {
				return ActionResult.failure("Pharmacie introuvable.");
			}
			return ActionResult.success("Pharmacie supprimee.", pharmacyId);
		} catch (SQLException exception) {
			return ActionResult.failure("Suppression pharmacie impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult updateMedicament(User user, int medicamentId, MedicamentDraft draft) {
		if (!can(user, Permission.MANAGE)) {
			return ActionResult.failure("Acces refuse.");
		}
		if (medicamentId <= 0 || draft == null) {
			return ActionResult.failure("Donnees invalides.");
		}

		String nom = normalize(draft.nom());
		if (nom.isBlank()) {
			return ActionResult.failure("Le nom du medicament est obligatoire.");
		}

		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		String sql = "UPDATE medicaments "
				+ "SET nom = ?, type = ?, description = ?, forme = ?, dosage = ?, prix = ?, laboratoire = ?, code_barre = ?, updated_at = ? "
				+ "WHERE id = ?";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, nom);
			statement.setString(2, nullIfBlank(draft.type()));
			statement.setString(3, nullIfBlank(draft.description()));
			statement.setString(4, nullIfBlank(draft.forme()));
			statement.setString(5, nullIfBlank(draft.dosage()));
			statement.setString(6, nullIfBlank(draft.prix()));
			statement.setString(7, nullIfBlank(draft.laboratoire()));
			statement.setString(8, nullIfBlank(draft.codeBarre()));
			statement.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
			statement.setInt(10, medicamentId);
			int updated = statement.executeUpdate();
			if (updated == 0) {
				return ActionResult.failure("Medicament introuvable.");
			}
			return ActionResult.success("Medicament mis a jour.", medicamentId);
		} catch (SQLException exception) {
			return ActionResult.failure("Mise a jour medicament impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult deleteMedicament(User user, int medicamentId) {
		if (!can(user, Permission.MANAGE)) {
			return ActionResult.failure("Acces refuse.");
		}
		if (medicamentId <= 0) {
			return ActionResult.failure("Medicament invalide.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		String sql = "DELETE FROM medicaments WHERE id = ?";
		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, medicamentId);
			int deleted = statement.executeUpdate();
			if (deleted == 0) {
				return ActionResult.failure("Medicament introuvable.");
			}
			return ActionResult.success("Medicament supprime.", medicamentId);
		} catch (SQLException exception) {
			return ActionResult.failure("Suppression medicament impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult deleteStock(User user, int stockId) {
		if (!can(user, Permission.STOCK)) {
			return ActionResult.failure("Acces refuse.");
		}
		if (stockId <= 0) {
			return ActionResult.failure("Stock invalide.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		boolean admin = authorizationPolicyService.isAdmin(user);
		Integer pharmacienId = findPharmacienIdByUser(user.getId());
		if (!admin && (pharmacienId == null || !stockBelongsToPharmacien(stockId, pharmacienId))) {
			return ActionResult.failure("Acces refuse: stock hors perimetre pharmacien.");
		}

		String sql = "DELETE FROM stock_pharmacies WHERE id = ?";
		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, stockId);
			int deleted = statement.executeUpdate();
			if (deleted == 0) {
				return ActionResult.failure("Stock introuvable.");
			}
			return ActionResult.success("Stock supprime.", stockId);
		} catch (SQLException exception) {
			return ActionResult.failure("Suppression stock impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult updateStock(User user, int stockId, int quantite, String prixVente) {
		if (!can(user, Permission.STOCK)) {
			return ActionResult.failure("Acces refuse.");
		}
		if (stockId <= 0 || quantite < 0) {
			return ActionResult.failure("Valeurs invalides.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		boolean admin = authorizationPolicyService.isAdmin(user);
		Integer pharmacienId = findPharmacienIdByUser(user.getId());
		if (!admin && (pharmacienId == null || !stockBelongsToPharmacien(stockId, pharmacienId))) {
			return ActionResult.failure("Acces refuse: stock hors perimetre pharmacien.");
		}

		String sql = "UPDATE stock_pharmacies SET quantite = ?, prix_vente = ?, updated_at = ? WHERE id = ?";
		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, quantite);
			statement.setString(2, nullIfBlank(prixVente));
			statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
			statement.setInt(4, stockId);
			int updated = statement.executeUpdate();
			if (updated == 0) {
				return ActionResult.failure("Stock introuvable.");
			}
			return ActionResult.success("Stock mis a jour.", stockId);
		} catch (SQLException exception) {
			return ActionResult.failure("Mise a jour stock impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult createReservation(User user, int stockId, int quantity) {
		if (!can(user, Permission.RESERVE)) {
			return ActionResult.failure("Acces refuse: reservation reservee au patient ou medecin actif.");
		}
		if (stockId <= 0) {
			return ActionResult.failure("Stock invalide.");
		}
		int qty = Math.max(1, quantity);
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		String lockStockSql = "SELECT s.id, s.quantite, s.prix_vente, s.pharmacie_id, s.medicament_id "
				+ "FROM stock_pharmacies s "
				+ "WHERE s.id = ? FOR UPDATE";
		String insertReservationSql = "INSERT INTO reservations_medicaments "
				+ "(patient_id, pharmacie_id, medicament_id, stock_id, quantite, prix_unitaire, prix_total, statut, created_at, updated_at, expires_at) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, 'en_attente', ?, ?, ?)";

		try (Connection connection = databaseService.getConnection()) {
			connection.setAutoCommit(false);

			int pharmacyId;
			int medicamentId;
			int stockQuantity;
			String prixVente;

			try (PreparedStatement lock = connection.prepareStatement(lockStockSql)) {
				lock.setInt(1, stockId);
				try (ResultSet resultSet = lock.executeQuery()) {
					if (!resultSet.next()) {
						connection.rollback();
						return ActionResult.failure("Stock introuvable.");
					}

					stockQuantity = resultSet.getInt("quantite");
					pharmacyId = resultSet.getInt("pharmacie_id");
					medicamentId = resultSet.getInt("medicament_id");
					prixVente = resultSet.getString("prix_vente");
				}
			}

			if (stockQuantity < qty) {
				connection.rollback();
				return ActionResult.failure("Stock insuffisant.");
			}

			BigDecimal unitPrice = toBigDecimal(prixVente);
			String totalPrice = unitPrice == null ? null : unitPrice.multiply(BigDecimal.valueOf(qty)).toPlainString();

			int reservationId;
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime expiresAt = now.plusHours(2);
			try (PreparedStatement insert = connection.prepareStatement(insertReservationSql, Statement.RETURN_GENERATED_KEYS)) {
				insert.setInt(1, user.getId());
				insert.setInt(2, pharmacyId);
				insert.setInt(3, medicamentId);
				insert.setInt(4, stockId);
				insert.setInt(5, qty);
				insert.setString(6, prixVente);
				insert.setString(7, totalPrice);
				insert.setTimestamp(8, Timestamp.valueOf(now));
				insert.setTimestamp(9, Timestamp.valueOf(now));
				insert.setTimestamp(10, Timestamp.valueOf(expiresAt));
				insert.executeUpdate();
				reservationId = generatedId(insert);
			}

			connection.commit();
			return ActionResult.success("Reservation enregistree avec statut en_attente.", reservationId);
		} catch (SQLException exception) {
			return ActionResult.failure("Creation reservation impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult cancelReservation(User user, int reservationId) {
		if (!can(user, Permission.RESERVE)) {
			return ActionResult.failure("Acces refuse.");
		}
		if (reservationId <= 0) {
			return ActionResult.failure("Reservation invalide.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		boolean admin = authorizationPolicyService.isAdmin(user);
		String selectSql = "SELECT patient_id, statut FROM reservations_medicaments WHERE id = ?";
		String updateSql = "UPDATE reservations_medicaments "
				+ "SET statut = 'annulee', cancelled_at = ?, updated_at = ? "
				+ "WHERE id = ?";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement select = connection.prepareStatement(selectSql)) {
			select.setInt(1, reservationId);
			try (ResultSet rs = select.executeQuery()) {
				if (!rs.next()) {
					return ActionResult.failure("Reservation introuvable.");
				}

				int patientId = rs.getInt("patient_id");
				String status = safe(rs.getString("statut"));
				if (!admin && patientId != user.getId()) {
					return ActionResult.failure("Acces refuse: reservation non possedee.");
				}
				if (!"en_attente".equalsIgnoreCase(status) && !"confirmee".equalsIgnoreCase(status)) {
					return ActionResult.failure("Statut non annulable: " + status + ".");
				}
			}

			try (PreparedStatement update = connection.prepareStatement(updateSql)) {
				Timestamp now = Timestamp.valueOf(LocalDateTime.now());
				update.setTimestamp(1, now);
				update.setTimestamp(2, now);
				update.setInt(3, reservationId);
				update.executeUpdate();
			}

			return ActionResult.success("Reservation annulee.", reservationId);
		} catch (SQLException exception) {
			return ActionResult.failure("Annulation impossible: " + safe(exception.getMessage()));
		}
	}

	public ActionResult confirmReservation(User user, int reservationId) {
		return changeReservationStatus(user, reservationId, true);
	}

	public ActionResult rejectReservation(User user, int reservationId) {
		return changeReservationStatus(user, reservationId, false);
	}

	public int expirePendingReservations() {
		if (!canConnect()) {
			return 0;
		}

		String sql = "UPDATE reservations_medicaments "
				+ "SET statut = 'expiree', updated_at = ? "
				+ "WHERE statut = 'en_attente' AND expires_at IS NOT NULL AND expires_at < ?";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			Timestamp now = Timestamp.valueOf(LocalDateTime.now());
			statement.setTimestamp(1, now);
			statement.setTimestamp(2, now);
			return statement.executeUpdate();
		} catch (SQLException exception) {
			return 0;
		}
	}

	private ActionResult changeReservationStatus(User user, int reservationId, boolean confirm) {
		if (!can(user, Permission.ORDERS)) {
			return ActionResult.failure("Acces refuse: traitement reservations reserve au pharmacien actif.");
		}
		if (reservationId <= 0) {
			return ActionResult.failure("Reservation invalide.");
		}
		if (!canConnect()) {
			return ActionResult.failure("Connexion base impossible: " + getLastConnectionError());
		}

		boolean admin = authorizationPolicyService.isAdmin(user);
		Integer pharmacienId = findPharmacienIdByUser(user.getId());

		String lockSql = "SELECT r.id, r.statut, r.quantite, r.stock_id, s.quantite AS stock_quantite, p.pharmacien_id "
				+ "FROM reservations_medicaments r "
				+ "INNER JOIN stock_pharmacies s ON s.id = r.stock_id "
				+ "INNER JOIN pharmacies p ON p.id = r.pharmacie_id "
				+ "WHERE r.id = ? FOR UPDATE";
		String updateReservationSql = confirm
				? "UPDATE reservations_medicaments SET statut = 'confirmee', confirmed_at = ?, updated_at = ? WHERE id = ?"
				: "UPDATE reservations_medicaments SET statut = 'refusee', rejected_at = ?, updated_at = ? WHERE id = ?";
		String updateStockSql = "UPDATE stock_pharmacies SET quantite = ?, updated_at = ? WHERE id = ?";

		try (Connection connection = databaseService.getConnection()) {
			connection.setAutoCommit(false);

			int stockId;
			int requestedQty;
			int stockQty;

			try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
				lock.setInt(1, reservationId);
				try (ResultSet rs = lock.executeQuery()) {
					if (!rs.next()) {
						connection.rollback();
						return ActionResult.failure("Reservation introuvable.");
					}

					int ownerPharmacienId = rs.getInt("pharmacien_id");
					if (!admin && (pharmacienId == null || ownerPharmacienId != pharmacienId)) {
						connection.rollback();
						return ActionResult.failure("Acces refuse: reservation hors perimetre pharmacien.");
					}

					String status = safe(rs.getString("statut"));
					if (!"en_attente".equalsIgnoreCase(status)) {
						connection.rollback();
						return ActionResult.failure("Seules les reservations en_attente sont modifiables.");
					}

					stockId = rs.getInt("stock_id");
					requestedQty = rs.getInt("quantite");
					stockQty = rs.getInt("stock_quantite");
				}
			}

			Timestamp now = Timestamp.valueOf(LocalDateTime.now());

			if (confirm) {
				if (stockQty < requestedQty) {
					connection.rollback();
					return ActionResult.failure("Stock insuffisant pour confirmer la reservation.");
				}

				try (PreparedStatement updateStock = connection.prepareStatement(updateStockSql)) {
					updateStock.setInt(1, stockQty - requestedQty);
					updateStock.setTimestamp(2, now);
					updateStock.setInt(3, stockId);
					updateStock.executeUpdate();
				}
			}

			try (PreparedStatement updateReservation = connection.prepareStatement(updateReservationSql)) {
				updateReservation.setTimestamp(1, now);
				updateReservation.setTimestamp(2, now);
				updateReservation.setInt(3, reservationId);
				updateReservation.executeUpdate();
			}

			connection.commit();
			String label = confirm ? "confirmee" : "refusee";
			return ActionResult.success("Reservation " + label + ".", reservationId);
		} catch (SQLException exception) {
			return ActionResult.failure("Operation impossible: " + safe(exception.getMessage()));
		}
	}

	private List<PharmacyRow> findPublicPharmacies(String query, Double userLat, Double userLng) {
		String sql = "SELECT p.id, p.nom, p.adresse, p.telephone, p.email, p.horaires, p.latitude, p.longitude, p.is_active, p.image_name, p.pharmacien_id "
				+ "FROM pharmacies p "
				+ "WHERE p.is_active = 1 "
				+ "AND (? = '' OR LOWER(COALESCE(p.nom, '')) LIKE ? OR LOWER(COALESCE(p.adresse, '')) LIKE ?) "
				+ "ORDER BY p.nom ASC";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			bindLikeTriplet(statement, query);
			try (ResultSet rs = statement.executeQuery()) {
				List<PharmacyRow> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(mapPharmacy(rs));
				}

				if (userLat != null && userLng != null) {
					rows.sort(Comparator.comparingDouble(row -> distanceOrMax(row, userLat, userLng)));
				}

				return rows;
			}
		} catch (SQLException exception) {
			return List.of();
		}
	}

	private double distanceOrMax(PharmacyRow row, double userLat, double userLng) {
		Double pharmacyLat = parseNullableDouble(row.latitude());
		Double pharmacyLng = parseNullableDouble(row.longitude());
		if (pharmacyLat == null || pharmacyLng == null) {
			return Double.MAX_VALUE;
		}
		return haversineKm(userLat, userLng, pharmacyLat, pharmacyLng);
	}

	private Double parseNullableDouble(String value) {
		String raw = safe(value);
		if (raw.isBlank()) {
			return null;
		}
		try {
			return Double.parseDouble(raw);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double sinLat = Math.sin(dLat / 2);
		double sinLng = Math.sin(dLng / 2);
		double a = sinLat * sinLat
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLng * sinLng;
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return 6371.0 * c;
	}

	private List<PharmacyRow> findManagedPharmacies(Integer pharmacienId, boolean admin, String query) {
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT p.id, p.nom, p.adresse, p.telephone, p.email, p.horaires, p.latitude, p.longitude, p.is_active, p.image_name, p.pharmacien_id ")
				.append("FROM pharmacies p ")
				.append("WHERE (? = '' OR LOWER(COALESCE(p.nom, '')) LIKE ? OR LOWER(COALESCE(p.adresse, '')) LIKE ?) ");

		if (!admin) {
			sql.append("AND p.pharmacien_id = ? ");
		}
		sql.append("ORDER BY p.updated_at DESC, p.id DESC");

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql.toString())) {
			bindLikeTriplet(statement, query);
			if (!admin) {
				statement.setInt(4, pharmacienId == null ? 0 : pharmacienId);
			}

			try (ResultSet rs = statement.executeQuery()) {
				List<PharmacyRow> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(mapPharmacy(rs));
				}
				return rows;
			}
		} catch (SQLException exception) {
			return List.of();
		}
	}

	private List<MedicamentRow> findMedicaments(String query) {
		String sql = "SELECT m.id, m.nom, m.type, m.description, m.forme, m.dosage, m.prix, m.stock, m.laboratoire, m.code_barre, m.image_name "
				+ "FROM medicaments m "
				+ "WHERE (? = '' OR LOWER(COALESCE(m.nom, '')) LIKE ? OR LOWER(COALESCE(m.description, '')) LIKE ? OR LOWER(COALESCE(m.code_barre, '')) LIKE ?) "
				+ "ORDER BY m.nom ASC";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			String normalized = normalize(query);
			String like = "%" + normalized + "%";
			statement.setString(1, normalized);
			statement.setString(2, like);
			statement.setString(3, like);
			statement.setString(4, like);

			try (ResultSet rs = statement.executeQuery()) {
				List<MedicamentRow> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(new MedicamentRow(
							rs.getInt("id"),
							safe(rs.getString("nom")),
							safe(rs.getString("type")),
							safe(rs.getString("description")),
							safe(rs.getString("forme")),
							safe(rs.getString("dosage")),
							safe(rs.getString("prix")),
							rs.getInt("stock"),
							safe(rs.getString("laboratoire")),
							safe(rs.getString("code_barre")),
							safe(rs.getString("image_name"))
					));
				}
				return rows;
			}
		} catch (SQLException exception) {
			return List.of();
		}
	}

	private List<StockRow> findAvailableStocks(String pharmacyQuery, String medicamentQuery) {
		String sql = "SELECT s.id, s.pharmacie_id, p.nom AS pharmacy_nom, p.adresse AS pharmacy_adresse, "
				+ "s.medicament_id, m.nom AS medicament_nom, m.type AS medicament_type, m.forme AS medicament_forme, m.dosage AS medicament_dosage, "
				+ "s.quantite, s.prix_vente "
				+ "FROM stock_pharmacies s "
				+ "INNER JOIN pharmacies p ON p.id = s.pharmacie_id "
				+ "INNER JOIN medicaments m ON m.id = s.medicament_id "
				+ "WHERE p.is_active = 1 AND s.quantite > 0 "
				+ "AND (? = '' OR LOWER(COALESCE(p.nom, '')) LIKE ? OR LOWER(COALESCE(p.adresse, '')) LIKE ?) "
				+ "AND (? = '' OR LOWER(COALESCE(m.nom, '')) LIKE ? OR LOWER(COALESCE(m.type, '')) LIKE ? OR LOWER(COALESCE(m.code_barre, '')) LIKE ?) "
				+ "ORDER BY p.nom ASC, m.nom ASC";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			bindLikeTriplet(statement, pharmacyQuery);

			String normalizedMed = normalize(medicamentQuery);
			String medLike = "%" + normalizedMed + "%";
			statement.setString(4, normalizedMed);
			statement.setString(5, medLike);
			statement.setString(6, medLike);
			statement.setString(7, medLike);

			try (ResultSet rs = statement.executeQuery()) {
				List<StockRow> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(mapStockRow(rs));
				}
				return rows;
			}
		} catch (SQLException exception) {
			return List.of();
		}
	}

	private List<StockRow> findManagedStocks(Integer pharmacienId, boolean admin) {
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT s.id, s.pharmacie_id, p.nom AS pharmacy_nom, p.adresse AS pharmacy_adresse, ")
				.append("s.medicament_id, m.nom AS medicament_nom, m.type AS medicament_type, m.forme AS medicament_forme, m.dosage AS medicament_dosage, ")
				.append("s.quantite, s.prix_vente ")
				.append("FROM stock_pharmacies s ")
				.append("INNER JOIN pharmacies p ON p.id = s.pharmacie_id ")
				.append("INNER JOIN medicaments m ON m.id = s.medicament_id ");

		if (!admin) {
			sql.append("WHERE p.pharmacien_id = ? ");
		}
		sql.append("ORDER BY s.updated_at DESC, s.id DESC");

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql.toString())) {
			if (!admin) {
				statement.setInt(1, pharmacienId == null ? 0 : pharmacienId);
			}

			try (ResultSet rs = statement.executeQuery()) {
				List<StockRow> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(mapStockRow(rs));
				}
				return rows;
			}
		} catch (SQLException exception) {
			return List.of();
		}
	}

	private List<ReservationRow> findReservationsForPatient(int userId) {
		String sql = "SELECT r.id, r.patient_id, r.pharmacie_id, p.nom AS pharmacy_nom, p.adresse AS pharmacy_adresse, "
				+ "r.medicament_id, m.nom AS medicament_nom, r.quantite, r.prix_unitaire, r.prix_total, r.statut, "
				+ "r.created_at, r.expires_at, r.confirmed_at, r.cancelled_at, r.rejected_at, "
				+ "u.nom AS patient_nom, u.prenom AS patient_prenom "
				+ "FROM reservations_medicaments r "
				+ "INNER JOIN pharmacies p ON p.id = r.pharmacie_id "
				+ "INNER JOIN medicaments m ON m.id = r.medicament_id "
				+ "LEFT JOIN users u ON u.id = r.patient_id "
				+ "WHERE r.patient_id = ? "
				+ "ORDER BY r.created_at DESC";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, userId);
			try (ResultSet rs = statement.executeQuery()) {
				List<ReservationRow> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(mapReservationRow(rs));
				}
				return rows;
			}
		} catch (SQLException exception) {
			return List.of();
		}
	}

	private List<ReservationRow> findReservationsForPharmacien(Integer pharmacienId, boolean admin) {
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT r.id, r.patient_id, r.pharmacie_id, p.nom AS pharmacy_nom, p.adresse AS pharmacy_adresse, ")
				.append("r.medicament_id, m.nom AS medicament_nom, r.quantite, r.prix_unitaire, r.prix_total, r.statut, ")
				.append("r.created_at, r.expires_at, r.confirmed_at, r.cancelled_at, r.rejected_at, ")
				.append("u.nom AS patient_nom, u.prenom AS patient_prenom ")
				.append("FROM reservations_medicaments r ")
				.append("INNER JOIN pharmacies p ON p.id = r.pharmacie_id ")
				.append("INNER JOIN medicaments m ON m.id = r.medicament_id ")
				.append("LEFT JOIN users u ON u.id = r.patient_id ");

		if (!admin) {
			sql.append("WHERE p.pharmacien_id = ? ");
		}
		sql.append("ORDER BY r.created_at DESC");

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql.toString())) {
			if (!admin) {
				statement.setInt(1, pharmacienId == null ? 0 : pharmacienId);
			}

			try (ResultSet rs = statement.executeQuery()) {
				List<ReservationRow> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(mapReservationRow(rs));
				}
				return rows;
			}
		} catch (SQLException exception) {
			return List.of();
		}
	}

	private ReservationRow mapReservationRow(ResultSet rs) throws SQLException {
		String patientName = (safe(rs.getString("patient_nom")) + " " + safe(rs.getString("patient_prenom"))).trim();
		if (patientName.isBlank()) {
			patientName = "Patient";
		}

		return new ReservationRow(
				rs.getInt("id"),
				rs.getInt("patient_id"),
				patientName,
				rs.getInt("pharmacie_id"),
				safe(rs.getString("pharmacy_nom")),
				safe(rs.getString("pharmacy_adresse")),
				rs.getInt("medicament_id"),
				safe(rs.getString("medicament_nom")),
				rs.getInt("quantite"),
				safe(rs.getString("prix_unitaire")),
				safe(rs.getString("prix_total")),
				safe(rs.getString("statut")),
				toLocalDateTime(rs.getTimestamp("created_at")),
				toLocalDateTime(rs.getTimestamp("expires_at")),
				toLocalDateTime(rs.getTimestamp("confirmed_at")),
				toLocalDateTime(rs.getTimestamp("cancelled_at")),
				toLocalDateTime(rs.getTimestamp("rejected_at"))
		);
	}

	private StockRow mapStockRow(ResultSet rs) throws SQLException {
		return new StockRow(
				rs.getInt("id"),
				rs.getInt("pharmacie_id"),
				safe(rs.getString("pharmacy_nom")),
				safe(rs.getString("pharmacy_adresse")),
				rs.getInt("medicament_id"),
				safe(rs.getString("medicament_nom")),
				safe(rs.getString("medicament_type")),
				safe(rs.getString("medicament_forme")),
				safe(rs.getString("medicament_dosage")),
				rs.getInt("quantite"),
				safe(rs.getString("prix_vente"))
		);
	}

	private PharmacyRow mapPharmacy(ResultSet rs) throws SQLException {
		Integer pharmacienId = rs.getObject("pharmacien_id") == null ? null : rs.getInt("pharmacien_id");
		return new PharmacyRow(
				rs.getInt("id"),
				pharmacienId,
				safe(rs.getString("nom")),
				safe(rs.getString("adresse")),
				safe(rs.getString("telephone")),
				safe(rs.getString("email")),
				safe(rs.getString("horaires")),
				safe(rs.getString("latitude")),
				safe(rs.getString("longitude")),
				rs.getBoolean("is_active"),
				safe(rs.getString("image_name"))
		);
	}

	private Integer findPharmacienIdByUser(Integer userId) {
		if (userId == null || userId <= 0 || !canConnect()) {
			return null;
		}

		String sql = "SELECT id FROM pharmaciens WHERE user_id = ? LIMIT 1";
		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, userId);
			try (ResultSet rs = statement.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("id");
				}
				return null;
			}
		} catch (SQLException exception) {
			return null;
		}
	}

	private boolean pharmacyBelongsToPharmacien(int pharmacyId, int pharmacienId) {
		String sql = "SELECT COUNT(*) AS c FROM pharmacies WHERE id = ? AND pharmacien_id = ?";
		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, pharmacyId);
			statement.setInt(2, pharmacienId);
			try (ResultSet rs = statement.executeQuery()) {
				return rs.next() && rs.getInt("c") > 0;
			}
		} catch (SQLException exception) {
			return false;
		}
	}

	private boolean stockBelongsToPharmacien(int stockId, int pharmacienId) {
		String sql = "SELECT COUNT(*) AS c "
				+ "FROM stock_pharmacies s "
				+ "INNER JOIN pharmacies p ON p.id = s.pharmacie_id "
				+ "WHERE s.id = ? AND p.pharmacien_id = ?";

		try (Connection connection = databaseService.getConnection();
			 PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, stockId);
			statement.setInt(2, pharmacienId);
			try (ResultSet rs = statement.executeQuery()) {
				return rs.next() && rs.getInt("c") > 0;
			}
		} catch (SQLException exception) {
			return false;
		}
	}

	private void bindLikeTriplet(PreparedStatement statement, String query) throws SQLException {
		String normalized = normalize(query);
		String like = "%" + normalized + "%";
		statement.setString(1, normalized);
		statement.setString(2, like);
		statement.setString(3, like);
	}

	private int generatedId(PreparedStatement statement) throws SQLException {
		try (ResultSet keys = statement.getGeneratedKeys()) {
			if (keys.next()) {
				return keys.getInt(1);
			}
		}
		return 0;
	}

	private boolean isUniqueConstraintViolation(SQLException exception) {
		String state = safe(exception.getSQLState());
		return "23000".equals(state) || "23505".equals(state);
	}

	private BigDecimal toBigDecimal(String value) {
		String raw = safe(value);
		if (raw.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(raw);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private LocalDateTime toLocalDateTime(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}

	private boolean isBannedEffective(User user) {
		if (user == null || !Boolean.TRUE.equals(user.getIsBanned())) {
			return false;
		}

		LocalDateTime banUntil = user.getBanUntil();
		if (banUntil == null) {
			return true;
		}
		return banUntil.isAfter(LocalDateTime.now());
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private String nullIfBlank(String value) {
		String normalized = normalize(value);
		return normalized.isBlank() ? null : normalized;
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}

	private void ensureTables() {
		String[] ddl = new String[] {
				"CREATE TABLE IF NOT EXISTS pharmacies ("
						+ "id INT AUTO_INCREMENT PRIMARY KEY,"
						+ "pharmacien_id INT NULL,"
						+ "nom VARCHAR(150) NOT NULL,"
						+ "adresse VARCHAR(255) NOT NULL,"
						+ "telephone VARCHAR(20) NULL,"
						+ "email VARCHAR(100) NULL,"
						+ "horaires VARCHAR(255) NULL,"
						+ "latitude DECIMAL(10,6) NULL,"
						+ "longitude DECIMAL(10,6) NULL,"
						+ "is_active TINYINT(1) NOT NULL DEFAULT 1,"
						+ "image_name VARCHAR(255) NULL,"
						+ "created_at DATETIME NULL,"
						+ "updated_at DATETIME NULL"
						+ ")",
				"CREATE TABLE IF NOT EXISTS medicaments ("
						+ "id INT AUTO_INCREMENT PRIMARY KEY,"
						+ "nom VARCHAR(150) NOT NULL,"
						+ "type VARCHAR(50) NULL,"
						+ "description TEXT NULL,"
						+ "forme VARCHAR(50) NULL,"
						+ "dosage VARCHAR(50) NULL,"
						+ "prix DECIMAL(10,2) NULL,"
						+ "stock INT NOT NULL DEFAULT 0,"
						+ "laboratoire VARCHAR(100) NULL,"
						+ "code_barre VARCHAR(120) NULL,"
						+ "image_name VARCHAR(255) NULL,"
						+ "created_at DATETIME NULL,"
						+ "updated_at DATETIME NULL"
						+ ")",
				"CREATE TABLE IF NOT EXISTS stock_pharmacies ("
						+ "id INT AUTO_INCREMENT PRIMARY KEY,"
						+ "pharmacie_id INT NOT NULL,"
						+ "medicament_id INT NOT NULL,"
						+ "quantite INT NOT NULL DEFAULT 0,"
						+ "prix_vente DECIMAL(10,2) NULL,"
						+ "date_expiration DATE NULL,"
						+ "created_at DATETIME NULL,"
						+ "updated_at DATETIME NULL,"
						+ "UNIQUE KEY unique_stock (pharmacie_id, medicament_id)"
						+ ")",
				"CREATE TABLE IF NOT EXISTS reservations_medicaments ("
						+ "id INT AUTO_INCREMENT PRIMARY KEY,"
						+ "patient_id INT NOT NULL,"
						+ "pharmacie_id INT NOT NULL,"
						+ "medicament_id INT NOT NULL,"
						+ "stock_id INT NOT NULL,"
						+ "quantite INT NOT NULL DEFAULT 1,"
						+ "prix_unitaire DECIMAL(10,2) NULL,"
						+ "prix_total DECIMAL(10,2) NULL,"
						+ "statut VARCHAR(20) NOT NULL DEFAULT 'en_attente',"
						+ "created_at DATETIME NULL,"
						+ "updated_at DATETIME NULL,"
						+ "expires_at DATETIME NULL,"
						+ "confirmed_at DATETIME NULL,"
						+ "cancelled_at DATETIME NULL,"
						+ "rejected_at DATETIME NULL"
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

	public enum Permission {
		VIEW,
		RESERVE,
		MANAGE,
		STOCK,
		ORDERS
	}

	public record AccessDecision(boolean allowed, String message) {
		public static AccessDecision allow(String message) {
			return new AccessDecision(true, message);
		}

		public static AccessDecision deny(String message) {
			return new AccessDecision(false, message);
		}
	}

	public record ActionResult(boolean success, String message, int numericValue) {
		public static ActionResult success(String message, int numericValue) {
			return new ActionResult(true, message, numericValue);
		}

		public static ActionResult failure(String message) {
			return new ActionResult(false, message, 0);
		}
	}

	public record ModuleData(
			List<PharmacyRow> publicPharmacies,
			List<PharmacyRow> managedPharmacies,
			List<MedicamentRow> medicaments,
			List<StockRow> availableStocks,
			List<StockRow> managedStocks,
			List<ReservationRow> myReservations,
			List<ReservationRow> incomingReservations
	) {
		public static ModuleData empty() {
			return new ModuleData(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
		}
	}

	public record PharmacyRow(
			int id,
			Integer pharmacienId,
			String nom,
			String adresse,
			String telephone,
			String email,
			String horaires,
			String latitude,
			String longitude,
			boolean active,
			String imageName
	) {
	}

	public record MedicamentRow(
			int id,
			String nom,
			String type,
			String description,
			String forme,
			String dosage,
			String prix,
			int stock,
			String laboratoire,
			String codeBarre,
			String imageName
	) {
	}

	public record StockRow(
			int id,
			int pharmacyId,
			String pharmacyNom,
			String pharmacyAdresse,
			int medicamentId,
			String medicamentNom,
			String medicamentType,
			String medicamentForme,
			String medicamentDosage,
			int quantite,
			String prixVente
	) {
	}

	public record ReservationRow(
			int id,
			int patientId,
			String patientDisplay,
			int pharmacyId,
			String pharmacyNom,
			String pharmacyAdresse,
			int medicamentId,
			String medicamentNom,
			int quantite,
			String prixUnitaire,
			String prixTotal,
			String statut,
			LocalDateTime createdAt,
			LocalDateTime expiresAt,
			LocalDateTime confirmedAt,
			LocalDateTime cancelledAt,
			LocalDateTime rejectedAt
	) {
		public String statusLabel() {
			if (statut == null || statut.isBlank()) {
				return "Inconnu";
			}
			return switch (statut.toLowerCase(Locale.ROOT)) {
				case "en_attente" -> "En attente";
				case "confirmee" -> "Confirmee";
				case "refusee" -> "Refusee";
				case "annulee" -> "Annulee";
				case "expiree" -> "Expiree";
				default -> statut;
			};
		}
	}

	public record PharmacyDraft(
			String nom,
			String adresse,
			String telephone,
			String email,
			String horaires,
			String latitude,
			String longitude,
			boolean active
	) {
	}

	public record MedicamentDraft(
			String nom,
			String type,
			String description,
			String forme,
			String dosage,
			String prix,
			int stock,
			String laboratoire,
			String codeBarre
	) {
	}

	public record StockDraft(
			int pharmacyId,
			int medicamentId,
			int quantite,
			String prixVente
	) {
	}

	public static String formatDateTime(LocalDateTime value) {
		if (value == null) {
			return "";
		}
		return DATE_TIME_FORMATTER.format(value);
	}
}
