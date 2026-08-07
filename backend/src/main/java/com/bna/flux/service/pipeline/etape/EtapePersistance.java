package com.bna.flux.service.pipeline.etape;

import com.bna.flux.entity.Alerte;
import com.bna.flux.entity.Transaction;
import com.bna.flux.entity.Alerte.NiveauAlerte;
import com.bna.flux.repository.AlerteRepository;
import com.bna.flux.repository.TransactionRepository;
import com.bna.flux.service.MoteurRegles.RegleDeclenchee;
import com.bna.flux.service.ServiceAudit;
import com.bna.flux.dto.NotificationWebSocket;
import com.bna.flux.service.ServiceEmail;
import com.bna.flux.service.ServiceNotificationWebSocket;
import com.bna.flux.service.pipeline.ContextePipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 5 du pipeline — Persistance et finalisation.
 * <p>
 * Dernière étape du pipeline. Elle est responsable de :
 * </p>
 * <ol>
 *   <li>Sauvegarder la transaction en base de données</li>
 *   <li>Générer les alertes pour chaque règle déclenchée</li>
 *   <li>Construire la piste d'audit hash-chaînée</li>
 *   <li>Déclencher l'envoi d'emails pour les alertes CRITIQUE (immédiat)
 *       et ELEVE (groupé)</li>
 *   <li>Marquer le contexte comme terminé</li>
 * </ol>
 *
 * <p><b>Ordre des opérations :</b></p>
 * <ol>
 *   <li>Sauvegarder la transaction (JPA)</li>
 *   <li>Générer et sauvegarder les alertes</li>
 *   <li>Enregistrer l'entrée d'audit PERSISTANCE</li>
 *   <li>Envoyer les emails d'alerte (asynchrone)</li>
 * </ol>
 *
 * <p><b>Gestion des erreurs :</b></p>
 * <p>
 * Si la persistance échoue, la transaction est perdue (pas de file d'attente
 * dans ce prototype). En production, un mécanisme de retry avec file morte
 * (dead letter queue) serait implémenté.
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Component
public class EtapePersistance {

    private final TransactionRepository transactionRepository;
    private final AlerteRepository alerteRepository;
    private final ServiceAudit serviceAudit;
    private final ServiceEmail serviceEmail;
    private final ServiceNotificationWebSocket serviceNotificationWebSocket;

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param transactionRepository le repository des transactions
     * @param alerteRepository     le repository des alertes
     * @param serviceAudit         le service d'audit
     * @param serviceEmail         le service d'email
     */
    public EtapePersistance(TransactionRepository transactionRepository,
                            AlerteRepository alerteRepository,
                            ServiceAudit serviceAudit,
                            ServiceEmail serviceEmail,
                            ServiceNotificationWebSocket serviceNotificationWebSocket) {
        this.transactionRepository = transactionRepository;
        this.alerteRepository = alerteRepository;
        this.serviceAudit = serviceAudit;
        this.serviceEmail = serviceEmail;
        this.serviceNotificationWebSocket = serviceNotificationWebSocket;
    }

    // Exécution de l'étape

