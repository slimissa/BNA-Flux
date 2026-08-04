package com.bna.flux.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant un disjoncteur (Circuit Breaker) dans le système BNA-FLUX.
 * <p>
 * Le disjoncteur est un mécanisme de protection automatique qui bloque
 * les transactions lorsqu'un nombre anormal d'échecs ou de blocages est
 * détecté pour une cible spécifique (compte source, compte destination,
 * agence, ou canal).
 * </p>
 *
 * <p><b>Cycle de vie du disjoncteur :</b></p>
 * <pre>
 *     ┌──────────┐
 *     │  FERMÉ   │  (opération normale)
 *     └─────┬────┘
 *           │ nombreEchecs >= seuilEchecs dans la fenêtre de temps
 *           ▼
 *     ┌──────────┐
 *     │  OUVERT  │  (toutes les transactions sont bloquées)
 *     └─────┬────┘
 *           │ délaiOuverture écoulé
 *           ▼
 *     ┌────────────┐
 *     │  MI_OUVERT │  (une transaction test est autorisée)
 *     └─────┬──────┘
 *           │ test réussi → FERMÉ
 *           │ test échoué → OUVERT
 *           ▼
 *     ┌──────────┐
 *     │  FERMÉ   │  (retour à la normale)
 *     └──────────┘
 * </pre>
 *
 * <p><b>Règles de gestion :</b></p>
 * <ul>
 *   <li>Un disjoncteur est identifié de manière unique par le couple
 *       (typeCible, identifiantCible).</li>
 *   <li>Le nombre d'échecs est comptabilisé dans une fenêtre glissante
 *       configurable (défaut : 24 heures).</li>
 *   <li>Quand le disjoncteur est OUVERT, toutes les transactions correspondant
 *       à la cible sont bloquées au Stage 1 du pipeline.</li>
 *   <li>La transition MI_OUVERT → FERMÉ ou OUVERT dépend du résultat
 *       de la première transaction test.</li>
 *   <li>Un SUPERVISEUR ou ADMIN peut réinitialiser manuellement un disjoncteur.</li>
 * </ul>
 *
 * <p><b>Types de cibles supportés :</b></p>
 * <ul>
 *   <li>{@code COMPTE_SOURCE} — RIB émetteur</li>
 *   <li>{@code COMPTE_DESTINATION} — RIB bénéficiaire</li>
 *   <li>{@code AGENCE} — Code agence (3 chiffres)</li>
 *   <li>{@code CANAL} — Canal de transaction (EN_LIGNE, MOBILE, etc.)</li>
 * </ul>
 *
 * <p><b>Relations :</b></p>
 * <ul>
 *   <li>Aucune relation JPA directe. Le disjoncteur est consulté par
 *       {@link com.bna.flux.service.ServiceDisjoncteur} lors du Stage 1
 *       (Validation) et Stage 4 (Notation) du pipeline.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Entity
