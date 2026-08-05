package com.bna.flux.controller;

import com.bna.flux.dto.ReponseErreur;
import com.bna.flux.dto.ReponseTransaction;
import com.bna.flux.dto.ReponseVerificationAudit;
import com.bna.flux.dto.RequeteTransaction;
import com.bna.flux.entity.Transaction;
import com.bna.flux.exception.RibInvalideException;
import com.bna.flux.service.ServiceAudit;
import com.bna.flux.service.ServiceTransaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour la gestion des transactions.
 * <p>
 * Point d'entrée pour la soumission de nouvelles transactions au pipeline
 * de surveillance et la consultation des transactions existantes avec
 * filtrage, pagination et tri.
 * </p>
 *
 * <p><b>Endpoints :</b></p>
 * <ul>
 *   <li>{@code POST /api/transactions} — Soumettre une transaction au pipeline</li>
 *   <li>{@code GET /api/transactions} — Lister avec filtrage et pagination</li>
 *   <li>{@code GET /api/transactions/{id}} — Détail d'une transaction</li>
 *   <li>{@code GET /api/transactions/{id}/piste-audit} — Piste d'audit</li>
 *   <li>{@code GET /api/transactions/{id}/piste-audit/verifier} — Vérifier l'intégrité</li>
 *   <li>{@code GET /api/transactions/{id}/alertes} — Alertes liées</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Soumission et consultation des transactions surveillées")
public class TransactionController {

    private final ServiceTransaction serviceTransaction;
    private final ServiceAudit serviceAudit;

    public TransactionController(ServiceTransaction serviceTransaction,
                                  ServiceAudit serviceAudit) {
        this.serviceTransaction = serviceTransaction;
        this.serviceAudit = serviceAudit;
    }

    // POST /api/transactions — Soumettre une transaction

