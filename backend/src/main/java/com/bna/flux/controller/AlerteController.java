package com.bna.flux.controller;

import com.bna.flux.dto.ReponseAlerte;
import com.bna.flux.dto.ReponseErreur;
import com.bna.flux.entity.Alerte;
import com.bna.flux.entity.Alerte.NiveauAlerte;
import com.bna.flux.repository.AlerteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour la consultation et l'acquittement des alertes.
 * <p>
 * Les alertes sont générées automatiquement par le pipeline lorsqu'une
 * règle de surveillance est déclenchée. Ce contrôleur permet aux opérateurs
 * de consulter les alertes et de les acquitter après revue manuelle.
 * </p>
 *
 * <p><b>Endpoints :</b></p>
 * <ul>
 *   <li>{@code GET /api/alertes} — Lister avec filtrage et pagination</li>
 *   <li>{@code GET /api/alertes/{id}} — Détail d'une alerte</li>
 *   <li>{@code PUT /api/alertes/{id}/acquitter} — Acquitter une alerte</li>
 *   <li>{@code GET /api/alertes/emails-envoyes} — Historique des emails</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/alertes")
@Tag(name = "Alertes", description = "Consultation et acquittement des alertes de surveillance")
public class AlerteController {

    private final AlerteRepository alerteRepository;

    public AlerteController(AlerteRepository alerteRepository) {
        this.alerteRepository = alerteRepository;
    }

    // GET /api/alertes — Lister avec filtrage

    /**
     * Liste les alertes avec filtrage, pagination et tri.
     *
     * @param niveau      filtre par niveau (optionnel)
     * @param acquittee   filtre par état d'acquittement (optionnel)
     * @param dateDebut   date de début (optionnel)
     * @param dateFin     date de fin (optionnel)
     * @param page        numéro de page
     * @param taille      taille de la page
     * @return une page d'alertes
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Lister les alertes", description = "Retourne une liste paginée et filtrable des alertes.")
    public ResponseEntity<Map<String, Object>> lister(
            @Parameter(description = "Filtre par niveau (FAIBLE, MOYEN, ELEVE, CRITIQUE)")
            @RequestParam(required = false) String niveau,

            @Parameter(description = "Filtre par acquittement (true/false)")
            @RequestParam(required = false) Boolean acquittee,

            @Parameter(description = "Date de début (ISO 8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateDebut,

            @Parameter(description = "Date de fin (ISO 8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFin,

            @Parameter(description = "Numéro de page (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Taille de la page")
            @RequestParam(defaultValue = "20") int taille) {

        log.debug("GET /api/alertes — Page: {}, Taille: {}", page, taille);

        int tailleLimitee = Math.min(taille, 100);
        Pageable pageable = PageRequest.of(page, tailleLimitee, Sort.by(Sort.Direction.DESC, "dateCreation"));

        Page<Alerte> alertes;

        // Appliquer les filtres
        if (niveau != null && dateDebut != null && dateFin != null) {
            try {
                NiveauAlerte niveauEnum = NiveauAlerte.valueOf(niveau.toUpperCase());
                alertes = alerteRepository.findByNiveauAndDateCreationBetween(niveauEnum, dateDebut, dateFin, pageable);
            } catch (IllegalArgumentException e) {
                alertes = alerteRepository.findByDateCreationBetween(dateDebut, dateFin, pageable);
            }
        } else if (dateDebut != null && dateFin != null) {
            alertes = alerteRepository.findByDateCreationBetween(dateDebut, dateFin, pageable);
        } else if (niveau != null) {
            try {
                NiveauAlerte niveauEnum = NiveauAlerte.valueOf(niveau.toUpperCase());
                alertes = alerteRepository.findByNiveau(niveauEnum, pageable);
            } catch (IllegalArgumentException e) {
                alertes = Page.empty();
            }
        } else if (acquittee != null) {
            alertes = acquittee
                    ? alerteRepository.findByAcquitteeTrue(pageable)
                    : alerteRepository.findByAcquitteeFalse(pageable);
        } else {
            alertes = alerteRepository.findAll(pageable);
        }

        List<ReponseAlerte> contenu = alertes.getContent().stream()
                .map(this::mapperAlerte)
                .collect(Collectors.toList());

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("donnees", contenu);
        reponse.put("pagination", Map.of(
                "page", alertes.getNumber(),
                "taille", alertes.getSize(),
                "totalElements", alertes.getTotalElements(),
                "totalPages", alertes.getTotalPages()
        ));
        reponse.put("horodatage", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(reponse);
    }

    // GET /api/alertes/{id} — Détail d'une alerte

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Consulter une alerte", description = "Retourne le détail d'une alerte.")
    public ResponseEntity<?> consulter(@PathVariable Long id) {
        log.debug("GET /api/alertes/{} — Consultation", id);

        return alerteRepository.findById(id)
                .map(alerte -> {
                    ReponseAlerte reponseAlerte = mapperAlerte(alerte);

                    Map<String, Object> reponse = new LinkedHashMap<>();
                    reponse.put("statut", "SUCCES");
                    reponse.put("donnees", reponseAlerte);
                    reponse.put("horodatage", java.time.LocalDateTime.now().toString());

                    return ResponseEntity.ok((Object) reponse);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ReponseErreur.introuvable("Alerte", id)));
    }

    // PUT /api/alertes/{id}/acquitter — Acquitter une alerte

    /**
     * Acquitte une alerte après revue manuelle.
     * <p>
     * L'acquittement est tracé avec l'identifiant de l'opérateur
     * et la date/heure. Une alerte déjà acquittée ne peut pas l'être
     * une seconde fois.
     * </p>
     *
     * @param id             l'identifiant de l'alerte
     * @param authentication l'authentification de l'utilisateur connecté
     * @return l'alerte acquittée ou une erreur
     */
    @PutMapping("/{id}/acquitter")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Acquitter une alerte", description = "Marque une alerte comme traitée par l'opérateur.")
    @ApiResponse(responseCode = "400", description = "Alerte déjà acquittée",
            content = @Content(schema = @Schema(implementation = ReponseErreur.class)))
    public ResponseEntity<?> acquitter(@PathVariable Long id, Authentication authentication) {
        log.info("PUT /api/alertes/{}/acquitter — Acquittement par {}", id,
                authentication != null ? authentication.getName() : "inconnu");

        return alerteRepository.findById(id)
                .map(alerte -> {
                    if (alerte.isAcquittee()) {
                        return ResponseEntity.badRequest()
                                .body(ReponseErreur.of("ALERTE_DEJA_ACQUITTEE",
                                        "Cette alerte a déjà été acquittée le " + alerte.getAcquitteeLe()
                                        + " par " + alerte.getAcquitteePar()));
                    }

                    String operateur = authentication != null ? authentication.getName() : "SYSTEME";
                    alerte.acquitter(operateur);
                    alerteRepository.save(alerte);

                    log.info("Alerte {} acquittée par {}", id, operateur);

                    Map<String, Object> reponse = new LinkedHashMap<>();
                    reponse.put("statut", "SUCCES");
                    reponse.put("message", "Alerte acquittée avec succès");
                    reponse.put("acquitteePar", operateur);
                    reponse.put("acquitteeLe", alerte.getAcquitteeLe().toString());
                    reponse.put("horodatage", java.time.LocalDateTime.now().toString());

                    return ResponseEntity.ok((Object) reponse);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ReponseErreur.introuvable("Alerte", id)));
    }

