package com.bna.flux.controller;

import com.bna.flux.dto.ReponseErreur;
import com.bna.flux.dto.ReponseResumeTableauBord;
import com.bna.flux.service.ServiceTableauBord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contrôleur REST pour le tableau de bord BNA-FLUX.
 * <p>
 * Fournit des statistiques consolidées en temps réel sur les transactions,
 * les alertes, les disjoncteurs et les règles. Les données sont agrégées
 * par le {@link ServiceTableauBord} à partir des repositories.
 * </p>
 *
 * <p><b>Endpoints :</b></p>
 * <ul>
 *   <li>{@code GET /api/tableau-bord/resume} — Résumé complet du dashboard</li>
 *   <li>{@code GET /api/tableau-bord/tendance} — Tendance journalière</li>
 * </ul>
 *
 * <p><b>Données incluses dans le résumé :</b></p>
 * <ul>
 *   <li>Transactions : total, acceptées, surveillées, bloquées, par canal/type/devise</li>
 *   <li>Alertes : total, par niveau, non acquittées, délai moyen d'acquittement</li>
 *   <li>Disjoncteurs : total, ouverts, fermés, mi-ouverts, total échecs</li>
 *   <li>Règles : totales, actives, top 5 des plus déclenchées</li>
 *   <li>Tendance : évolution journalière sur 7 ou 30 jours</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/tableau-bord")
@Tag(name = "Tableau de Bord", description = "Statistiques et indicateurs en temps réel")
public class TableauBordController {

    private final ServiceTableauBord serviceTableauBord;

    public TableauBordController(ServiceTableauBord serviceTableauBord) {
        this.serviceTableauBord = serviceTableauBord;
    }

    // GET /api/tableau-bord/resume

