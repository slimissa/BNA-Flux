package com.bna.flux.service;

import com.bna.flux.entity.Alerte;
import com.bna.flux.entity.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Implémentation du service d'email pour l'environnement de développement.
 * <p>
 * Au lieu d'envoyer de véritables emails via SMTP, cette implémentation
 * loggue le contenu des emails dans la console. Active uniquement lorsque
 * le profil {@code dev} est actif.
 * </p>
 *
 * <p><b>Activation :</b></p>
 * <pre>
 * spring.profiles.active: dev
 * bna.alertes.email.mode-console: true
 * </pre>
 *
 * <p><b>Format de sortie console :</b></p>
 * <pre>
 * ╔═══════════════════════════════════════════════════════════╗
 * ║  EMAIL SIMULÉ — Alerte CRITIQUE                          ║
 * ╠═══════════════════════════════════════════════════════════╣
 * ║  De : bna-flux@bna.com.tn                                ║
 * ║  À  : surveillance@bna.com.tn                            ║
 * ║  Sujet : [BNA-FLUX] Alerte CRITIQUE — Règle X            ║
 * ╠═══════════════════════════════════════════════════════════╣
 * ║  Transaction : BNA-20260804-0001                         ║
 * ║  Montant : 75 000,00 EUR                                 ║
 * ║  ...                                                     ║
 * ╚═══════════════════════════════════════════════════════════╝
 * </pre>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Service
@Profile("dev")
public class ServiceEmailConsole implements ServiceEmail {

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String SEPARATEUR = "═══════════════════════════════════════════════════════════";
    private static final String EXPEDITEUR = "bna-flux@bna.com.tn";
    private static final String DESTINATAIRE_DEFAUT = "surveillance@bna.com.tn";

    // Implémentation

    /**
     * Simule l'envoi immédiat d'un email pour une alerte CRITIQUE.
     * <p>
     * Exécuté de manière asynchrone pour ne pas bloquer le pipeline.
     * </p>
     *
     * @param alerte      l'alerte CRITIQUE
     * @param transaction la transaction concernée
     */
    @Override
    @Async("emailExecutor")
    public void envoyerEmailImmediat(Alerte alerte, Transaction transaction) {
        String destinataire = alerte.getEmailDestinataire() != null
                ? alerte.getEmailDestinataire()
                : DESTINATAIRE_DEFAUT;

        log.info("\n" +
                "╔" + SEPARATEUR + "╗\n" +
                "║  EMAIL SIMULÉ — Alerte CRITIQUE — Envoi immédiat       ║\n" +
                "╠" + SEPARATEUR + "╣\n" +
                "║  De    : " + padRight(EXPEDITEUR, 50) + "║\n" +
                "║  À     : " + padRight(destinataire, 50) + "║\n" +
                "║  Sujet : " + padRight("[BNA-FLUX] Alerte CRITIQUE — " + 
                        (alerte.getRegle() != null ? alerte.getRegle().getNom() : "Règle inconnue"), 50) + "║\n" +
                "╠" + SEPARATEUR + "╣\n" +
                "║  Une alerte de niveau CRITIQUE a été déclenchée.        ║\n" +
                "║                                                         ║\n" +
                formatLigne("Transaction", transaction.getReferenceTransaction()) +
                formatLigne("Règle", alerte.getRegle() != null ? alerte.getRegle().getNom() : "N/A") +
                formatLigne("Montant", transaction.getMontant() + " " + 
                        (transaction.getDevise() != null ? transaction.getDevise().getCode() : "N/A")) +
                formatLigne("Date", transaction.getDateTransaction() != null 
                        ? transaction.getDateTransaction().format(FORMAT_DATE) : "N/A") +
                formatLigne("Statut", transaction.getStatut() != null 
                        ? transaction.getStatut().name() : "N/A") +
                "║                                                         ║\n" +
                "║  Veuillez prendre les mesures nécessaires.              ║\n" +
                "╚" + SEPARATEUR + "╝"
        );

        // Marquer l'alerte comme envoyée
        alerte.marquerEmailEnvoye(destinataire);
    }

