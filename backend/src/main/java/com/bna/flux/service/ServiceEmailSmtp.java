package com.bna.flux.service;

import com.bna.flux.entity.Alerte;
import com.bna.flux.entity.Transaction;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Implémentation SMTP réelle du service d'email.
 * Active avec le profil "smtp" ou quand spring.mail.host est configuré.
 *
 * @author Slim Issa — Projet Stage BNA
 * @since 2026-08-08
 */
@Slf4j
@Service
@Profile("docker")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.mail.host")
@RequiredArgsConstructor
public class ServiceEmailSmtp implements ServiceEmail {

    private final JavaMailSender mailSender;

    @Value("${bna.alertes.email.destinataire-defaut:surveillance@bna.com.tn}")
    private String destinataireDefaut;

    @Value("${bna.alertes.email.expediteur:bna-flux@bna.com.tn}")
    private String expediteur;

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    @Async("emailExecutor")
    public void envoyerEmailImmediat(Alerte alerte, Transaction transaction) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String destinataire = alerte.getEmailDestinataire() != null
                    ? alerte.getEmailDestinataire() : destinataireDefaut;

            helper.setFrom(expediteur);
            helper.setTo(destinataire);
            helper.setSubject("[BNA-FLUX] Alerte CRITIQUE — " +
                    (alerte.getRegle() != null ? alerte.getRegle().getNom() : "Règle inconnue"));

            String corps = buildEmailCritique(alerte, transaction);
            helper.setText(corps, true);

            mailSender.send(message);
            log.info("Email CRITIQUE envoyé à {} — Transaction {}", destinataire, transaction.getReferenceTransaction());

            alerte.marquerEmailEnvoye(destinataire);
        } catch (MessagingException e) {
            log.error("Échec envoi email CRITIQUE: {}", e.getMessage());
        }
    }

    @Override
    @Async("emailExecutor")
    public void envoyerEmailGroupe(List<Alerte> alertes, String destinataire) {
        if (alertes == null || alertes.isEmpty()) return;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String dest = destinataire != null ? destinataire : destinataireDefaut;

            helper.setFrom(expediteur);
            helper.setTo(dest);
            helper.setSubject("[BNA-FLUX] Rapport d'alertes — " + alertes.size() + " alerte(s) ELEVE");

            String corps = buildEmailGroupe(alertes);
            helper.setText(corps, true);

            mailSender.send(message);
            log.info("Email groupé envoyé à {} — {} alerte(s)", dest, alertes.size());

            for (Alerte alerte : alertes) {
                alerte.marquerEmailEnvoye(dest);
            }
        } catch (MessagingException e) {
            log.error("Échec envoi email groupé: {}", e.getMessage());
        }
    }

    @Override
    @Async("emailExecutor")
    public void envoyerNotificationDisjoncteur(String typeCible, String identifiantCible, int nombreEchecs) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(expediteur);
            helper.setTo(destinataireDefaut);
            helper.setSubject("[BNA-FLUX] Disjoncteur OUVERT — " + typeCible + " " + identifiantCible);

            String corps = String.format("""
                <h2>Disjoncteur Ouvert</h2>
                <p>Un circuit breaker a été automatiquement ouvert.</p>
                <table border="1" cellpadding="8" style="border-collapse:collapse">
                    <tr><td><b>Type</b></td><td>%s</td></tr>
                    <tr><td><b>Identifiant</b></td><td>%s</td></tr>
                    <tr><td><b>Nombre d'échecs</b></td><td>%d</td></tr>
                </table>
                <p>Veuillez vérifier et réinitialiser si nécessaire.</p>
                """, typeCible, identifiantCible, nombreEchecs);

            helper.setText(corps, true);
            mailSender.send(message);
            log.info("Notification disjoncteur envoyée — {} {}", typeCible, identifiantCible);
        } catch (MessagingException e) {
            log.error("Échec envoi notification disjoncteur: {}", e.getMessage());
        }
    }

    @Override
    public boolean estOperationnel() {
        try {
            mailSender.createMimeMessage();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getTypeImplementation() {
        return "SMTP";
    }

    private String buildEmailCritique(Alerte alerte, Transaction transaction) {
        return String.format("""
            <html><body style="font-family:Arial,sans-serif">
            <div style="background:#e74c3c;color:white;padding:16px;border-radius:8px 8px 0 0">
                <h2 style="margin:0">🚨 Alerte CRITIQUE</h2>
            </div>
            <div style="border:1px solid #ddd;padding:16px;border-radius:0 0 8px 8px">
                <p>Une alerte de niveau <b>CRITIQUE</b> a été déclenchée.</p>
                <table border="1" cellpadding="8" style="border-collapse:collapse;width:100%%">
                    <tr><td><b>Transaction</b></td><td>%s</td></tr>
                    <tr><td><b>Règle</b></td><td>%s</td></tr>
                    <tr><td><b>Montant</b></td><td>%s %s</td></tr>
                    <tr><td><b>Date</b></td><td>%s</td></tr>
                    <tr><td><b>Score</b></td><td>%s/100</td></tr>
                    <tr><td><b>Statut</b></td><td>%s</td></tr>
                </table>
                <p style="margin-top:16px;color:#e74c3c"><b>Veuillez prendre les mesures nécessaires.</b></p>
            </div>
            <p style="color:#999;font-size:11px">BNA-FLUX v1.0 — Banque Nationale Agricole</p>
            </body></html>
            """,
            transaction.getReferenceTransaction(),
            alerte.getRegle() != null ? alerte.getRegle().getNom() : "N/A",
            transaction.getMontant(),
            transaction.getDevise() != null ? transaction.getDevise().getCode() : "",
            transaction.getDateTransaction() != null ? transaction.getDateTransaction().format(FORMAT_DATE) : "N/A",
            transaction.getScoreRisque(),
            transaction.getStatut() != null ? transaction.getStatut().name() : "N/A"
        );
    }

    private String buildEmailGroupe(List<Alerte> alertes) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            <html><body style="font-family:Arial,sans-serif">
            <div style="background:#e67e22;color:white;padding:16px;border-radius:8px 8px 0 0">
                <h2 style="margin:0">📋 Rapport d'Alertes</h2>
            </div>
            <div style="border:1px solid #ddd;padding:16px;border-radius:0 0 8px 8px">
            """);

        for (int i = 0; i < alertes.size(); i++) {
            Alerte a = alertes.get(i);
            Transaction t = a.getTransaction();
            sb.append(String.format("""
                <div style="margin-bottom:12px;padding:12px;background:#fafafa;border-left:4px solid #e67e22">
                    <b>Alerte #%d</b> — %s<br>
                    Transaction: %s | Montant: %s %s | Statut: %s
                </div>
                """,
                i + 1,
                a.getRegle() != null ? a.getRegle().getNom() : "N/A",
                t != null ? t.getReferenceTransaction() : "N/A",
                t != null ? t.getMontant() : "N/A",
                t != null && t.getDevise() != null ? t.getDevise().getCode() : "",
                t != null && t.getStatut() != null ? t.getStatut().name() : "N/A"
            ));
        }

        sb.append("""
            </div>
            <p style="color:#999;font-size:11px">BNA-FLUX v1.0 — Banque Nationale Agricole</p>
            </body></html>
            """);

        return sb.toString();
    }
}