    /**
     * Retourne le résumé complet du tableau de bord.
     * <p>
     * Inclut toutes les statistiques agrégées pour la période spécifiée.
     * Par défaut, la période couvre la journée en cours.
     * </p>
     *
     * @param dateDebut date de début (optionnel, défaut : aujourd'hui)
     * @param dateFin   date de fin (optionnel, défaut : aujourd'hui)
     * @return le résumé complet
     */
    @GetMapping("/resume")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(
            summary = "Résumé du tableau de bord",
            description = "Retourne les statistiques consolidées pour le tableau de bord."
    )
    @ApiResponse(responseCode = "200", description = "Résumé généré avec succès",
            content = @Content(schema = @Schema(implementation = ReponseResumeTableauBord.class)))
    public ResponseEntity<?> resume(
            @Parameter(description = "Date de début (ISO 8601, ex: 2026-08-04)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,

            @Parameter(description = "Date de fin (ISO 8601, ex: 2026-08-04)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {

        log.info("GET /api/tableau-bord/resume — Période: {} à {}",
                dateDebut != null ? dateDebut : "aujourd'hui",
                dateFin != null ? dateFin : "maintenant");

        try {
            ReponseResumeTableauBord resume = serviceTableauBord.getResume(dateDebut, dateFin);

            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("donnees", resume);
            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            log.debug("Résumé tableau de bord généré — {} transactions, {} alertes, {} disjoncteurs ouverts",
                    resume.getTransactions() != null ? resume.getTransactions().getTotal() : 0,
                    resume.getAlertes() != null ? resume.getAlertes().getTotal() : 0,
                    resume.getDisjoncteurs() != null ? resume.getDisjoncteurs().getOuverts() : 0);

            return ResponseEntity.ok(reponse);

        } catch (Exception e) {
            log.error("Erreur lors de la génération du résumé : {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ReponseErreur.interne("Erreur lors de la génération du tableau de bord"));
        }
    }

    // GET /api/tableau-bord/tendance

    /**
     * Retourne la tendance journalière des transactions.
     * <p>
     * Permet de visualiser l'évolution du nombre de transactions
     * (acceptées, surveillées, bloquées) sur une période.
     * </p>
     *
     * @param periode période : JOURNALIER (7 jours), HEBDOMADAIRE (4 semaines), MENSUEL (12 mois)
     * @param debut   date de début (optionnel)
     * @param fin     date de fin (optionnel)
     * @return les données de tendance
     */
    @GetMapping("/tendance")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(
            summary = "Tendance des transactions",
            description = "Retourne l'évolution journalière des transactions sur une période."
    )
    public ResponseEntity<?> tendance(
            @Parameter(description = "Période : JOURNALIER, HEBDOMADAIRE, MENSUEL")
            @RequestParam(defaultValue = "JOURNALIER") String periode,

            @Parameter(description = "Date de début (optionnel)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,

            @Parameter(description = "Date de fin (optionnel)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {

        log.debug("GET /api/tableau-bord/tendance — Période: {}, {} à {}",
                periode, debut != null ? debut : "auto", fin != null ? fin : "auto");

        try {
            // Déterminer la période par défaut selon le type
            if (debut == null && fin == null) {
                fin = LocalDate.now();
                debut = switch (periode.toUpperCase()) {
                    case "HEBDOMADAIRE" -> fin.minusWeeks(4);
                    case "MENSUEL" -> fin.minusMonths(12);
                    default -> fin.minusDays(7); // JOURNALIER
                };
            } else if (debut == null) {
                debut = fin != null ? fin.minusDays(7) : LocalDate.now().minusDays(7);
            } else if (fin == null) {
                fin = LocalDate.now();
            }

            ReponseResumeTableauBord resume = serviceTableauBord.getResume(debut, fin);

            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("periode", Map.of("debut", debut.toString(), "fin", fin.toString()));
            reponse.put("typePeriode", periode.toUpperCase());
            reponse.put("tendance", resume.getTendance());
            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(reponse);

        } catch (Exception e) {
            log.error("Erreur lors de la génération de la tendance : {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ReponseErreur.interne("Erreur lors de la génération de la tendance"));
        }
    }

    // GET /api/tableau-bord/statistiques — Stats rapides

    /**
     * Retourne des statistiques rapides pour les widgets du dashboard.
     * <p>
     * Plus léger que le résumé complet — uniquement les compteurs principaux.
     * </p>
     *
     * @return les compteurs principaux
     */
    @GetMapping("/statistiques")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(
            summary = "Statistiques rapides",
            description = "Retourne les compteurs principaux pour les widgets du dashboard."
    )
    public ResponseEntity<?> statistiques() {
        log.debug("GET /api/tableau-bord/statistiques");

        try {
            ReponseResumeTableauBord resume = serviceTableauBord.getResume(
                    LocalDate.now(), LocalDate.now());

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("transactionsTotal", resume.getTransactions() != null
                    ? resume.getTransactions().getTotal() : 0);
            stats.put("transactionsSurveillees", resume.getTransactions() != null
                    ? resume.getTransactions().getSurveillees() : 0);
            stats.put("transactionsBloquees", resume.getTransactions() != null
                    ? resume.getTransactions().getBloquees() : 0);
            stats.put("alertesNonAcquittees", resume.getAlertes() != null
                    ? resume.getAlertes().getNonAcquittees() : 0);
            stats.put("alertesActionsRequises", resume.getAlertes() != null
                    ? resume.getAlertes().getActionsRequises() : 0);
            stats.put("disjoncteursOuverts", resume.getDisjoncteurs() != null
                    ? resume.getDisjoncteurs().getOuverts() : 0);
            stats.put("scoreRisqueMoyen", resume.getScoreRisqueMoyen());
            stats.put("reglesActives", resume.getRegles() != null
                    ? resume.getRegles().getActives() : 0);
            stats.put("devisesActives", resume.getNombreDevisesActives());
            stats.put("utilisateursActifs", resume.getNombreUtilisateursActifs());

            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("statistiques", stats);
            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(reponse);

        } catch (Exception e) {
            log.error("Erreur lors de la génération des statistiques : {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ReponseErreur.interne("Erreur lors de la génération des statistiques"));
        }
    }
}