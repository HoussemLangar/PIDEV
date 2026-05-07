package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class InvoiceService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DatabaseService databaseService;

    public InvoiceService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    }

    public InvoiceResult createInvoice(int userId, int abonnementId, String planType, BigDecimal amountTtc, String currency, String outputDirectory) {
        if (userId <= 0 || abonnementId <= 0) {
            return InvoiceResult.failure("Parametres de facture invalides.");
        }
        if (outputDirectory == null || outputDirectory.isBlank()) {
            return InvoiceResult.failure("Aucun emplacement de sauvegarde de facture fourni.");
        }

        try {
            ensureFactureTable();
            String numero = buildInvoiceNumber();

            BigDecimal tvaRate = new BigDecimal("19.00");
            BigDecimal montantHt = amountTtc.divide(new BigDecimal("1.19"), 2, RoundingMode.HALF_UP);
            BigDecimal montantTva = amountTtc.subtract(montantHt).setScale(2, RoundingMode.HALF_UP);
            InvoiceContext context = loadInvoiceContext(userId, abonnementId, numero, planType, amountTtc, montantHt, tvaRate, montantTva, currency);
            Path pdfPath = generatePdf(context, outputDirectory);

            String sql = "INSERT INTO factures (numero, user_id, abonnement_id, montant_ht, tva_taux, tva_montant, montant_ttc, devise, pdf_path, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, numero);
                statement.setInt(2, userId);
                statement.setInt(3, abonnementId);
                statement.setBigDecimal(4, montantHt);
                statement.setBigDecimal(5, tvaRate);
                statement.setBigDecimal(6, montantTva);
                statement.setBigDecimal(7, amountTtc.setScale(2, RoundingMode.HALF_UP));
                statement.setString(8, safeCurrency(currency));
                statement.setString(9, pdfPath.toString());
                statement.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
                statement.executeUpdate();
            }

            return InvoiceResult.success(numero, pdfPath.toString());
        } catch (Exception exception) {
            return InvoiceResult.failure("Generation facture impossible: " + exception.getMessage());
        }
    }

    private void ensureFactureTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS factures ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "numero VARCHAR(50) NOT NULL,"
                + "user_id INT NOT NULL,"
                + "abonnement_id INT NOT NULL,"
                + "montant_ht NUMERIC(10,2) NOT NULL,"
                + "tva_taux NUMERIC(5,2) NOT NULL,"
                + "tva_montant NUMERIC(10,2) NOT NULL,"
                + "montant_ttc NUMERIC(10,2) NOT NULL,"
                + "devise VARCHAR(8) NOT NULL,"
                + "pdf_path VARCHAR(1024) NOT NULL,"
                + "created_at DATETIME NOT NULL"
                + ")";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private Path generatePdf(InvoiceContext context, String outputDirectory) throws IOException {
        Path folder = Paths.get(outputDirectory);
        Files.createDirectories(folder);

        String fileName = "facture-" + context.invoiceNumber + ".pdf";
        Path file = folder.resolve(fileName);

        String html = buildInvoicePdfHtml(context);
        try (OutputStream outputStream = Files.newOutputStream(file)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
        } catch (Exception exception) {
            throw new IOException("Rendu PDF impossible: " + exception.getMessage(), exception);
        }
        return file;
    }

    private InvoiceContext loadInvoiceContext(
            int userId,
            int abonnementId,
            String invoiceNumber,
            String fallbackPlanType,
            BigDecimal amountTtc,
            BigDecimal montantHt,
            BigDecimal tvaRate,
            BigDecimal montantTva,
            String currency
    ) {
        String sql = "SELECT "
                + "u.nom, u.prenom, u.email, u.adresse, "
                + "a.nom AS abonnement_nom, a.date_debut, a.date_fin "
                + "FROM users u "
                + "LEFT JOIN abonnements a ON a.id = ? "
                + "WHERE u.id = ? LIMIT 1";

        String fullName = "";
        String email = "";
        String address = "";
        String planName = safe(fallbackPlanType);
        LocalDate startDate = null;
        LocalDate endDate = null;

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, abonnementId);
            statement.setInt(2, userId);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String nom = safe(rs.getString("nom"));
                    String prenom = safe(rs.getString("prenom"));
                    fullName = (nom + " " + prenom).trim();
                    email = safe(rs.getString("email"));
                    address = safe(rs.getString("adresse"));
                    String dbPlan = safe(rs.getString("abonnement_nom"));
                    if (!dbPlan.isBlank()) {
                        planName = dbPlan;
                    }

                    java.sql.Date dStart = rs.getDate("date_debut");
                    java.sql.Date dEnd = rs.getDate("date_fin");
                    startDate = dStart == null ? null : dStart.toLocalDate();
                    endDate = dEnd == null ? null : dEnd.toLocalDate();
                }
            }
        } catch (SQLException ignored) {
            // Non bloquant: fallback sur valeurs minimales.
        }

        if (fullName.isBlank()) {
            fullName = email;
        }

        return new InvoiceContext(
                invoiceNumber,
                LocalDateTime.now(),
                fullName,
                email,
                address,
                planName,
                startDate,
                endDate,
                amount(montantHt),
                amount(tvaRate),
                amount(montantTva),
                amount(amountTtc),
                safeCurrency(currency)
        );
    }

    private String buildInvoicePdfHtml(InvoiceContext context) {
        return "<!DOCTYPE html>"
                + "<html lang=\"fr\">"
                + "<head>"
            + "<meta charset=\"UTF-8\"/>"
                + "<style>"
                + "body{font-family:DejaVu Sans,Arial,sans-serif;color:#1f2937;font-size:12px;}"
                + ".header-table{width:100%;border-collapse:collapse;}"
                + ".header-left{text-align:left;vertical-align:top;}"
                + ".header-right{text-align:right;vertical-align:top;}"
                + ".brand{font-size:20px;font-weight:bold;color:#0288D1;}"
                + ".invoice-box{margin-top:10px;padding:16px;border:1px solid #e5e7eb;border-radius:8px;}"
                + ".row-table{width:100%;border-collapse:collapse;margin-top:6px;}"
                + ".row-left{width:58%;vertical-align:top;}"
                + ".row-right{width:42%;vertical-align:top;}"
                + ".muted{color:#6b7280;}"
                + "table{width:100%;border-collapse:collapse;margin-top:16px;}"
                + "th,td{border-bottom:1px solid #e5e7eb;padding:8px;text-align:left;}"
                + "th{background:#f9fafb;}"
                + ".total{text-align:right;margin-top:12px;}"
                + ".total strong{font-size:14px;}"
                + ".footer{margin-top:24px;font-size:11px;color:#6b7280;}"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<table class=\"header-table\"><tr>"
                + "<td class=\"header-left\"><div class=\"brand\">SANTEA</div></td>"
                + "<td class=\"header-right\"><div><strong>Facture</strong> #" + escapeHtml(context.invoiceNumber) + "</div>"
                + "<div class=\"muted\">Date: " + escapeHtml(formatDate(context.createdAt.toLocalDate())) + "</div></td>"
                + "</tr></table>"
                + "<div class=\"invoice-box\">"
                + "<table class=\"row-table\"><tr><td class=\"row-left\"><strong>Client</strong><br/>"
                + escapeHtml(defaultIfBlank(context.customerName, context.customerEmail)) + "<br/>"
                + escapeHtml(context.customerEmail) + "<br/>"
                + escapeHtml(context.customerAddress)
                + "</td><td class=\"row-right\"><strong>Abonnement</strong><br/>"
                + escapeHtml(context.planName) + "<br/>"
                + "Debut: " + escapeHtml(formatDate(context.startDate)) + "<br/>"
                + "Fin: " + escapeHtml(formatDate(context.endDate))
                + "</td></tr></table></div>"
                + "<table><thead><tr><th>Description</th><th>Qte</th><th>Prix HT</th><th>Total HT</th></tr></thead><tbody><tr>"
                + "<td>Abonnement " + escapeHtml(context.planName) + " (1 mois)</td>"
                + "<td>1</td>"
                + "<td>" + escapeHtml(context.amountHt) + " " + escapeHtml(context.currency) + "</td>"
                + "<td>" + escapeHtml(context.amountHt) + " " + escapeHtml(context.currency) + "</td>"
                + "</tr></tbody></table>"
                + "<div class=\"total\"><div>Sous-total: " + escapeHtml(context.amountHt) + " " + escapeHtml(context.currency) + "</div>"
                + "<div>TVA (" + escapeHtml(context.tvaRate) + "%): " + escapeHtml(context.tvaAmount) + " " + escapeHtml(context.currency) + "</div>"
                + "<strong>Total TTC: " + escapeHtml(context.amountTtc) + " " + escapeHtml(context.currency) + "</strong></div>"
                + "<div class=\"footer\">Merci pour votre confiance. Cette facture a ete generee automatiquement.</div>"
                + "</body></html>";
    }

    private String buildInvoiceNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "INV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;
    }

    private String amount(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : DATE_FMT.format(date);
    }

    private String defaultIfBlank(String value, String fallback) {
        String candidate = safe(value);
        return candidate.isBlank() ? safe(fallback) : candidate;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String safeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "EUR";
        }
        return currency.trim().toUpperCase();
    }

    public record InvoiceResult(boolean success, String invoiceNumber, String pdfPath, String message) {
        public static InvoiceResult success(String invoiceNumber, String pdfPath) {
            return new InvoiceResult(true, invoiceNumber, pdfPath, "Facture generee.");
        }

        public static InvoiceResult failure(String message) {
            return new InvoiceResult(false, "", "", message);
        }
    }

    private record InvoiceContext(
            String invoiceNumber,
            LocalDateTime createdAt,
            String customerName,
            String customerEmail,
            String customerAddress,
            String planName,
            LocalDate startDate,
            LocalDate endDate,
            String amountHt,
            String tvaRate,
            String tvaAmount,
            String amountTtc,
            String currency
    ) {
    }
}
