package com.santea.service;

import com.santea.config.MailConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.Properties;

public class EmailService {
    private final MailConfig mailConfig;
    private String lastError = "";

    public EmailService() {
        this.mailConfig = MailConfig.fromEnvironment();
    }

    public boolean sendEmailVerification(String toEmail, String fullName, String verificationToken) {
        String verifyUrl = normalizeBaseUrl(mailConfig.appBaseUrl()) + "/verify-email/" + verificationToken;
        String subject = "Confirmation de votre email - SANTEA";

        String html = "<h2>Bienvenue " + escapeHtml(fullName) + "</h2>"
                + "<p>Merci d'avoir cree votre compte SANTEA.</p>"
                + "<p>Confirmez votre email en cliquant ici :</p>"
                + "<p><a href=\"" + verifyUrl + "\">Verifier mon email</a></p>"
                + "<p>Si le bouton ne fonctionne pas, copiez ce lien :<br>" + verifyUrl + "</p>";

        String text = "Bienvenue " + fullName + "\n"
                + "Merci d'avoir cree votre compte SANTEA.\n"
                + "Verifiez votre email via ce lien: " + verifyUrl;

        return sendEmail(toEmail, subject, html, text);
    }

    public boolean sendPasswordReset(String toEmail, String fullName, String token) {
        String resetUrl = normalizeBaseUrl(mailConfig.appBaseUrl()) + "/reset-password/" + token;
        String subject = "Reinitialisation de votre mot de passe - SANTEA";

        String html = "<h2>Bonjour " + escapeHtml(fullName) + "</h2>"
                + "<p>Nous avons recu une demande de reinitialisation de mot de passe.</p>"
                + "<p>Lien de reinitialisation :</p>"
                + "<p><a href=\"" + resetUrl + "\">Reinitialiser mon mot de passe</a></p>"
                + "<p>Token (si necessaire dans JavaFX) : <strong>" + escapeHtml(token) + "</strong></p>"
                + "<p>Ce lien expire dans 1 heure.</p>";

        String text = "Bonjour " + fullName + "\n"
                + "Demande de reinitialisation recue.\n"
                + "Lien: " + resetUrl + "\n"
                + "Token: " + token + "\n"
                + "Le lien expire dans 1 heure.";

        return sendEmail(toEmail, subject, html, text);
    }

    public boolean isConfigured() {
        return mailConfig.isEnabled();
    }

        public boolean sendInvoice(
            String toEmail,
            String fullName,
            String invoiceNumber,
            String pdfPath,
            String planType,
            BigDecimal amount,
            String currency
        ) {
        String subject = "Votre facture SANTEA - " + safe(invoiceNumber);
        String amountText = (amount == null ? "0.00" : amount.toPlainString()) + " " + (currency == null ? "EUR" : currency.toUpperCase());

            String displayName = safe(fullName).isBlank() ? safe(toEmail) : safe(fullName);

            String html = "<!DOCTYPE html>"
                    + "<html lang=\"fr\">"
                    + "<head><meta charset=\"UTF-8\"><title>Votre facture</title></head>"
                    + "<body style=\"margin:0; padding:0; background:#f6f9fc; font-family: Arial, sans-serif; color:#1f2937;\">"
                    + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"background:#f6f9fc; padding:30px 0;\">"
                    + "<tr><td align=\"center\">"
                    + "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"background:#ffffff; border-radius:16px; overflow:hidden; box-shadow:0 12px 30px rgba(15,23,42,0.08);\">"
                    + "<tr><td style=\"background:linear-gradient(135deg,#40C4FF,#0288D1); padding:24px;\">"
                    + "<h1 style=\"margin:0; color:#ffffff; font-size:22px;\">SANTEA</h1>"
                    + "<p style=\"margin:6px 0 0; color:#e0f2fe; font-size:14px;\">Facture & abonnement</p>"
                    + "</td></tr>"
                    + "<tr><td style=\"padding:28px;\">"
                    + "<h2 style=\"margin:0 0 10px; font-size:20px;\">Votre facture est prete</h2>"
                    + "<p style=\"margin:0 0 14px; color:#4b5563;\">Bonjour " + escapeHtml(displayName) + ",</p>"
                    + "<p style=\"margin:0 0 16px; color:#4b5563;\">Votre paiement a ete confirme. Vous trouverez la facture en piece jointe.</p>"
                    + "<div style=\"background:#f9fafb; border:1px solid #eef2f6; border-radius:12px; padding:16px; margin:14px 0;\">"
                    + "<p style=\"margin:0 0 6px; font-weight:bold;\">Details</p>"
                    + "<p style=\"margin:0; color:#6b7280;\">Numero facture: <strong>" + escapeHtml(invoiceNumber) + "</strong></p>"
                    + "<p style=\"margin:0; color:#6b7280;\">Plan: <strong>" + escapeHtml(planType) + "</strong></p>"
                    + "<p style=\"margin:0; color:#6b7280;\">Total TTC: <strong>" + escapeHtml(amountText) + "</strong></p>"
                    + "</div>"
                    + "<p style=\"margin:0; color:#6b7280; font-size:12px;\">Besoin d'aide ? Contactez notre support.</p>"
                    + "</td></tr>"
                    + "<tr><td style=\"padding:18px 28px; background:#f9fafb; color:#6b7280; font-size:12px;\">"
                    + "Merci pour votre confiance."
                    + "</td></tr>"
                    + "</table></td></tr></table></body></html>";

            String text = "Votre facture est prete\n\n"
                    + "Bonjour " + displayName + ",\n"
                    + "Votre paiement a ete confirme. Vous trouverez la facture en piece jointe.\n\n"
                    + "Details\n"
                    + "Numero facture: " + safe(invoiceNumber) + "\n"
                    + "Plan: " + safe(planType) + "\n"
                    + "Total TTC: " + amountText + "\n\n"
                    + "Merci pour votre confiance.";

        return sendEmailWithAttachment(toEmail, subject, html, text, pdfPath);
        }

