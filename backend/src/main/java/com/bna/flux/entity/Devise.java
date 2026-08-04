package com.bna.flux.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant une devise monétaire conforme à la norme ISO 4217.
 * <p>
 * Cette entité est la table de référence pour toutes les devises prises en
 * charge par BNA-FLUX. Elle est initialisée au démarrage via
 * {@link com.bna.flux.service.InitialisateurDevises} à partir du fichier
 * {@code devises.json}.
 * </p>
 *
 * <p><b>Règles de gestion :</b></p>
 * <ul>
 *   <li>Le code devise est une clé primaire (ex: TND, EUR, USD).</li>
 *   <li>Les unités mineures définissent la précision décimale :
 *       TND=3 (millimes), EUR=2 (centimes), JPY=0 (pas de sous-unité).</li>
 *   <li>Une devise inactive ne peut plus être utilisée dans de nouvelles
 *       transactions mais reste en base pour l'historique.</li>
 *   <li>La suppression physique est interdite — utiliser le flag {@code actif}.</li>
 * </ul>
 *
 * <p><b>Relations :</b></p>
 * <ul>
 *   <li>{@code Transaction} → {@code @ManyToOne Devise} : chaque transaction
 *       est libellée dans une devise.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Entity
@Table(name = "devises")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Devise {

    /**
     * Code alphabétique ISO 4217 de la devise.
     * <p>
     * Exactement 3 caractères majuscules.
     * Exemples : TND, EUR, USD, KWD, JPY.
     * </p>
     * <p>
     * Clé primaire — aucune autre table ne génère cette valeur.
     * </p>
     */
    @Id
    @NotBlank(message = "Le code devise est obligatoire")
    @Size(min = 3, max = 3, message = "Le code devise doit contenir exactement 3 caractères")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Le code devise doit être composé de 3 lettres majuscules")
    @Column(name = "code", length = 3, nullable = false, unique = true)
    private String code;

    /**
     * Nom complet de la devise en français.
     * <p>
     * Exemples : "Dinar Tunisien", "Euro", "Dollar Américain".
     * </p>
     */
    @NotBlank(message = "Le nom de la devise est obligatoire")
    @Size(max = 100, message = "Le nom de la devise ne doit pas dépasser 100 caractères")
    @Column(name = "nom", length = 100, nullable = false)
    private String nom;

    /**
     * Nombre d'unités mineures (décimales) de la devise.
     * <p>
     * Détermine la précision des montants :
     * </p>
     * <ul>
     *   <li>0 : Pas de sous-unité (ex: JPY, KRW)</li>
     *   <li>2 : Centimes (ex: EUR, USD, TND en affichage courant)</li>
     *   <li>3 : Millimes (ex: TND en précision bancaire, KWD, BHD)</li>
     *   <li>8 : Crypto-monnaies (non utilisées ici)</li>
     * </ul>
     * <p>
     * Pour le TND : bien que l'affichage courant utilise 2 décimales,
     * la précision bancaire est de 3 décimales (millimes).
     * </p>
     */
    @PositiveOrZero(message = "Les unités mineures ne peuvent pas être négatives")
    @Column(name = "unites_mineures", nullable = false)
    private int unitesMineures;

    /**
     * Symbole monétaire de la devise.
     * <p>
     * Utilisé pour l'affichage des montants formatés.
     * Exemples : "د.ت" (TND), "€" (EUR), "$" (USD), "¥" (JPY).
     * </p>
     */
    @Size(max = 10, message = "Le symbole ne doit pas dépasser 10 caractères")
    @Column(name = "symbole", length = 10)
    private String symbole;

    /**
     * Code numérique ISO 4217 de la devise.
     * <p>
     * Code à 3 chiffres attribué par l'ISO.
     * Exemples : 788 (TND), 978 (EUR), 840 (USD).
     * </p>
     * <p>
     * Peut ne pas être unique si des codes historiques sont réutilisés.
     * Ne pas utiliser comme clé alternative.
     * </p>
     */
    @Pattern(regexp = "^[0-9]{3}$", message = "Le code numérique doit contenir exactement 3 chiffres")
    @Column(name = "code_numerique", length = 3)
    private String codeNumerique;

    /**
     * Indique si la devise est active et utilisable dans les transactions.
     * <p>
     * Une devise inactive :
     * </p>
     * <ul>
     *   <li>N'apparaît plus dans les listes de sélection</li>
     *   <li>Reste en base pour l'historique des transactions passées</li>
     *   <li>Ne peut plus être attribuée à une nouvelle transaction</li>
     * </ul>
     */
    @Column(name = "actif", nullable = false)
    @Builder.Default
    private boolean actif = true;

    /**
     * Date de création de l'enregistrement.
     * <p>
     * Remplie automatiquement avant la persistance initiale.
     * Ne peut pas être modifiée après création.
     * </p>
     */
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * Date de la dernière modification de l'enregistrement.
     * <p>
     * Mise à jour automatiquement avant chaque modification.
     * </p>
     */
    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    // ============================================================
    // Callbacks JPA
    // ============================================================

    /**
     * Initialise les dates de création et modification avant la première
     * persistance.
     */
    @PrePersist
    protected void avantCreation() {
        this.dateCreation = LocalDateTime.now();
        this.dateModification = LocalDateTime.now();
    }

    /**
     * Met à jour la date de modification avant chaque mise à jour.
     */
    @PreUpdate
    protected void avantModification() {
        this.dateModification = LocalDateTime.now();
    }

    // ============================================================
    // Méthodes métier
    // ============================================================

    /**
     * Convertit un montant en unités mineures (centimes, millimes, etc.).
     * <p>
     * Exemples :
     * </p>
     * <ul>
     *   <li>100.500 TND (3 unités mineures) → 100500 millimes</li>
     *   <li>100.50 EUR (2 unités mineures) → 10050 centimes</li>
     *   <li>500 JPY (0 unité mineure) → 500</li>
     * </ul>
     *
     * @param montant le montant dans l'unité principale
     * @return le montant converti en unités mineures, ou {@code null} si le montant est null
     * @throws ArithmeticException si la conversion produit un nombre non entier
     */
    public java.math.BigDecimal versUnitesMineures(java.math.BigDecimal montant) {
        if (montant == null) {
            return null;
        }
        return montant.movePointRight(unitesMineures);
    }

    /**
     * Convertit un montant depuis les unités mineures vers l'unité principale.
     *
     * @param montant le montant en unités mineures
     * @return le montant dans l'unité principale, ou {@code null} si le montant est null
     */
    public java.math.BigDecimal depuisUnitesMineures(java.math.BigDecimal montant) {
        if (montant == null) {
            return null;
        }
        return montant.movePointLeft(unitesMineures);
    }

    /**
     * Formate un montant avec le symbole de la devise.
     * <p>
     * Exemples :
     * </p>
     * <ul>
     *   <li>"د.ت 100,500" pour 100.500 TND</li>
     *   <li>"€ 100,50" pour 100.50 EUR</li>
     *   <li>"$ 100.50" pour 100.50 USD</li>
     * </ul>
     *
     * @param montant le montant dans l'unité principale
     * @return la représentation formatée avec le symbole
     */
    public String formater(java.math.BigDecimal montant) {
        if (montant == null) {
            return symbole + " 0";
        }
        return symbole + " " + String.format("%." + unitesMineures + "f", montant);
    }
}