    /**
     * Simule l'envoi d'un email groupé pour plusieurs alertes ELEVE.
     * <p>
     * Appelé périodiquement par la tâche planifiée toutes les 15 minutes.
     * </p>
     *
     * @param alertes     la liste des alertes à inclure
     * @param destinataire l'adresse du destinataire
     */
    @Override
    @Async("emailExecutor")
    public void envoyerEmailGroupe(List<Alerte> alertes, String destinataire) {
        if (alertes == null || alertes.isEmpty()) {
            log.debug("Aucune alerte à envoyer — lot vide");
            return;
        }

        String dest = destinataire != null ? destinataire : DESTINATAIRE_DEFAUT;
        int nombreAlertes = alertes.size();

        StringBuilder contenu = new StringBuilder();
        contenu.append("\n")
                .append("╔").append(SEPARATEUR).append("╗\n")
                .append("║  EMAIL SIMULÉ — Rapport groupé — ").append(nombreAlertes)
                .append(" alerte(s)").append(padRight("", 22 - String.valueOf(nombreAlertes).length())).append("║\n")
                .append("╠").append(SEPARATEUR).append("╣\n")
                .append("║  De    : ").append(padRight(EXPEDITEUR, 50)).append("║\n")
                .append("║  À     : ").append(padRight(dest, 50)).append("║\n")
                .append("║  Sujet : ").append(padRight("[BNA-FLUX] Rapport d'alertes — " + nombreAlertes + " alerte(s) ELEVE", 50)).append("║\n")
                .append("╠").append(SEPARATEUR).append("╣\n");

        for (int i = 0; i < alertes.size(); i++) {
            Alerte alerte = alertes.get(i);
            Transaction transaction = alerte.getTransaction();

            contenu.append("║  Alerte #").append(i + 1).append("\n");
            if (alerte.getRegle() != null) {
                contenu.append(formatLigne("   Règle", alerte.getRegle().getNom()));
            }
            if (transaction != null) {
                contenu.append(formatLigne("   Transaction", transaction.getReferenceTransaction()));
                contenu.append(formatLigne("   Montant", transaction.getMontant() + " " +
                        (transaction.getDevise() != null ? transaction.getDevise().getCode() : "")));
                contenu.append(formatLigne("   Statut", transaction.getStatut() != null
                        ? transaction.getStatut().name() : "N/A"));
            }
            if (i < alertes.size() - 1) {
                contenu.append("║  ───────────────────────────────────────────────────── ║\n");
            }
        }

        contenu.append("╠").append(SEPARATEUR).append("╣\n")
                .append("║  ").append(nombreAlertes).append(" alerte(s) en attente de revue.            ║\n")
                .append("╚").append(SEPARATEUR).append("╝");

        log.info(contenu.toString());

        // Marquer toutes les alertes comme envoyées
        for (Alerte alerte : alertes) {
            alerte.marquerEmailEnvoye(dest);
        }
    }

    /**
     * Simule l'envoi d'une notification d'ouverture de disjoncteur.
     *
     * @param typeCible        le type de cible
     * @param identifiantCible l'identifiant de la cible
     * @param nombreEchecs     le nombre d'échecs
     */
    @Override
    @Async("emailExecutor")
    public void envoyerNotificationDisjoncteur(String typeCible, String identifiantCible, int nombreEchecs) {
        log.info("\n" +
                "╔" + SEPARATEUR + "╗\n" +
                "║  EMAIL SIMULÉ — Notification disjoncteur               ║\n" +
                "╠" + SEPARATEUR + "╣\n" +
                "║  De    : " + padRight(EXPEDITEUR, 50) + "║\n" +
                "║  À     : " + padRight(DESTINATAIRE_DEFAUT, 50) + "║\n" +
                "║  Sujet : " + padRight("[BNA-FLUX] Disjoncteur OUVERT — " + typeCible + " " + identifiantCible, 50) + "║\n" +
                "╠" + SEPARATEUR + "╣\n" +
                "║  Un disjoncteur a été ouvert automatiquement.          ║\n" +
                "║                                                         ║\n" +
                formatLigne("Type", typeCible) +
                formatLigne("Identifiant", identifiantCible) +
                formatLigne("Nombre d'échecs", String.valueOf(nombreEchecs)) +
                "║                                                         ║\n" +
                "║  Veuillez vérifier la situation et réinitialiser       ║\n" +
                "║  le disjoncteur si nécessaire.                         ║\n" +
                "╚" + SEPARATEUR + "╝"
        );
    }

    /**
     * Vérifie si le service email est opérationnel.
     * <p>
     * En mode console, le service est toujours considéré comme opérationnel
     * puisqu'aucune connexion réseau n'est nécessaire.
     * </p>
     *
     * @return toujours {@code true}
     */
    @Override
    public boolean estOperationnel() {
        return true;
    }

    /**
     * Retourne le type d'implémentation active.
     *
     * @return "CONSOLE"
     */
    @Override
    public String getTypeImplementation() {
        return "CONSOLE";
    }

    // Méthodes privées utilitaires

    /**
     * Formate une ligne pour l'affichage dans la boîte email simulée.
     *
     * @param etiquette l'étiquette (ex: "Transaction")
     * @param valeur    la valeur associée
     * @return la ligne formatée
     */
    private String formatLigne(String etiquette, String valeur) {
        String ligne = "║  " + etiquette + " : " + (valeur != null ? valeur : "N/A");
        return padRight(ligne, 57) + "║\n";
    }

    /**
     * Complète une chaîne avec des espaces jusqu'à la longueur spécifiée.
     *
     * @param texte    le texte à compléter
     * @param longueur la longueur cible
     * @return le texte complété à droite avec des espaces
     */
    private String padRight(String texte, int longueur) {
        if (texte == null) {
            texte = "";
        }
        if (texte.length() >= longueur) {
            return texte.substring(0, longueur);
        }
        return texte + " ".repeat(longueur - texte.length());
    }
}