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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité centrale représentant une transaction bancaire soumise au pipeline
 * de surveillance BNA-FLUX.
 * <p>
 * Une transaction traverse cinq étapes de pipeline :
 * </p>
 * <ol>
 *   <li><b>Validation</b> — Vérification des RIBs, devise, montant, disjoncteurs</li>
 *   <li><b>Enrichissement</b> — Détermination du pays d'origine, catégorie contrepartie</li>
 *   <li><b>Évaluation des règles</b> — Exécution des règles SpEL actives</li>
 *   <li><b>Notation</b> — Calcul du score de risque, application des disjoncteurs</li>
 *   <li><b>Persistance</b> — Sauvegarde, génération des alertes, chaîne d'audit</li>
 * </ol>
 *
 * <p><b>Règles de gestion :</b></p>
 * <ul>
 *   <li>Le RIB tunisien est composé de 20 chiffres (banque 2, agence 3, compte 13, clé 2).</li>
 *   <li>La validation RIB utilise l'algorithme modulo 97 (clé = 97 - (N × 100 mod 97)).</li>
 *   <li>Le score de risque (0-100) détermine le statut final.</li>
 *   <li>Le versionnement optimiste (@Version) protège contre les conflits de mise à jour.</li>
 *   <li>La référence transaction est générée automatiquement au format BNA-YYYYMMDD-XXXX.</li>
 * </ul>
 *
 * <p><b>Relations :</b></p>
 * <ul>
 *   <li>{@code @ManyToOne Devise} — Devise de la transaction (obligatoire)</li>
 *   <li>{@code Alerte} → {@code @ManyToOne Transaction} — Alertes générées par cette transaction</li>
 *   <li>{@code EntreeAudit} → {@code @ManyToOne Transaction} — Piste d'audit de cette transaction</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    /**
     * Identifiant unique généré automatiquement.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Référence unique de la transaction.
     * <p>
     * Format : {@code BNA-YYYYMMDD-XXXX} où XXXX est un incrément quotidien.
     * Générée automatiquement avant la persistance.
     * </p>
     */
    @NotBlank(message = "La référence est obligatoire")
    @Size(max = 30, message = "La référence ne doit pas dépasser 30 caractères")
    @Pattern(regexp = "^BNA-\\d{8}-\\d{4}$", message = "Format de référence invalide")
    @Column(name = "reference_transaction", length = 30, nullable = false, unique = true)
    private String referenceTransaction;

    /**
     * RIB source de la transaction (émetteur).
     * <p>
     * Format tunisien : 20 chiffres (banque 2, agence 3, compte 13, clé 2).
     * Validé par {@link com.bna.flux.service.ValidateurRib} avant persistance.
     * </p>
     */
    @NotBlank(message = "Le RIB source est obligatoire")
    @Pattern(regexp = "^[0-9]{20}$", message = "Le RIB source doit contenir exactement 20 chiffres")
    @Column(name = "rib_source", length = 20, nullable = false)
    private String ribSource;

    /**
     * RIB destination de la transaction (bénéficiaire).
     * <p>
     * Format tunisien : 20 chiffres (banque 2, agence 3, compte 13, clé 2).
     * Validé par {@link com.bna.flux.service.ValidateurRib} avant persistance.
     * </p>
     */
    @NotBlank(message = "Le RIB destination est obligatoire")
    @Pattern(regexp = "^[0-9]{20}$", message = "Le RIB destination doit contenir exactement 20 chiffres")
    @Column(name = "rib_destination", length = 20, nullable = false)
    private String ribDestination;

    /**
     * Montant de la transaction dans l'unité principale de la devise.
     * <p>
     * Stocké en {@link BigDecimal} pour éviter les erreurs de précision
     * des types float/double. La précision (nombre de décimales) dépend
     * de la devise associée.
     * </p>
     */
    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.001", message = "Le montant doit être supérieur à zéro")
    @Digits(integer = 15, fraction = 3, message = "Le montant ne doit pas dépasser 15 chiffres entiers et 3 décimales")
    @Column(name = "montant", precision = 18, scale = 3, nullable = false)
    private BigDecimal montant;

    /**
     * Devise de la transaction.
     * <p>
     * Relation obligatoire vers l'entité {@link Devise}.
     * Chargée en EAGER car systématiquement utilisée (affichage, validation, enrichissement).
     * </p>
     */
    @NotNull(message = "La devise est obligatoire")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "code_devise", referencedColumnName = "code", nullable = false)
    private Devise devise;

    /**
     * Type de transaction bancaire.
     */
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Le type de transaction est obligatoire")
    @Column(name = "type_transaction", length = 15, nullable = false)
    private TypeTransaction typeTransaction;

    /**
     * Canal par lequel la transaction a été initiée.
     */
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Le canal est obligatoire")
    @Column(name = "canal", length = 10, nullable = false)
    private Canal canal;

    /**
     * Date et heure d'exécution de la transaction.
     * <p>
     * Correspond au moment où la transaction a été initiée dans le système bancaire,
     * pas au moment où elle a été traitée par le pipeline.
     * </p>
     */
    @NotNull(message = "La date d'exécution est obligatoire")
    @Column(name = "date_transaction", nullable = false)
    private LocalDateTime dateTransaction;

    /**
     * Description ou motif de la transaction.
     * <p>
     * Texte libre fourni par le système bancaire ou l'opérateur.
     * </p>
     */
    @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Pays d'origine de la transaction.
     * <p>
     * Déterminé lors de l'étape d'enrichissement (Stage 2).
     * </p>
     * <ul>
     *   <li>Pour un RIB tunisien avec devise TND → "Tunisie"</li>
     *   <li>Pour une devise étrangère → pays émetteur de la devise</li>
     *   <li>Peut être raffiné par des règles d'enrichissement ultérieures</li>
     * </ul>
     */
    @Size(max = 100, message = "Le pays d'origine ne doit pas dépasser 100 caractères")
    @Column(name = "pays_origine", length = 100)
    private String paysOrigine;

    /**
     * Catégorie de la contrepartie (émetteur ou bénéficiaire).
     * <p>
     * Déterminée lors de l'étape d'enrichissement (Stage 2).
     * </p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie_contrepartie", length = 20)
    private CategorieContrepartie categorieContrepartie;

    /**
     * Score de risque calculé (0.00 à 100.00).
     * <p>
     * Calculé lors de l'étape de notation (Stage 4) comme la somme
     * des contributions de toutes les règles déclenchées, plafonnée à 100.
     * </p>
     * <p>
     * Interprétation :
     * </p>
     * <ul>
     *   <li>0-29 : Risque faible → ACCEPTE</li>
     *   <li>30-70 : Risque modéré → SURVEILLE</li>
     *   <li>71-100 : Risque élevé → BLOQUE</li>
     * </ul>
     */
    @Column(name = "score_risque", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal scoreRisque = BigDecimal.ZERO;

    /**
     * Statut final de la transaction après passage dans le pipeline.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", length = 10, nullable = false)
    @Builder.Default
    private StatutTransaction statut = StatutTransaction.ACCEPTE;

    /**
     * Motif de rejet ou de surveillance.
     * <p>
     * Rempli lorsque le statut est SURVEILLE ou BLOQUE.
     * Contient la concaténation des messages des règles déclenchées.
     * </p>
     */
    @Size(max = 1000, message = "Le motif ne doit pas dépasser 1000 caractères")
    @Column(name = "motif", length = 1000)
    private String motif;

    /**
     * Date et heure de traitement par le pipeline.
     * <p>
     * Correspond au moment où la dernière étape du pipeline (Persistance)
     * a été exécutée.
     * </p>
     */
    @Column(name = "traite_le")
    private LocalDateTime traiteLe;

    /**
     * Version pour le locking optimiste.
     * <p>
     * Incrémenté automatiquement par Hibernate à chaque mise à jour.
     * Protège contre les conflits de modification concurrente.
     * </p>
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Date de création de l'enregistrement en base.
     */
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * Date de la dernière modification de l'enregistrement.
     */
    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    // Callbacks JPA

    @PrePersist
    protected void avantCreation() {
        this.dateCreation = LocalDateTime.now();
        this.dateModification = LocalDateTime.now();
        if (this.referenceTransaction == null) {
            this.referenceTransaction = genererReference();
        }
    }

    @PreUpdate
    protected void avantModification() {
        this.dateModification = LocalDateTime.now();
    }

    // Enums internes

    /**
     * Types de transactions bancaires supportés.
     */
    public enum TypeTransaction {
        /** Virement bancaire (domestique ou international). */
        VIREMENT,

        /** Chèque (dépôt ou émission). */
        CHEQUE,

        /** Dépôt ou retrait en espèces. */
        ESPECES,

        /** Paiement par carte bancaire. */
        CARTE,

        /** Prélèvement automatique. */
        PRELEVEMENT
    }

    /**
     * Canaux d'initiation des transactions.
     */
    public enum Canal {
        /** Agence physique (guichet). */
        AGENCE,

        /** Distributeur automatique de billets. */
        DAB,

        /** Plateforme de banque en ligne. */
        EN_LIGNE,

        /** Application mobile. */
        MOBILE
    }

    /**
     * Catégories de contrepartie.
     */
    public enum CategorieContrepartie {
        /** Personne physique. */
        PARTICULIER,

        /** Entreprise ou personne morale. */
        ENTREPRISE,

        /** Organisme gouvernemental ou administration publique. */
        GOUVERNEMENT
    }

    /**
     * Statuts possibles d'une transaction après traitement par le pipeline.
     */
    public enum StatutTransaction {
        /** Transaction acceptée sans alerte. */
        ACCEPTE,

        /** Transaction sous surveillance — alertes de niveau MOYEN ou ELEVE. */
        SURVEILLE,

        /** Transaction bloquée — alerte CRITIQUE ou disjoncteur ouvert. */
        BLOQUE
    }

    // Méthodes métier

    /**
     * Génère une référence unique de transaction.
     * <p>
     * Format : BNA-YYYYMMDD-XXXX où XXXX est un nombre aléatoire entre 0000 et 9999.
     * En production, ce serait un incrément quotidien géré par une séquence.
     * </p>
     *
     * @return la référence générée
     */
    private String genererReference() {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sequence = String.format("%04d", new java.util.Random().nextInt(10000));
        return "BNA-" + date + "-" + sequence;
    }

    /**
     * Détermine le statut de la transaction en fonction du score de risque.
     * <p>
     * Seuils configurables dans application.yml :
     * </p>
     * <ul>
     *   <li>score < 30 → ACCEPTE</li>
     *   <li>30 ≤ score ≤ 70 → SURVEILLE</li>
     *   <li>score > 70 → BLOQUE</li>
     * </ul>
     *
     * @param seuilSurveille seuil à partir duquel la transaction est surveillée
     * @param seuilBloque seuil à partir duquel la transaction est bloquée
     */
    public void evaluerStatut(int seuilSurveille, int seuilBloque) {
        if (scoreRisque == null) {
            this.statut = StatutTransaction.ACCEPTE;
            return;
        }

        int score = scoreRisque.intValue();
        if (score >= seuilBloque) {
            this.statut = StatutTransaction.BLOQUE;
        } else if (score >= seuilSurveille) {
            this.statut = StatutTransaction.SURVEILLE;
        } else {
            this.statut = StatutTransaction.ACCEPTE;
        }
    }

    /**
     * Vérifie si la transaction est en statut final (ne peut plus être modifiée).
     *
     * @return {@code true} si la transaction est ACCEPTEE, BLOQUEE, ou SURVEILLE
     */
    public boolean estFinalisee() {
        return statut != null;
    }

    /**
     * Vérifie si la transaction est bloquée.
     *
     * @return {@code true} si le statut est BLOQUE
     */
    public boolean estBloquee() {
        return statut == StatutTransaction.BLOQUE;
    }

    /**
     * Extrait le code banque à partir du RIB source (2 premiers chiffres).
     *
     * @return le code banque (2 caractères)
     */
    public String getCodeBanqueSource() {
        return ribSource != null && ribSource.length() >= 2
                ? ribSource.substring(0, 2)
                : null;
    }

    /**
     * Extrait le code agence à partir du RIB source (positions 3-5).
     *
     * @return le code agence (3 caractères)
     */
    public String getCodeAgenceSource() {
        return ribSource != null && ribSource.length() >= 5
                ? ribSource.substring(2, 5)
                : null;
    }

    /**
     * Récupère le code devise sous forme de String (évite le NullPointerException
     * si la devise n'est pas chargée).
     *
     * @return le code devise ou null
     */
    public String getCodeDevise() {
        return devise != null ? devise.getCode() : null;
    }
}