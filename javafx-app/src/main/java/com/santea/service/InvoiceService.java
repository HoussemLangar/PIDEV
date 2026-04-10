package com.santea.service;

import com.santea.config.DatabaseConfig;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class InvoiceService {
    private static final DateTimeFormatter NUMBER_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DatabaseService databaseService;

    public InvoiceService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    }

    public InvoiceResult createInvoice(int userId, int abonnementId, String planType, BigDecimal amountTtc, String currency) {
        if (userId <= 0 || abonnementId <= 0) {
            return InvoiceResult.failure("Parametres de facture invalides.");
        }

        try {
            ensureFactureTable();
            Path pdfPath = generatePdf(planType, amountTtc, currency);
            String numero = "FAC-" + NUMBER_FMT.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            BigDecimal tvaRate = new BigDecimal("0.19");
            BigDecimal montantHt = amountTtc.divide(BigDecimal.ONE.add(tvaRate), 2, RoundingMode.HALF_UP);
            BigDecimal montantTva = amountTtc.subtract(montantHt).setScale(2, RoundingMode.HALF_UP);

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

    private Path generatePdf(String planType, BigDecimal amount, String currency) throws IOException {
        Path folder = Paths.get("generated-invoices");
        Files.createDirectories(folder);

        String fileName = "invoice_" + NUMBER_FMT.format(LocalDateTime.now()) + "_" + UUID.randomUUID().toString().substring(0, 16) + ".pdf";
        Path file = folder.resolve(fileName);

        String amountLine = amount.setScale(2, RoundingMode.HALF_UP) + " " + safeCurrency(currency);
        String content = "BT\n/F1 24 Tf\n50 790 Td\n(Facture SANTEA) Tj\n"
                + "0 -30 Td\n/F1 12 Tf\n(Plan: " + escapePdf(planType) + ") Tj\n"
                + "0 -18 Td\n(Montant TTC: " + escapePdf(amountLine) + ") Tj\n"
                + "0 -18 Td\n(Date: " + escapePdf(LocalDateTime.now().toString()) + ") Tj\n"
                + "0 -18 Td\n(Merci pour votre abonnement.) Tj\nET";

        byte[] stream = content.getBytes(StandardCharsets.US_ASCII);

        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        int xref1 = pdf.length();
        pdf.append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n");
        int xref2 = pdf.length();
        pdf.append("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n");
        int xref3 = pdf.length();
        pdf.append("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n");
        int xref4 = pdf.length();
        pdf.append("4 0 obj << /Length ").append(stream.length).append(" >> stream\n");
        pdf.append(content).append("\nendstream endobj\n");
        int xref5 = pdf.length();
        pdf.append("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n");

        int xrefPos = pdf.length();
        pdf.append("xref\n0 6\n");
        pdf.append("0000000000 65535 f \n");
        pdf.append(formatXref(xref1));
        pdf.append(formatXref(xref2));
        pdf.append(formatXref(xref3));
        pdf.append(formatXref(xref4));
        pdf.append(formatXref(xref5));
        pdf.append("trailer << /Size 6 /Root 1 0 R >>\nstartxref\n");
        pdf.append(xrefPos).append("\n%%EOF\n");

        Files.writeString(file, pdf.toString(), StandardCharsets.US_ASCII);
        return file;
    }

    private String formatXref(int offset) {
        return String.format("%010d 00000 n \n", offset);
    }

    private String escapePdf(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("(", "[").replace(")", "]");
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
}