    /**
     * Soumet une nouvelle transaction au pipeline de surveillance.
     * <p>
     * La transaction traverse les 5 étapes du pipeline (Validation,
     * Enrichissement, Évaluation, Notation, Persistance) et le résultat
     * est retourné avec le statut final, le score de risque et les alertes.
     * </p>
     *
     * @param requete le DTO contenant les données de la transaction
     * @return la réponse avec le résultat du pipeline
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(
            summary = "Soumettre une transaction au pipeline",
            description = "Soumet une transaction qui traverse les 5 étapes du pipeline de surveillance."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction traitée avec succès",
                    content = @Content(schema = @Schema(implementation = ReponseTransaction.class))),
            @ApiResponse(responseCode = "400", description = "Données de transaction invalides",
                    content = @Content(schema = @Schema(implementation = ReponseErreur.class))),
            @ApiResponse(responseCode = "422", description = "Transaction bloquée par disjoncteur",
                    content = @Content(schema = @Schema(implementation = ReponseErreur.class)))
    })
    public ResponseEntity<?> soumettre(@Valid @RequestBody RequeteTransaction requete) {
        log.info("POST /api/transactions — Soumission d'une transaction");

        try {
            ReponseTransaction reponse = serviceTransaction.soumettre(requete);

            Map<String, Object> enveloppe = new LinkedHashMap<>();
            enveloppe.put("statut", reponse.getStatut());
            enveloppe.put("donnees", reponse);
            enveloppe.put("horodatage", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(enveloppe);

        } catch (RibInvalideException e) {
            log.warn("Transaction rejetée — RIB invalide : {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ReponseErreur.of("RIB_INVALIDE", e.getMessage(), e.getDetails()));

        } catch (Exception e) {
            log.error("Erreur lors de la soumission de la transaction : {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ReponseErreur.interne("Erreur lors du traitement de la transaction"));
        }
    }

    // GET /api/transactions — Lister avec filtrage

    /**
     * Liste les transactions avec filtrage, pagination et tri.
     *
     * @param statut         filtre par statut (optionnel)
     * @param codeDevise     filtre par devise (optionnel)
     * @param canal          filtre par canal (optionnel)
     * @param typeTransaction filtre par type (optionnel)
     * @param minMontant     montant minimum (optionnel)
     * @param maxMontant     montant maximum (optionnel)
     * @param dateDebut      date de début (optionnel)
     * @param dateFin        date de fin (optionnel)
     * @param page           numéro de page (0-based)
     * @param taille         taille de la page
     * @param tri            champ de tri (ex: dateTransaction,desc)
     * @return une page de transactions
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(
            summary = "Lister les transactions",
            description = "Retourne une liste paginée et filtrable des transactions."
    )
    public ResponseEntity<Map<String, Object>> lister(
            @Parameter(description = "Filtre par statut (ACCEPTE, SURVEILLE, BLOQUE)")
            @RequestParam(required = false) String statut,

            @Parameter(description = "Filtre par code devise (ex: TND, EUR)")
            @RequestParam(required = false) String codeDevise,

            @Parameter(description = "Filtre par canal (AGENCE, DAB, EN_LIGNE, MOBILE)")
            @RequestParam(required = false) String canal,

            @Parameter(description = "Filtre par type (VIREMENT, CHEQUE, ESPECES, CARTE, PRELEVEMENT)")
            @RequestParam(required = false) String typeTransaction,

            @Parameter(description = "Montant minimum")
            @RequestParam(required = false) BigDecimal minMontant,

            @Parameter(description = "Montant maximum")
            @RequestParam(required = false) BigDecimal maxMontant,

            @Parameter(description = "Date de début (ISO 8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateDebut,

            @Parameter(description = "Date de fin (ISO 8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFin,

            @Parameter(description = "Numéro de page (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Taille de la page")
            @RequestParam(defaultValue = "20") int taille,

            @Parameter(description = "Tri (ex: dateTransaction,desc)")
            @RequestParam(defaultValue = "dateTransaction,desc") String tri) {

        log.debug("GET /api/transactions — Page: {}, Taille: {}, Tri: {}", page, taille, tri);

        // Construire le tri
        Sort sort = construireTri(tri);

        // Limiter la taille de page
        int tailleLimitee = Math.min(taille, 100);
        Pageable pageable = PageRequest.of(page, tailleLimitee, sort);

        // Rechercher avec filtres
        Page<Transaction> transactions = serviceTransaction.rechercher(
                statut, codeDevise, canal, typeTransaction,
                minMontant, maxMontant, dateDebut, dateFin, pageable
        );

        // Mapper les transactions en DTOs
        List<ReponseTransaction> contenu = transactions.getContent().stream()
                .map(this::mapperTransactionResume)
                .collect(Collectors.toList());

        // Construire la réponse paginée
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("donnees", contenu);
        reponse.put("pagination", Map.of(
                "page", transactions.getNumber(),
                "taille", transactions.getSize(),
                "totalElements", transactions.getTotalElements(),
                "totalPages", transactions.getTotalPages()
        ));
        reponse.put("horodatage", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(reponse);
    }

    // GET /api/transactions/{id} — Détail

    /**
     * Retourne le détail complet d'une transaction.
     *
     * @param id l'identifiant de la transaction
     * @return le détail de la transaction ou 404
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Consulter une transaction", description = "Retourne le détail complet d'une transaction.")
    public ResponseEntity<?> consulter(@Parameter(description = "ID de la transaction") @PathVariable Long id) {
        log.debug("GET /api/transactions/{} — Consultation", id);

        return serviceTransaction.getParId(id)
                .map(transaction -> {
                    ReponseTransaction reponse = mapperTransactionDetail(transaction);

                    Map<String, Object> enveloppe = new LinkedHashMap<>();
                    enveloppe.put("statut", "SUCCES");
                    enveloppe.put("donnees", reponse);
                    enveloppe.put("horodatage", java.time.LocalDateTime.now().toString());

                    return ResponseEntity.ok((Object) enveloppe);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ReponseErreur.introuvable("Transaction", id)));
    }

    // GET /api/transactions/{id}/piste-audit

    /**
     * Retourne la piste d'audit complète d'une transaction.
     *
     * @param id l'identifiant de la transaction
     * @return la liste des entrées d'audit
     */
    @GetMapping("/{id}/piste-audit")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Piste d'audit", description = "Retourne la piste d'audit hash-chaînée de la transaction.")
    public ResponseEntity<?> pisteAudit(@PathVariable Long id) {
        log.debug("GET /api/transactions/{}/piste-audit", id);

        // Vérifier que la transaction existe
        if (serviceTransaction.getParId(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ReponseErreur.introuvable("Transaction", id));
        }

        var entrees = serviceAudit.getPisteAudit(id);

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("transactionId", id);
        reponse.put("nombreEntrees", entrees.size());
        reponse.put("entrees", entrees);
        reponse.put("horodatage", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(reponse);
    }

    // GET /api/transactions/{id}/piste-audit/verifier

    /**
     * Vérifie l'intégrité de la piste d'audit.
     *
     * @param id l'identifiant de la transaction
     * @return le résultat de la vérification
     */
    @GetMapping("/{id}/piste-audit/verifier")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Vérifier l'intégrité", description = "Vérifie l'intégrité de la chaîne de hachage de la piste d'audit.")
    public ResponseEntity<?> verifierPisteAudit(@PathVariable Long id) {
        log.info("GET /api/transactions/{}/piste-audit/verifier — Vérification d'intégrité", id);

        // Vérifier que la transaction existe
        if (serviceTransaction.getParId(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ReponseErreur.introuvable("Transaction", id));
        }

        long debut = System.currentTimeMillis();
        ServiceAudit.ResultatVerification resultat = serviceAudit.verifierChaine(id);
        long duree = System.currentTimeMillis() - debut;

        // Construire la réponse
        ReponseVerificationAudit reponse = ReponseVerificationAudit.builder()
                .transactionId(id)
                .referenceTransaction(serviceTransaction.getParId(id)
                        .map(Transaction::getReferenceTransaction).orElse(null))
                .chaineIntacte(resultat.isChaineIntacte())
                .nombreEntrees(resultat.getNombreEntrees())
                .entreeCorrompue(resultat.getEntreeCorrompue())
                .messageErreur(resultat.getMessage())
                .dureeVerificationMs(duree)
                .build();

        Map<String, Object> enveloppe = new LinkedHashMap<>();
        enveloppe.put("statut", "SUCCES");
        enveloppe.put("donnees", reponse);
        enveloppe.put("horodatage", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(enveloppe);
    }

    // GET /api/transactions/{id}/alertes

    /**
     * Retourne les alertes liées à une transaction.
     *
     * @param id l'identifiant de la transaction
     * @return la liste des alertes
     */
    @GetMapping("/{id}/alertes")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Alertes de la transaction", description = "Retourne les alertes générées pour cette transaction.")
    public ResponseEntity<?> alertes(@PathVariable Long id) {
        log.debug("GET /api/transactions/{}/alertes", id);

        if (serviceTransaction.getParId(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ReponseErreur.introuvable("Transaction", id));
        }

        // Utiliser le service audit pour récupérer les alertes via la transaction
        var transactionOpt = serviceTransaction.getParId(id);

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("transactionId", id);
        reponse.put("referenceTransaction", transactionOpt.map(Transaction::getReferenceTransaction).orElse(null));
        reponse.put("horodatage", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(reponse);
    }

    // Méthodes privées

    /**
     * Mappe une transaction en DTO résumé (pour les listes).
     */
    private ReponseTransaction mapperTransactionResume(Transaction transaction) {
        return ReponseTransaction.builder()
                .id(transaction.getId())
                .referenceTransaction(transaction.getReferenceTransaction())
                .montant(transaction.getMontant())
                .codeDevise(transaction.getCodeDevise())
                .typeTransaction(transaction.getTypeTransaction())
                .canal(transaction.getCanal())
                .dateTransaction(transaction.getDateTransaction())
                .scoreRisque(transaction.getScoreRisque())
                .statutTransaction(transaction.getStatut())
                .motif(transaction.getMotif())
                .traiteLe(transaction.getTraiteLe())
                .build();
    }

    /**
     * Mappe une transaction en DTO détaillé.
     */
    private ReponseTransaction mapperTransactionDetail(Transaction transaction) {
        return ReponseTransaction.builder()
                .id(transaction.getId())
                .referenceTransaction(transaction.getReferenceTransaction())
                .ribSource(transaction.getRibSource())
                .ribDestination(transaction.getRibDestination())
                .montant(transaction.getMontant())
                .codeDevise(transaction.getCodeDevise())
                .nomDevise(transaction.getDevise() != null ? transaction.getDevise().getNom() : null)
                .symboleDevise(transaction.getDevise() != null ? transaction.getDevise().getSymbole() : null)
                .typeTransaction(transaction.getTypeTransaction())
                .canal(transaction.getCanal())
                .dateTransaction(transaction.getDateTransaction())
                .description(transaction.getDescription())
                .paysOrigine(transaction.getPaysOrigine())
                .categorieContrepartie(transaction.getCategorieContrepartie() != null
                        ? transaction.getCategorieContrepartie().name() : null)
                .scoreRisque(transaction.getScoreRisque())
                .statutTransaction(transaction.getStatut())
                .motif(transaction.getMotif())
                .traiteLe(transaction.getTraiteLe())
                .dateCreation(transaction.getDateCreation())
                .pisteAuditDisponible(true)
                .build();
    }

    /**
     * Construit l'objet Sort à partir de la chaîne de tri.
     *
     * @param tri chaîne de tri (ex: "dateTransaction,desc")
     * @return l'objet Sort
     */
    private Sort construireTri(String tri) {
        if (tri == null || tri.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "dateTransaction");
        }

        String[] parties = tri.split(",");
        String champ = parties[0].trim();
        Sort.Direction direction = parties.length > 1 && "asc".equalsIgnoreCase(parties[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(direction, champ);
    }
}