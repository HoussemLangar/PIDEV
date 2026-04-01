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
}
