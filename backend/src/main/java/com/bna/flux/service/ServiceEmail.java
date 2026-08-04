package com.bna.flux.service;

import com.bna.flux.entity.Alerte;
import com.bna.flux.entity.Transaction;

/**
 * Interface du service d'envoi d'emails d'alerte BNA-FLUX.
 * <p>
 * Définit le contrat pour l'envoi de notifications par email lors
 * du déclenchement d'alertes de surveillance. L'implémentation varie
 * selon l'environnement :
 * </p>
 * <ul>
 *   <li><b>Développement</b> — {@link ServiceEmailConsole} loggue les emails
 *       dans la console au lieu de les envoyer.</li>
 *   <li><b>Production</b> — Implémentation SMTP réelle avec connexion
 *       au serveur de messagerie de la BNA.</li>
 * </ul>
 *
 * <p><b>Stratégie d'envoi :</b></p>
 * <ul>
 *   <li><b>Alertes CRITIQUE</b> — Email immédiat, envoyé de manière asynchrone
 *       dès que l'alerte est créée dans le pipeline.</li>
 *   <li><b>Alertes ELEVE</b> — Email groupé toutes les 15 minutes via une
 *       tâche planifiée ({@code @Scheduled}).</li>
 *   <li><b>Alertes MOYEN et FAIBLE</b> — Pas d'email, consultation via
 *       le dashboard uniquement.</li>
 * </ul>
 *
 * <p><b>Format des emails :</b></p>
 * <pre>
 * Sujet : [BNA-FLUX] Alerte CRITIQUE — Virement international > 50k TND
 * Corps :
 * Une alerte de niveau CRITIQUE a été déclenchée.
 *
 * Transaction : BNA-20260804-0001
 * Règle : Virement international > 50k TND
 * Montant : 75 000,00 EUR
 * Date : 04/08/2026 09:15
 * Statut : BLOQUE
 *
 * Veuillez prendre les mesures nécessaires.
 * </pre>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
public interface ServiceEmail {

    /**
     * Envoie un email d'alerte immédiatement (utilisé pour les alertes CRITIQUE).
     * <p>
     * Appelé de manière asynchrone via {@code @Async("emailExecutor")}
     * depuis le Stage 5 (Persistance) du pipeline.
     * </p>
     *
     * @param alerte      l'alerte à notifier
     * @param transaction la transaction concernée
     */
    void envoyerEmailImmediat(Alerte alerte, Transaction transaction);

    /**
     * Envoie un lot d'alertes groupées dans un seul email.
     * <p>
     * Utilisé par la tâche planifiée pour les alertes ELEVE
     * accumulées dans les dernières 15 minutes.
     * </p>
     *
     * @param alertes     la liste des alertes à inclure dans l'email groupé
     * @param destinataire l'adresse email du destinataire
     */
    void envoyerEmailGroupe(java.util.List<Alerte> alertes, String destinataire);

    /**
     * Envoie un email de notification pour l'ouverture d'un disjoncteur.
     * <p>
     * Appelé lorsqu'un circuit breaker passe à l'état OUVERT,
     * pour informer les superviseurs qu'une action est requise.
     * </p>
     *
     * @param typeCible        le type de cible (COMPTE_SOURCE, AGENCE, etc.)
     * @param identifiantCible l'identifiant de la cible
     * @param nombreEchecs     le nombre d'échecs ayant déclenché l'ouverture
     */
    void envoyerNotificationDisjoncteur(String typeCible, String identifiantCible, int nombreEchecs);

    /**
     * Vérifie si le service email est correctement configuré et accessible.
     *
     * @return {@code true} si le service est opérationnel
     */
    boolean estOperationnel();

    /**
     * Retourne le type d'implémentation active (pour diagnostic).
     *
     * @return "SMTP" pour la production, "CONSOLE" pour le développement
     */
    String getTypeImplementation();
}