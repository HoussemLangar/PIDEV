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
        String displayName = safe(fullName).isBlank() ? "Utilisateur" : safe(fullName);

        String html = buildActionEmailHtml(
            "Activation du compte",
            "Bienvenue sur SANTEA",
            displayName,
            "Merci d'avoir cree votre compte. Activez votre adresse email pour finaliser l'inscription.",
            "Verifier mon email",
            verifyUrl,
            "Ce lien expire dans 48 heures.",
            "Token de verification",
            verificationToken
        );

        String text = buildActionEmailText(
            "Bienvenue sur SANTEA",
            displayName,
            "Merci d'avoir cree votre compte. Activez votre adresse email pour finaliser l'inscription.",
            verifyUrl,
            "Ce lien expire dans 48 heures.",
            "Token de verification",
            verificationToken
        );

        return sendEmail(toEmail, subject, html, text);
    }

    public boolean sendPasswordReset(String toEmail, String fullName, String token) {
        String resetUrl = normalizeBaseUrl(mailConfig.appBaseUrl()) + "/reset-password/" + token;
        String subject = "Reinitialisation de votre mot de passe - SANTEA";
        String displayName = safe(fullName).isBlank() ? "Utilisateur" : safe(fullName);

        String html = buildActionEmailHtml(
            "Securite compte",
            "Reinitialisation de mot de passe",
            displayName,
            "Nous avons recu une demande de reinitialisation. Si vous etes a l'origine de cette action, continuez via le bouton ci-dessous.",
            "Reinitialiser mon mot de passe",
            resetUrl,
            "Ce lien expire dans 1 heure.",
            "Token de secours",
            token
        );

        String text = buildActionEmailText(
            "Reinitialisation de mot de passe",
            displayName,
            "Nous avons recu une demande de reinitialisation de votre mot de passe.",
            resetUrl,
            "Ce lien expire dans 1 heure.",
            "Token de secours",
            token
        );

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

        private String buildActionEmailHtml(
            String badge,
            String title,
            String recipientName,
            String intro,
            String actionLabel,
            String actionUrl,
            String expiryText,
            String tokenLabel,
            String tokenValue
        ) {
        return "<!DOCTYPE html>"
            + "<html lang=\"fr\">"
            + "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><title>" + escapeHtml(title) + "</title></head>"
            + "<body style=\"margin:0; padding:0; background:#f3f8fc; font-family:Segoe UI, Arial, sans-serif; color:#0f172a;\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f3f8fc; padding:28px 0;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"620\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-inline-size:620px; background:#ffffff; border-radius:18px; overflow:hidden; box-shadow:0 14px 40px rgba(15,23,42,0.10);\">"
            + "<tr><td style=\"padding:24px; background:linear-gradient(120deg,#0891b2,#2563eb);\">"
            + "<p style=\"margin:0 0 8px; color:#dbeafe; font-size:12px; letter-spacing:1px; text-transform:uppercase; font-weight:700;\">" + escapeHtml(badge) + "</p>"
            + "<h1 style=\"margin:0; color:#ffffff; font-size:25px; line-height:1.2;\">" + escapeHtml(title) + "</h1>"
            + "</td></tr>"
            + "<tr><td style=\"padding:26px;\">"
            + "<p style=\"margin:0 0 12px; font-size:16px; font-weight:700; color:#0f172a;\">Bonjour " + escapeHtml(recipientName) + ",</p>"
            + "<p style=\"margin:0 0 20px; color:#334155; line-height:1.6; font-size:14px;\">" + escapeHtml(intro) + "</p>"
            + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr><td>"
            + "<a href=\"" + escapeHtml(actionUrl) + "\" style=\"display:inline-block; background:linear-gradient(120deg,#0891b2,#2563eb); color:#ffffff; text-decoration:none; font-weight:700; padding:12px 18px; border-radius:10px;\">" + escapeHtml(actionLabel) + "</a>"
            + "</td></tr></table>"
            + "<p style=\"margin:16px 0 6px; color:#64748b; font-size:12px;\">" + escapeHtml(expiryText) + "</p>"
            + "<p style=\"margin:0; color:#64748b; font-size:12px;\">Si le bouton ne fonctionne pas, copiez ce lien:<br><span style=\"color:#0f172a;\">" + escapeHtml(actionUrl) + "</span></p>"
                + "<div style=\"margin-block-start:14px; background:#f8fafc; border:1px solid #e2e8f0; border-radius:10px; padding:10px 12px;\">"
            + "<p style=\"margin:0; color:#334155; font-size:12px;\">" + escapeHtml(tokenLabel) + ": <strong>" + escapeHtml(tokenValue) + "</strong></p>"
            + "</div>"
            + "</td></tr>"
            + "<tr><td style=\"padding:16px 26px; background:#f8fafc; color:#64748b; font-size:12px;\">Equipe SANTEA - Message automatique de securite</td></tr>"
            + "</table></td></tr></table></body></html>";
        }

        private String buildActionEmailText(
            String title,
            String recipientName,
            String intro,
            String actionUrl,
            String expiryText,
            String tokenLabel,
            String tokenValue
        ) {
        return title + "\n\n"
            + "Bonjour " + recipientName + ",\n"
            + intro + "\n\n"
            + "Lien: " + actionUrl + "\n"
            + expiryText + "\n"
            + tokenLabel + ": " + tokenValue + "\n\n"
            + "Equipe SANTEA";
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