@Table(name = "etats_disjoncteur")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtatDisjoncteur {

    /**
     * Identifiant unique généré automatiquement.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nom descriptif du disjoncteur pour l'interface d'administration.
     * <p>
     * Exemples : "Compte source 08601000191000748054",
     * "Agence 601", "Canal EN_LIGNE".
     * </p>
     */
    @Size(max = 200, message = "Le nom ne doit pas dépasser 200 caractères")
    @Column(name = "nom", length = 200)
    private String nom;

    /**
     * Type de cible surveillée par ce disjoncteur.
     */
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Le type de cible est obligatoire")
    @Column(name = "type_cible", length = 25, nullable = false)
    private TypeCible typeCible;

    /**
     * Identifiant de la cible surveillée.
     * <p>
     * Format dépend du type de cible :
     * </p>
     * <ul>
     *   <li>COMPTE_SOURCE, COMPTE_DESTINATION : RIB 20 chiffres</li>
     *   <li>AGENCE : code agence 3 chiffres</li>
     *   <li>CANAL : EN_LIGNE, MOBILE, DAB, AGENCE</li>
     * </ul>
     */
    @NotBlank(message = "L'identifiant de la cible est obligatoire")
    @Size(max = 100, message = "L'identifiant ne doit pas dépasser 100 caractères")
    @Column(name = "identifiant_cible", length = 100, nullable = false)
    private String identifiantCible;

    /**
     * État actuel du disjoncteur.
     *
     * <ul>
     *   <li>{@code FERME} — Fonctionnement normal, transactions autorisées.</li>
     *   <li>{@code OUVERT} — Blocage actif, toutes les transactions sont rejetées.</li>
     *   <li>{@code MI_OUVERT} — Test en cours, une transaction autorisée pour évaluer
     *       si le problème est résolu.</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @NotNull(message = "L'état est obligatoire")
    @Column(name = "etat", length = 15, nullable = false)
    @Builder.Default
    private Etat etat = Etat.FERME;

    /**
     * Nombre d'échecs consécutifs dans la fenêtre de temps courante.
     * <p>
     * Incrémenté à chaque transaction BLOQUE pour cette cible.
     * Réinitialisé quand le disjoncteur repasse à l'état FERME.
     * </p>
     */
    @Min(value = 0, message = "Le nombre d'échecs ne peut pas être négatif")
    @Column(name = "nombre_echecs", nullable = false)
    @Builder.Default
    private int nombreEchecs = 0;

    /**
     * Seuil d'échecs à partir duquel le disjoncteur s'ouvre.
     * <p>
     * Valeur par défaut : 3 (configurable dans application.yml).
     * Quand {@code nombreEchecs >= seuilEchecs}, le disjoncteur passe à OUVERT.
     * </p>
     */
    @Min(value = 1, message = "Le seuil d'échecs doit être au moins 1")
    @Column(name = "seuil_echecs", nullable = false)
    @Builder.Default
    private int seuilEchecs = 3;

    /**
     * Durée pendant laquelle le disjoncteur reste OUVERT avant de passer
     * en MI_OUVERT, en minutes.
     * <p>
     * Valeur par défaut : 60 minutes (configurable dans application.yml).
     * </p>
     */
    @Min(value = 1, message = "Le délai d'ouverture doit être au moins 1 minute")
    @Column(name = "delai_ouverture_minutes", nullable = false)
    @Builder.Default
    private int delaiOuvertureMinutes = 60;

    /**
     * Fenêtre de temps glissante pour le comptage des échecs, en heures.
     * <p>
     * Les échecs antérieurs à cette fenêtre ne sont pas comptabilisés.
     * Valeur par défaut : 24 heures.
     * </p>
     */
    @Min(value = 1, message = "La fenêtre de temps doit être au moins 1 heure")
    @Column(name = "fenetre_heures", nullable = false)
    @Builder.Default
    private int fenetreHeures = 24;

    /**
     * Date et heure de la dernière ouverture du disjoncteur.
     * <p>
     * {@code null} si le disjoncteur n'a jamais été ouvert.
     * </p>
     */
    @Column(name = "date_derniere_ouverture")
    private LocalDateTime dateDerniereOuverture;

    /**
     * Date et heure de la dernière fermeture (retour à la normale).
     * <p>
     * {@code null} si le disjoncteur n'a jamais été fermé après ouverture.
     * </p>
     */
    @Column(name = "date_derniere_fermeture")
    private LocalDateTime dateDerniereFermeture;

    /**
     * Date et heure du dernier échec enregistré.
     */
    @Column(name = "date_dernier_echec")
    private LocalDateTime dateDernierEchec;

    /**
     * Date de création du disjoncteur.
     */
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * Date de la dernière modification.
     */
    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    // Callbacks JPA

    @PrePersist
    protected void avantCreation() {
        this.dateCreation = LocalDateTime.now();
        this.dateModification = LocalDateTime.now();
        if (this.etat == null) {
            this.etat = Etat.FERME;
        }
    }

    @PreUpdate
    protected void avantModification() {
        this.dateModification = LocalDateTime.now();
    }

    // Enums internes

    /**
     * Types de cibles pour les disjoncteurs.
     */
    public enum TypeCible {
        /** Blocage basé sur le RIB émetteur (source). */
        COMPTE_SOURCE,

        /** Blocage basé sur le RIB bénéficiaire (destination). */
        COMPTE_DESTINATION,

        /** Blocage basé sur le code agence. */
        AGENCE,

        /** Blocage basé sur le canal de transaction. */
        CANAL
    }

    /**
     * États possibles du disjoncteur.
     */
    public enum Etat {
        /**
         * Fonctionnement normal — les transactions sont traitées.
         */
        FERME,

        /**
         * Blocage actif — toutes les transactions sont rejetées au Stage 1.
         */
        OUVERT,

        /**
         * Test — une transaction autorisée pour évaluer le retour à la normale.
         */
        MI_OUVERT
    }

    // Méthodes métier — Transitions d'état

    /**
     * Enregistre un échec et vérifie si le seuil est atteint.
     * <p>
     * Si le nombre d'échecs atteint ou dépasse le seuil, le disjoncteur
     * passe à l'état OUVERT.
     * </p>
     *
     * @return {@code true} si le disjoncteur vient de s'ouvrir
     */
    public boolean enregistrerEchec() {
        this.nombreEchecs++;
        this.dateDernierEchec = LocalDateTime.now();

        if (this.nombreEchecs >= this.seuilEchecs && this.etat == Etat.FERME) {
            this.etat = Etat.OUVERT;
            this.dateDerniereOuverture = LocalDateTime.now();
            return true;
        }
        return false;
    }

    /**
     * Tente un passage de MI_OUVERT à FERMÉ après un test réussi.
     *
     * @return {@code true} si la transition a eu lieu
     */
    public boolean testReussi() {
        if (this.etat == Etat.MI_OUVERT) {
            reinitialiser();
            return true;
        }
        return false;
    }

    /**
     * Enregistre un échec du test en MI_OUVERT — retour à OUVERT.
     *
     * @return {@code true} si la transition a eu lieu
     */
    public boolean testEchoue() {
        if (this.etat == Etat.MI_OUVERT) {
            this.etat = Etat.OUVERT;
            this.nombreEchecs++;
            this.dateDernierEchec = LocalDateTime.now();
            this.dateDerniereOuverture = LocalDateTime.now();
            return true;
        }
        return false;
    }

    /**
     * Tente un passage automatique de OUVERT à MI_OUVERT si le délai est écoulé.
     *
     * @return {@code true} si la transition a eu lieu
     */
    public boolean tenterPassageMiOuvert() {
        if (this.etat == Etat.OUVERT && dateDerniereOuverture != null) {
            LocalDateTime delaiExpiration = dateDerniereOuverture.plusMinutes(delaiOuvertureMinutes);
            if (LocalDateTime.now().isAfter(delaiExpiration)) {
                this.etat = Etat.MI_OUVERT;
                return true;
            }
        }
        return false;
    }

    /**
     * Réinitialise manuellement le disjoncteur à l'état FERMÉ.
     * <p>
     * Action réservée aux SUPERVISEUR et ADMIN.
     * </p>
     */
    public void reinitialiser() {
        this.etat = Etat.FERME;
        this.nombreEchecs = 0;
        this.dateDerniereFermeture = LocalDateTime.now();
    }

    // Méthodes métier — Vérifications d'état

    /**
     * Vérifie si le disjoncteur bloque actuellement les transactions.
     *
     * @return {@code true} si l'état est OUVERT
     */
    public boolean estOuvert() {
        return etat == Etat.OUVERT;
    }

    /**
     * Vérifie si le disjoncteur est en phase de test.
     *
     * @return {@code true} si l'état est MI_OUVERT
     */
    public boolean estMiOuvert() {
        return etat == Etat.MI_OUVERT;
    }

    /**
     * Vérifie si les transactions sont autorisées.
     *
     * @return {@code true} si l'état est FERME ou MI_OUVERT
     */
    public boolean transactionsAutorisees() {
        return etat == Etat.FERME || etat == Etat.MI_OUVERT;
    }

    /**
     * Génère un nom descriptif pour ce disjoncteur.
     *
     * @return le nom généré
     */
    public String genererNom() {
        String typeLibelle = switch (typeCible) {
            case COMPTE_SOURCE -> "Compte source";
            case COMPTE_DESTINATION -> "Compte destination";
            case AGENCE -> "Agence";
            case CANAL -> "Canal";
        };
        return typeLibelle + " " + identifiantCible;
    }
}