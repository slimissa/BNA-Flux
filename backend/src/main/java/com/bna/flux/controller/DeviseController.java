package com.bna.flux.controller;

import com.bna.flux.dto.ReponseErreur;
import com.bna.flux.entity.Devise;
import com.bna.flux.repository.DeviseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la consultation des devises.
 * <p>
 * Endpoint public (sans authentification) exposant la liste des devises
 * supportées par BNA-FLUX. Les devises sont chargées au démarrage par
 * {@link com.bna.flux.service.InitialisateurDevises} depuis le fichier
 * {@code devises.json}.
 * </p>
 *
 * <p><b>Endpoints :</b></p>
 * <ul>
 *   <li>{@code GET /api/devises} — Liste de toutes les devises actives</li>
 *   <li>{@code GET /api/devises/{code}} — Détail d'une devise spécifique</li>
 * </ul>
 *
 * <p><b>Sécurité :</b> Endpoint public — aucune authentification requise.</p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/devises")
@Tag(name = "Devises", description = "Consultation des devises ISO 4217 supportées par BNA-FLUX")
public class DeviseController {

    private final DeviseRepository deviseRepository;

    /**
     * Constructeur avec injection du repository.
     *
     * @param deviseRepository le repository des devises
     */
    public DeviseController(DeviseRepository deviseRepository) {
        this.deviseRepository = deviseRepository;
    }

    // GET /api/devises — Liste toutes les devises actives

    /**
     * Retourne la liste de toutes les devises actives.
     * <p>
     * Les devises inactives (supprimées logiquement) ne sont pas incluses.
     * La réponse est enrobée dans un objet standard avec statut et horodatage.
     * </p>
     *
     * @return la liste des devises actives
     */
    @GetMapping
    @Operation(
            summary = "Lister toutes les devises actives",
            description = "Retourne la liste des devises actives supportées par BNA-FLUX. Endpoint public sans authentification."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des devises récupérée avec succès",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur",
                    content = @Content(schema = @Schema(implementation = ReponseErreur.class)))
    })
    public ResponseEntity<Map<String, Object>> listerDevisesActives() {
        log.debug("GET /api/devises — Consultation de la liste des devises actives");

        List<Devise> devises = deviseRepository.findByActifTrueOrderByCodeAsc();

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("nombre", devises.size());
        reponse.put("devises", devises);
        reponse.put("horodatage", java.time.LocalDateTime.now().toString());

        log.debug("{} devise(s) active(s) retournée(s)", devises.size());
        return ResponseEntity.ok(reponse);
    }

    // GET /api/devises/{code} — Détail d'une devise

    /**
     * Retourne le détail d'une devise spécifique par son code ISO 4217.
     * <p>
     * La recherche est insensible à la casse. Retourne la devise
     * même si elle est inactive (pour consultation administrative).
     * </p>
     *
     * @param code le code ISO 4217 de la devise (ex: TND, EUR, USD)
     * @return le détail de la devise, ou 404 si non trouvée
     */
    @GetMapping("/{code}")
    @Operation(
            summary = "Consulter une devise par son code",
            description = "Retourne le détail d'une devise spécifique. La recherche est insensible à la casse."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Devise trouvée"),
            @ApiResponse(responseCode = "404", description = "Devise non trouvée",
                    content = @Content(schema = @Schema(implementation = ReponseErreur.class)))
    })
    public ResponseEntity<?> consulterDevise(
            @Parameter(description = "Code ISO 4217 de la devise (ex: TND, EUR, USD)", required = true, example = "TND")
            @PathVariable String code) {

        log.debug("GET /api/devises/{} — Consultation d'une devise", code);

        return deviseRepository.findByCodeIgnoreCase(code)
                .map(devise -> {
                    Map<String, Object> reponse = new LinkedHashMap<>();
                    reponse.put("statut", "SUCCES");
                    reponse.put("devise", devise);
                    reponse.put("horodatage", java.time.LocalDateTime.now().toString());
                    return ResponseEntity.ok((Object) reponse);
                })
                .orElseGet(() -> {
                    log.debug("Devise non trouvée : {}", code);
                    return ResponseEntity.status(404).body(
                            ReponseErreur.introuvable("Devise", code)
                    );
                });
    }
}