    public String getLastError() {
        return lastError == null ? "" : lastError;
    }

    public String getConfigSummary() {
        return mailConfig.debugSummary();
    }

    private boolean sendEmail(String toEmail, String subject, String htmlBody, String textBody) {
        lastError = "";

        if (!mailConfig.isEnabled()) {
            lastError = "Configuration email désactivée (MAILER_DSN null://null ou SMTP non configuré). " + mailConfig.debugSummary();
            return false;
        }

        if (mailConfig.host() == null || mailConfig.host().isBlank()) {
            lastError = "Hôte SMTP manquant. " + mailConfig.debugSummary();
            return false;
        }

        if (mailConfig.fromAddress() == null || mailConfig.fromAddress().isBlank()) {
            lastError = "Adresse expéditeur MAIL_FROM manquante. " + mailConfig.debugSummary();
            return false;
        }

        if (mailConfig.auth() && (mailConfig.username().isBlank() || mailConfig.password().isBlank())) {
            lastError = "Authentification SMTP active mais MAIL_USERNAME/MAIL_PASSWORD manquants. " + mailConfig.debugSummary();
            return false;
        }

        try {
            Session session = buildSession();
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(mailConfig.fromAddress(), mailConfig.fromName(), StandardCharsets.UTF_8.name()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
            message.setSubject(subject, StandardCharsets.UTF_8.name());

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(textBody, StandardCharsets.UTF_8.name());

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");

            MimeMultipart alternative = new MimeMultipart("alternative");
            alternative.addBodyPart(textPart);
            alternative.addBodyPart(htmlPart);

            message.setContent(alternative);

            Transport.send(message);
            lastError = "";
            return true;
        } catch (MessagingException exception) {
            lastError = "Erreur SMTP: " + exception.getClass().getSimpleName() + " - " + safeMessage(exception.getMessage());
            System.err.println("[SANTEA][MAIL] " + lastError);
            exception.printStackTrace();
            return false;
        } catch (Exception exception) {
            lastError = "Erreur email: " + exception.getClass().getSimpleName() + " - " + safeMessage(exception.getMessage());
            System.err.println("[SANTEA][MAIL] " + lastError);
            exception.printStackTrace();
            return false;
        }
    }

    private boolean sendEmailWithAttachment(String toEmail, String subject, String htmlBody, String textBody, String attachmentPath) {
        lastError = "";

        if (!mailConfig.isEnabled()) {
            lastError = "Configuration email désactivée (MAILER_DSN null://null ou SMTP non configuré). " + mailConfig.debugSummary();
            return false;
        }

        if (mailConfig.host() == null || mailConfig.host().isBlank()) {
            lastError = "Hôte SMTP manquant. " + mailConfig.debugSummary();
            return false;
        }

        try {
            Session session = buildSession();
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(mailConfig.fromAddress(), mailConfig.fromName(), StandardCharsets.UTF_8.name()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
            message.setSubject(subject, StandardCharsets.UTF_8.name());

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(textBody, StandardCharsets.UTF_8.name());

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");

            MimeMultipart alternative = new MimeMultipart("alternative");
            alternative.addBodyPart(textPart);
            alternative.addBodyPart(htmlPart);

            MimeBodyPart alternativeContainer = new MimeBodyPart();
            alternativeContainer.setContent(alternative);

            MimeMultipart mixed = new MimeMultipart("mixed");
            mixed.addBodyPart(alternativeContainer);

            if (attachmentPath != null && !attachmentPath.isBlank()) {
                Path file = Path.of(attachmentPath);
                if (Files.exists(file)) {
                    MimeBodyPart attachment = new MimeBodyPart();
                    attachment.attachFile(file.toFile());
                    attachment.setFileName(file.getFileName().toString());
                    mixed.addBodyPart(attachment);
                }
            }

            message.setContent(mixed);
            Transport.send(message);
            lastError = "";
            return true;
        } catch (MessagingException exception) {
            lastError = "Erreur SMTP: " + exception.getClass().getSimpleName() + " - " + safeMessage(exception.getMessage());
            System.err.println("[SANTEA][MAIL] " + lastError);
            exception.printStackTrace();
            return false;
        } catch (Exception exception) {
            lastError = "Erreur email: " + exception.getClass().getSimpleName() + " - " + safeMessage(exception.getMessage());
            System.err.println("[SANTEA][MAIL] " + lastError);
            exception.printStackTrace();
            return false;
        }
    }

    private Session buildSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", mailConfig.host());
        props.put("mail.smtp.port", String.valueOf(mailConfig.port()));
        props.put("mail.smtp.auth", String.valueOf(mailConfig.auth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(mailConfig.startTls()));
        props.put("mail.smtp.ssl.enable", String.valueOf(mailConfig.ssl()));

        if (mailConfig.auth()) {
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(mailConfig.username(), mailConfig.password());
                }
            });
        }

        return Session.getInstance(props);
    }

    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8000";
        }
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
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

    private String safeMessage(String message) {
        return (message == null || message.isBlank()) ? "message indisponible" : message;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