    /**
     * Exécute le Stage 5 — Persistance et finalisation.
     * <p>
     * Sauvegarde la transaction, génère les alertes, construit la piste
     * d'audit et déclenche les notifications.
     * </p>
     *
     * @param contexte le contexte du pipeline contenant la transaction finale
     */
    @Transactional
    public void executer(ContextePipeline contexte) {
        Transaction transaction = contexte.getTransaction();
        log.debug("Stage 5 — Persistance de la transaction : {}",
                transaction.getReferenceTransaction() != null ? transaction.getReferenceTransaction() : "nouvelle");

        try {
            // 1. Marquer la date de traitement
            transaction.setTraiteLe(LocalDateTime.now());

            // 2. Sauvegarder la transaction
            Transaction transactionSauvegardee = transactionRepository.save(transaction);
            contexte.setTransaction(transactionSauvegardee);
            log.debug("Transaction sauvegardée — ID: {}, Réf: {}, Statut: {}",
                    transactionSauvegardee.getId(),
                    transactionSauvegardee.getReferenceTransaction(),
                    transactionSauvegardee.getStatut());

            // 3. Générer les alertes pour les règles déclenchées
            List<Alerte> alertes = genererAlertes(contexte, transactionSauvegardee);

            // 4. Sauvegarder les alertes
            if (!alertes.isEmpty()) {
                alertes = alerteRepository.saveAll(alertes);
                contexte.setAlertesGenerees(alertes);
                log.info("{} alerte(s) générée(s) pour la transaction {}",
                        alertes.size(), transactionSauvegardee.getReferenceTransaction());
            }

            // 5. Notification WebSocket — alerter les superviseurs en temps réel
            envoyerNotificationWebSocket(transactionSauvegardee);

            // 6. Enregistrer l'entrée d'audit PERSISTANCE
            enregistrerAuditPersistance(transactionSauvegardee, alertes);

            // 7. Envoyer les emails d'alerte (asynchrone)
            envoyerEmailsAlertes(alertes, transactionSauvegardee);

            // 8. Envoyer une notification si un disjoncteur s'est ouvert
            notifierOuvertureDisjoncteur(contexte, transactionSauvegardee);

            // Succès
            contexte.setPersistanceReussie(true);
            contexte.terminer();
            log.info("Stage 5 réussi — Transaction finalisée : {} ({}) — {} alerte(s)",
                    transactionSauvegardee.getReferenceTransaction(),
                    transactionSauvegardee.getStatut().name(),
                    alertes.size());

        } catch (Exception e) {
            log.error("Stage 5 — Erreur lors de la persistance : {}", e.getMessage(), e);
            contexte.interrompre("PERSISTANCE", "Erreur lors de la sauvegarde : " + e.getMessage());
        }
    }

    // Génération des alertes

    /**
     * Génère les entités {@link Alerte} pour chaque règle déclenchée.
     *
     * @param contexte     le contexte du pipeline
     * @param transaction  la transaction sauvegardée
     * @return la liste des alertes générées
     */
    private List<Alerte> genererAlertes(ContextePipeline contexte, Transaction transaction) {
        List<RegleDeclenchee> reglesDeclenchees = contexte.getReglesDeclenchees();

        if (reglesDeclenchees == null || reglesDeclenchees.isEmpty()) {
            return new ArrayList<>();
        }

        List<Alerte> alertes = new ArrayList<>();

        for (RegleDeclenchee regleDeclenchee : reglesDeclenchees) {
            Alerte alerte = Alerte.builder()
                    .transaction(transaction)
                    .regle(regleDeclenchee.getRegle())
                    .message(regleDeclenchee.getMessage())
                    .niveau(mapperSeverite(regleDeclenchee.getRegle().getSeverite()))
                    .acquittee(false)
                    .emailEnvoye(false)
                    .build();

            alertes.add(alerte);
        }

        return alertes;
    }

    /**
     * Mappe la sévérité d'une règle vers le niveau d'alerte correspondant.
     *
     * @param severite la sévérité de la règle
     * @return le niveau d'alerte
     */
    private NiveauAlerte mapperSeverite(com.bna.flux.entity.Regle.Severite severite) {
        return switch (severite) {
            case FAIBLE -> NiveauAlerte.FAIBLE;
            case MOYEN -> NiveauAlerte.MOYEN;
            case ELEVE -> NiveauAlerte.ELEVE;
            case CRITIQUE -> NiveauAlerte.CRITIQUE;
        };
    }

    // Piste d'audit

    /**
     * Enregistre l'entrée d'audit pour l'étape de persistance.
     *
     * @param transaction la transaction sauvegardée
     * @param alertes     les alertes générées
     */
    private void enregistrerAuditPersistance(Transaction transaction, List<Alerte> alertes) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("statutFinal", transaction.getStatut().name());
            details.put("scoreRisque", transaction.getScoreRisque());
            details.put("nombreAlertes", alertes.size());
            details.put("referenceTransaction", transaction.getReferenceTransaction());

