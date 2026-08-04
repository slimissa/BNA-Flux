package com.bna.flux.dto;

import com.bna.flux.entity.Transaction.Canal;
import com.bna.flux.entity.Transaction.TypeTransaction;
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
 * DTO pour la soumission d'une nouvelle transaction au pipeline BNA-FLUX.
 * <p>
 * Reçu par {@link com.bna.flux.controller.TransactionController#soumettre(RequeteTransaction)}
 * lorsqu'une transaction est injectée dans le système pour analyse.
 * </p>
 *
 * <p><b>Cycle de vie :</b></p>
 * <ol>
 *   <li>Le contrôleur reçoit ce DTO et le valide.</li>
 *   <li>Le {@link com.bna.flux.service.ServiceTransaction} le transforme
 *       en entité {@link com.bna.flux.entity.Transaction}.</li>
 *   <li>La transaction est soumise au {@link com.bna.flux.service.pipeline.MoteurPipeline}
 *       qui exécute les 5 étapes séquentiellement.</li>
 *   <li>Le résultat est retourné sous forme de {@link ReponseTransaction}.</li>
 * </ol>
 *
 * <p><b>Validation :</b></p>
 * <ul>
 *   <li>Les RIBs doivent contenir exactement 20 chiffres (format tunisien).</li>
 *   <li>Le montant doit être strictement positif.</li>
 *   <li>Le code devise doit être un code ISO 4217 valide (3 lettres majuscules).</li>
 *   <li>La date de transaction ne peut pas être dans le futur.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequeteTransaction {

    /**
     * RIB source de la transaction (émetteur).
     * <p>
     * Format tunisien : exactement 20 chiffres.
     * Composé de : code banque (2), code agence (3), numéro compte (13), clé RIB (2).
     * Validé par {@link com.bna.flux.service.ValidateurRib} lors du Stage 1 du pipeline.
     * </p>
     */
    @NotBlank(message = "Le RIB source est obligatoire")
    @Pattern(regexp = "^[0-9]{20}$", message = "Le RIB source doit contenir exactement 20 chiffres")
    private String ribSource;

    /**
     * RIB destination de la transaction (bénéficiaire).
     * <p>
     * Format tunisien : exactement 20 chiffres.
     * Composé de : code banque (2), code agence (3), numéro compte (13), clé RIB (2).
     * Validé par {@link com.bna.flux.service.ValidateurRib} lors du Stage 1 du pipeline.
     * </p>
     */
    @NotBlank(message = "Le RIB destination est obligatoire")
    @Pattern(regexp = "^[0-9]{20}$", message = "Le RIB destination doit contenir exactement 20 chiffres")
    private String ribDestination;

    /**
     * Montant de la transaction dans l'unité principale de la devise.
     * <p>
     * Doit être strictement positif. La précision (nombre de décimales)
     * sera validée en fonction des unités mineures de la devise lors
     * du Stage 1 du pipeline.
     * </p>
     * <p>
     * Exemples valides : 100.500 (TND, 3 décimales), 100.50 (EUR, 2 décimales).
     * </p>
     */
    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.001", message = "Le montant doit être supérieur à zéro")
    @Digits(integer = 15, fraction = 3, message = "Le montant ne doit pas dépasser 15 chiffres entiers et 3 décimales")
    private BigDecimal montant;

    /**
     * Code ISO 4217 de la devise de la transaction.
     * <p>
     * Doit correspondre à une devise active dans la table {@code devises}.
     * Validé lors du Stage 1 du pipeline.
     * </p>
     * <p>
     * Exemples : TND, EUR, USD, KWD, GBP.
     * </p>
     */
    @NotBlank(message = "Le code devise est obligatoire")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Le code devise doit être composé de 3 lettres majuscules")
    @Size(min = 3, max = 3, message = "Le code devise doit contenir exactement 3 caractères")
    private String codeDevise;

    /**
     * Type de transaction bancaire.
     */
    @NotNull(message = "Le type de transaction est obligatoire")
    private TypeTransaction typeTransaction;

    /**
     * Canal par lequel la transaction a été initiée.
     */
    @NotNull(message = "Le canal est obligatoire")
    private Canal canal;

    /**
     * Date et heure d'exécution de la transaction.
     * <p>
     * Correspond au moment où la transaction a été initiée dans le système
     * bancaire source. Ne peut pas être dans le futur.
     * </p>
     */
    @NotNull(message = "La date de transaction est obligatoire")
    private LocalDateTime dateTransaction;

    /**
     * Description ou motif de la transaction (optionnel).
     * <p>
     * Texte libre fourni par le système bancaire ou l'opérateur.
     * Peut être utilisé par les règles d'enrichissement.
     * </p>
     */
    @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
    private String description;
}