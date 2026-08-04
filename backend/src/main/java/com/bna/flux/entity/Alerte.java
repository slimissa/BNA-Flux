package com.bna.flux.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant une alerte générée lorsqu'une règle de surveillance
 * est déclenchée lors du passage d'une transaction dans le pipeline.
 * <p>
 * Une alerte est toujours liée à une transaction et à la règle qui l'a
 * déclenchée. Elle peut être acquittée par un opérateur après revue manuelle.
 * </p>
 *
 * <p><b>Règles de gestion :</b></p>
 * <ul>
 *   <li>Une alerte ne peut pas exister sans transaction ni règle associée.</li>
 *   <li>La sévérité de l'alerte est héritée de la règle déclenchée.</li>
 *   <li>L'acquittement est une action manuelle tracée dans l'audit.</li>
 *   <li>Les alertes CRITIQUE génèrent un email immédiat.</li>
 *   <li>Les alertes ELEVE sont incluses dans le lot d'emails groupés (15 min).</li>
 *   <li>Les alertes FAIBLE et MOYEN n'envoient pas d'email.</li>
 * </ul>
 *
 * <p><b>Relations :</b></p>
 * <ul>
 *   <li>{@code @ManyToOne Transaction} — Transaction ayant déclenché l'alerte.</li>
 *   <li>{@code @ManyToOne Regle} — Règle qui a été déclenchée.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Entity
@Table(name = "alertes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alerte {

    /**
     * Identifiant unique généré automatiquement.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Transaction ayant déclenché cette alerte.
     * <p>
     * Relation obligatoire. Chargée en EAGER car toujours affichée
     * avec l'alerte (référence transaction, montant, statut).
     * </p>
     */
    @NotNull(message = "La transaction est obligatoire")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    /**
     * Règle qui a été déclenchée et a généré cette alerte.
     * <p>
     * Relation obligatoire. Chargée en EAGER car toujours affichée
     * avec l'alerte (nom de la règle, sévérité, catégorie).
     * </p>
     */
    @NotNull(message = "La règle est obligatoire")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "regle_id", nullable = false)
    private Regle regle;

    /**
     * Message descriptif de l'alerte.
     * <p>
     * Généré automatiquement à partir du nom de la règle et des détails
     * de la transaction. Exemple : "Virement international > 50k TND —
     * Transaction de 75 000,00 EUR vers FR763000..."
     * </p>
     */
    @NotBlank(message = "Le message d'alerte est obligatoire")
    @Size(max = 500, message = "Le message ne doit pas dépasser 500 caractères")
    @Column(name = "message", length = 500, nullable = false)
    private String message;

    /**
     * Niveau de sévérité de l'alerte.
     * <p>
     * Hérité de la règle déclenchée. Détermine le mode de notification
     * et l'urgence de traitement.
     * </p>
     */
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Le niveau d'alerte est obligatoire")
    @Column(name = "niveau", length = 10, nullable = false)
    private NiveauAlerte niveau;

    /**
     * Date et heure de déclenchement de l'alerte.
     * <p>
     * Correspond au moment où la règle a été évaluée dans le pipeline,
     * pas au moment où l'alerte a été consultée.
     * </p>
     */
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * Indique si l'alerte a été acquittée par un opérateur.
     * <p>
     * L'acquittement signifie qu'un humain a pris connaissance de l'alerte
     * et a décidé de l'action à prendre. Cela ne supprime pas l'alerte
     * mais la marque comme traitée.
     * </p>
     */
    @Column(name = "acquittee", nullable = false)
    @Builder.Default
    private boolean acquittee = false;

    /**
     * Identifiant de l'utilisateur ayant acquitté l'alerte.
     * <p>
     * Null tant que l'alerte n'a pas été acquittée.
     * </p>
     */
    @Size(max = 150)
    @Column(name = "acquittee_par", length = 150)
    private String acquitteePar;

    /**
     * Date et heure de l'acquittement.
     */
    @Column(name = "acquittee_le")
    private LocalDateTime acquitteeLe;

    /**
     * Indique si un email a été envoyé pour cette alerte.
     */
    @Column(name = "email_envoye", nullable = false)
    @Builder.Default
    private boolean emailEnvoye = false;

    /**
     * Date et heure d'envoi de l'email.
     */
    @Column(name = "email_envoye_le")
    private LocalDateTime emailEnvoyeLe;

    /**
     * Destinataire de l'email d'alerte.
     * <p>
     * Déterminé par la configuration des alertes et la sévérité.
     * </p>
     */
    @Size(max = 255)
    @Column(name = "email_destinataire", length = 255)
    private String emailDestinataire;

    // Callbacks JPA

    /**
     * Initialise la date de création avant la première persistance.
     */
    @PrePersist
    protected void avantCreation() {
        this.dateCreation = LocalDateTime.now();
    }

    // Enum interne

    /**
     * Niveaux de sévérité des alertes.
     */
    public enum NiveauAlerte {
        /**
         * Information — pas d'action immédiate requise.
         */
        FAIBLE,

        /**
         * Surveillance — revue recommandée dans la journée.
         */
        MOYEN,

        /**
         * Alerte — action requise dans l'heure.
         */
        ELEVE,

        /**
         * Critique — action immédiate requise, email envoyé instantanément.
         */
        CRITIQUE
    }

    // Méthodes métier

    /**
     * Acquitte l'alerte avec l'identifiant de l'opérateur.
     *
     * @param operateur identifiant de l'utilisateur qui acquitte
     * @throws IllegalStateException si l'alerte est déjà acquittée
     */
    public void acquitter(String operateur) {
        if (this.acquittee) {
            throw new IllegalStateException("L'alerte a déjà été acquittée");
        }
        this.acquittee = true;
        this.acquitteePar = operateur;
        this.acquitteeLe = LocalDateTime.now();
    }

    /**
     * Marque l'email comme envoyé avec la date et le destinataire.
     *
     * @param destinataire l'adresse email du destinataire
     */
    public void marquerEmailEnvoye(String destinataire) {
        this.emailEnvoye = true;
        this.emailEnvoyeLe = LocalDateTime.now();
        this.emailDestinataire = destinataire;
    }

    /**
     * Vérifie si cette alerte doit déclencher un email immédiat.
     *
     * @return {@code true} si le niveau est CRITIQUE
     */
    public boolean necessiteEmailImmediat() {
        return niveau == NiveauAlerte.CRITIQUE && !emailEnvoye;
    }

    /**
     * Vérifie si cette alerte est éligible à l'envoi groupé d'emails.
     *
     * @return {@code true} si le niveau est ELEVE et l'email n'a pas encore été envoyé
     */
    public boolean estEligibleEmailGroupe() {
        return niveau == NiveauAlerte.ELEVE && !emailEnvoye;
    }

    /**
     * Vérifie si cette alerte nécessite une action humaine.
     *
     * @return {@code true} si non acquittée et de niveau ELEVE ou CRITIQUE
     */
    public boolean necessiteAction() {
        return !acquittee && (niveau == NiveauAlerte.ELEVE || niveau == NiveauAlerte.CRITIQUE);
    }

    /**
     * Calcule le délai depuis le déclenchement de l'alerte.
     *
     * @return le nombre de minutes écoulées depuis la création
     */
    public long getDelaiMinutes() {
        if (dateCreation == null) {
            return 0;
        }
        return java.time.Duration.between(dateCreation, LocalDateTime.now()).toMinutes();
    }
}