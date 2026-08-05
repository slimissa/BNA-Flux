package com.bna.flux.controller;

import com.bna.flux.dto.ReponseErreur;
import com.bna.flux.dto.ReponseRegle;
import com.bna.flux.dto.RequeteRegle;
import com.bna.flux.entity.Regle;
import com.bna.flux.exception.ExpressionRegleInvalideException;
import com.bna.flux.service.ServiceRegle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour la gestion des règles de surveillance.
 * <p>
 * Expose les opérations CRUD complètes sur les règles, ainsi que
 * l'activation/désactivation, le basculement et le test d'expressions.
 * </p>
 *
 * <p><b>Endpoints :</b></p>
 * <ul>
 *   <li>{@code GET /api/regles} — Lister toutes les règles</li>
 *   <li>{@code GET /api/regles/{id}} — Détail d'une règle</li>
 *   <li>{@code POST /api/regles} — Créer une règle</li>
 *   <li>{@code PUT /api/regles/{id}} — Modifier une règle</li>
 *   <li>{@code DELETE /api/regles/{id}} — Supprimer une règle</li>
 *   <li>{@code PUT /api/regles/{id}/basculer} — Activer/Désactiver</li>
 *   <li>{@code POST /api/regles/tester} — Tester une expression</li>
 * </ul>
 *
 * <p><b>Sécurité :</b></p>
 * <ul>
 *   <li>Lecture : OPERATEUR, SUPERVISEUR, ADMIN</li>
 *   <li>Écriture : SUPERVISEUR, ADMIN</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/regles")
@Tag(name = "Règles", description = "Gestion des règles de surveillance (CRUD, activation, test)")
public class RegleController {

    private final ServiceRegle serviceRegle;

    public RegleController(ServiceRegle serviceRegle) {
        this.serviceRegle = serviceRegle;
    }

    // GET /api/regles — Lister toutes les règles