            if (!alertes.isEmpty()) {
                List<String> nomsRegles = alertes.stream()
                        .map(a -> a.getRegle() != null ? a.getRegle().getNom() : "Inconnue")
                        .toList();
                details.put("reglesDeclenchees", nomsRegles);
            }

            serviceAudit.enregistrer(
                    transaction,
                    "PERSISTANCE",
                    "TRANSACTION_" + transaction.getStatut().name(),
                    details,
                    "SYSTEME"
            );

            log.debug("Entrée d'audit PERSISTANCE enregistrée pour la transaction {}",
                    transaction.getReferenceTransaction());

        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de l'audit PERSISTANCE : {}", e.getMessage(), e);
            // Ne pas bloquer le pipeline si l'audit échoue
        }
    }

    // Envoi d'emails (asynchrone)

    /**
     * Déclenche l'envoi d'emails pour les alertes générées.
     * <p>
     * Les alertes CRITIQUE sont envoyées immédiatement.
     * Les alertes ELEVE seront envoyées par la tâche groupée (toutes les 15 min).
     * </p>
     *
     * @param alertes     les alertes générées
     * @param transaction la transaction concernée
     */
    private void envoyerEmailsAlertes(List<Alerte> alertes, Transaction transaction) {
        if (alertes == null || alertes.isEmpty()) {
            return;
        }

        try {
            for (Alerte alerte : alertes) {
                if (alerte.necessiteEmailImmediat()) {
                    // Envoi immédiat pour les alertes CRITIQUE
                    serviceEmail.envoyerEmailImmediat(alerte, transaction);
                    log.debug("Email immédiat déclenché pour l'alerte CRITIQUE — Transaction {}",
                            transaction.getReferenceTransaction());
                }
                // Les alertes ELEVE seront traitées par la tâche planifiée
                // ServiceEmail.estEligibleEmailGroupe() sera vérifié lors du batch
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi des emails d'alerte : {}", e.getMessage(), e);
            // Ne pas bloquer le pipeline si l'email échoue
        }
    }

    /**
     * Envoie une notification si un disjoncteur s'est ouvert suite à cette transaction.
     *
     * @param contexte    le contexte du pipeline
     * @param transaction la transaction
     */
    private void notifierOuvertureDisjoncteur(ContextePipeline contexte, Transaction transaction) {
        // Vérifier si la transaction a été bloquée et si le score est élevé
        if (transaction.estBloquee() && contexte.getScoreRisque() >= 70) {
            try {
                // Notifier pour le RIB source
                if (transaction.getRibSource() != null) {
                    serviceEmail.envoyerNotificationDisjoncteur(
                            "COMPTE_SOURCE",
                            transaction.getRibSource(),
                            contexte.getScoreRisque()
                    );
                }

                // Notifier pour le RIB destination
                if (transaction.getRibDestination() != null) {
                    serviceEmail.envoyerNotificationDisjoncteur(
                            "COMPTE_DESTINATION",
                            transaction.getRibDestination(),
                            contexte.getScoreRisque()
                    );
                }
            } catch (Exception e) {
                log.debug("Erreur lors de l'envoi de la notification disjoncteur : {}", e.getMessage());
            }
        }
    }

    /**
     * Envoie une notification WebSocket selon le statut de la transaction.
     *
     * @param transaction la transaction sauvegardée
     */
    private void envoyerNotificationWebSocket(Transaction transaction) {
        try {
            NotificationWebSocket notification = switch (transaction.getStatut()) {
                case BLOQUE -> NotificationWebSocket.transactionBloquee(
                        transaction.getId(),
                        transaction.getScoreRisque().intValue(),
                        transaction.getReferenceTransaction());
                case SURVEILLE -> NotificationWebSocket.transactionSurveillee(
                        transaction.getId(),
                        transaction.getScoreRisque().intValue(),
                        transaction.getReferenceTransaction());
                default -> null;
            };

            if (notification != null) {
                serviceNotificationWebSocket.diffuserAlerte(notification);
            }
        } catch (Exception e) {
            log.warn("Erreur lors de l'envoi de la notification WebSocket : {}", e.getMessage());
        }
    }

}