    // GET /api/alertes/emails-envoyes — Historique des emails

    @GetMapping("/emails-envoyes")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Emails envoyés", description = "Retourne l'historique des alertes pour lesquelles un email a été envoyé.")
    public ResponseEntity<Map<String, Object>> emailsEnvoyes(
            @Parameter(description = "Numéro de page (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Taille de la page")
            @RequestParam(defaultValue = "20") int taille) {

        log.debug("GET /api/alertes/emails-envoyes");

        int tailleLimitee = Math.min(taille, 100);
        Pageable pageable = PageRequest.of(page, tailleLimitee, Sort.by(Sort.Direction.DESC, "emailEnvoyeLe"));

        // Récupérer toutes les alertes et filtrer celles avec email envoyé
        Page<Alerte> alertes = alerteRepository.findAll(pageable);

        List<Map<String, Object>> emails = alertes.getContent().stream()
                .filter(Alerte::isEmailEnvoye)
                .map(alerte -> {
                    Map<String, Object> email = new LinkedHashMap<>();
                    email.put("alerteId", alerte.getId());
                    email.put("destinataire", alerte.getEmailDestinataire());
                    email.put("dateEnvoi", alerte.getEmailEnvoyeLe() != null
                            ? alerte.getEmailEnvoyeLe().toString() : null);
                    email.put("niveau", alerte.getNiveau().name());
                    email.put("message", alerte.getMessage());
                    return email;
                })
                .collect(Collectors.toList());

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("emails", emails);
        reponse.put("horodatage", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(reponse);
    }

    // Méthode privée de mapping

    /**
     * Mappe une entité {@link Alerte} vers un DTO {@link ReponseAlerte}.
     */
    private ReponseAlerte mapperAlerte(Alerte alerte) {
        if (alerte == null) {
            return null;
        }

        return ReponseAlerte.builder()
                .id(alerte.getId())
                .transactionId(alerte.getTransaction() != null ? alerte.getTransaction().getId() : null)
                .referenceTransaction(alerte.getTransaction() != null
                        ? alerte.getTransaction().getReferenceTransaction() : null)
                .regleId(alerte.getRegle() != null ? alerte.getRegle().getId() : null)
                .nomRegle(alerte.getRegle() != null ? alerte.getRegle().getNom() : null)
                .message(alerte.getMessage())
                .niveau(alerte.getNiveau())
                .dateCreation(alerte.getDateCreation())
                .acquittee(alerte.isAcquittee())
                .acquitteePar(alerte.getAcquitteePar())
                .acquitteeLe(alerte.getAcquitteeLe())
                .emailEnvoye(alerte.isEmailEnvoye())
                .emailEnvoyeLe(alerte.getEmailEnvoyeLe())
                .emailDestinataire(alerte.getEmailDestinataire())
                .delaiMinutes(alerte.getDelaiMinutes())
                .build();
    }
}