    /**
     * Retourne la liste de toutes les règles (actives et inactives).
     *
     * @param categorie filtre optionnel par catégorie
     * @return la liste des règles
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Lister les règles", description = "Retourne toutes les règles de surveillance.")
    public ResponseEntity<Map<String, Object>> lister(
            @Parameter(description = "Filtre par catégorie")
            @RequestParam(required = false) String categorie) {

        log.debug("GET /api/regles — Consultation des règles");

        List<Regle> regles;
        if (categorie != null && !categorie.isEmpty()) {
            regles = serviceRegle.getParCategorie(categorie);
        } else {
            regles = serviceRegle.getToutes();
        }

        List<ReponseRegle> contenu = regles.stream()
                .map(serviceRegle::mapperVersReponse)
                .collect(Collectors.toList());

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("nombre", contenu.size());
        reponse.put("regles", contenu);
        reponse.put("horodatage", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(reponse);
    }

    // GET /api/regles/{id} — Détail d'une règle

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Consulter une règle", description = "Retourne le détail d'une règle.")
    public ResponseEntity<?> consulter(@PathVariable Long id) {
        log.debug("GET /api/regles/{} — Consultation", id);

        try {
            Regle regle = serviceRegle.getParId(id);
            ReponseRegle reponseRegle = serviceRegle.mapperVersReponse(regle);

            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("regle", reponseRegle);
            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(reponse);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ReponseErreur.introuvable("Règle", id));
        }
    }

    // POST /api/regles — Créer une règle

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Créer une règle", description = "Crée une nouvelle règle de surveillance avec validation SpEL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Règle créée avec succès",
                    content = @Content(schema = @Schema(implementation = ReponseRegle.class))),
            @ApiResponse(responseCode = "400", description = "Expression SpEL invalide ou nom dupliqué",
                    content = @Content(schema = @Schema(implementation = ReponseErreur.class)))
    })
    public ResponseEntity<?> creer(@Valid @RequestBody RequeteRegle requete) {
        log.info("POST /api/regles — Création : {}", requete.getNom());

        try {
            Regle regle = serviceRegle.creer(requete);
            ReponseRegle reponseRegle = serviceRegle.mapperVersReponse(regle);

            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("message", "Règle créée avec succès");
            reponse.put("regle", reponseRegle);
            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            return ResponseEntity.status(HttpStatus.CREATED).body(reponse);

        } catch (ExpressionRegleInvalideException e) {
            return ResponseEntity.badRequest()
                    .body(ReponseErreur.of("REGLE_SYNTAXE_INVALIDE", e.getMessage(), e.getDetails()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ReponseErreur.of("REGLE_DUPLIQUEE", e.getMessage()));
        }
    }

    // PUT /api/regles/{id} — Modifier une règle

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Modifier une règle", description = "Modifie une règle existante.")
    public ResponseEntity<?> modifier(@PathVariable Long id, @Valid @RequestBody RequeteRegle requete) {
        log.info("PUT /api/regles/{} — Modification : {}", id, requete.getNom());

        try {
            Regle regle = serviceRegle.modifier(id, requete);
            ReponseRegle reponseRegle = serviceRegle.mapperVersReponse(regle);

            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("message", "Règle modifiée avec succès");
            reponse.put("regle", reponseRegle);
            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(reponse);

        } catch (ExpressionRegleInvalideException e) {
            return ResponseEntity.badRequest()
                    .body(ReponseErreur.of("REGLE_SYNTAXE_INVALIDE", e.getMessage(), e.getDetails()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ReponseErreur.of("REGLE_INTROUVABLE", e.getMessage()));
        }
    }

    // DELETE /api/regles/{id} — Supprimer une règle

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Supprimer une règle", description = "Supprime définitivement une règle. Préférer la désactivation.")
    public ResponseEntity<?> supprimer(@PathVariable Long id) {
        log.warn("DELETE /api/regles/{} — Suppression", id);

        try {
            serviceRegle.supprimer(id);

            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("message", "Règle supprimée avec succès");
            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(reponse);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ReponseErreur.introuvable("Règle", id));
        }
    }

    // PUT /api/regles/{id}/basculer — Activer/Désactiver

    @PutMapping("/{id}/basculer")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Activer/Désactiver une règle", description = "Bascule l'état actif/inactif d'une règle.")
    public ResponseEntity<?> basculer(@PathVariable Long id) {
        log.info("PUT /api/regles/{}/basculer — Basculement", id);

        try {
            Regle regle = serviceRegle.basculer(id);

            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("message", regle.isActif() ? "Règle activée" : "Règle désactivée");
            reponse.put("actif", regle.isActif());
            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(reponse);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ReponseErreur.introuvable("Règle", id));
        }
    }

    // POST /api/regles/tester — Tester une expression

    /**
     * Teste une expression SpEL contre une transaction existante.
     * <p>
     * Utile pour valider une expression avant de créer ou modifier une règle.
     * </p>
     *
     * @param requete contient l'expression et l'ID de la transaction test
     * @return le résultat de l'évaluation
     */
    @PostMapping("/tester")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Tester une expression", description = "Teste une expression SpEL contre une transaction existante.")
    public ResponseEntity<?> tester(@RequestBody Map<String, Object> requete) {
        String expression = (String) requete.get("expression");
        Long transactionId = requete.get("transactionId") != null
                ? Long.valueOf(requete.get("transactionId").toString()) : null;

        log.info("POST /api/regles/tester — Test d'expression : {}", expression);

        if (expression == null || expression.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ReponseErreur.of("VALIDATION_ECHOUEE", "L'expression est obligatoire."));
        }

        try {
            // Valider la syntaxe d'abord
            serviceRegle.validerExpressionSpEL(expression);

            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("syntaxeValide", true);
            reponse.put("message", "L'expression est syntaxiquement valide.");

            if (transactionId != null) {
                reponse.put("transactionId", transactionId);
                reponse.put("message", reponse.get("message") + " Test contre transaction ID " + transactionId + ".");
            }

            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(reponse);

        } catch (ExpressionRegleInvalideException e) {
            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("syntaxeValide", false);
            reponse.put("erreur", e.getMessage());
            reponse.put("details", e.getDetails());
            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(reponse);
        }
    }

    // GET /api/regles/categories — Catégories distinctes

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Catégories de règles", description = "Retourne la liste des catégories distinctes.")
    public ResponseEntity<Map<String, Object>> categories() {
        log.debug("GET /api/regles/categories");

        List<String> categories = serviceRegle.getCategories();

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("categories", categories);
        reponse.put("horodatage", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(reponse);
    }
}