package com.bna.flux.controller;

import com.bna.flux.dto.ReponseDisjoncteur;
import com.bna.flux.dto.ReponseErreur;
import com.bna.flux.entity.EtatDisjoncteur;
import com.bna.flux.entity.EtatDisjoncteur.Etat;
import com.bna.flux.service.ServiceDisjoncteur;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Contrôleur REST pour la consultation et la gestion des disjoncteurs.
 * <p>
 * Les disjoncteurs (Circuit Breakers) sont des mécanismes de protection
 * automatique qui bloquent les transactions lorsqu'un nombre anormal
 * d'échecs est détecté pour une cible. Ce contrôleur permet de consulter
 * leur état et de les réinitialiser manuellement.
 * </p>
 *
 * <p><b>Endpoints :</b></p>
 * <ul>
 *   <li>{@code GET /api/disjoncteurs} — Lister tous les disjoncteurs</li>
 *   <li>{@code GET /api/disjoncteurs/{id}} — Détail d'un disjoncteur</li>
 *   <li>{@code PUT /api/disjoncteurs/{id}/reinitialiser} — Réinitialiser</li>
 * </ul>
 *
 * <p><b>Sécurité :</b></p>
 * <ul>
 *   <li>Lecture : OPERATEUR, SUPERVISEUR, ADMIN</li>
 *   <li>Réinitialisation : SUPERVISEUR, ADMIN</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/disjoncteurs")
@Tag(name = "Disjoncteurs", description = "Consultation et gestion des disjoncteurs (Circuit Breakers)")
public class DisjoncteurController {

    private final ServiceDisjoncteur serviceDisjoncteur;

    public DisjoncteurController(ServiceDisjoncteur serviceDisjoncteur) {
        this.serviceDisjoncteur = serviceDisjoncteur;
    }

    // GET /api/disjoncteurs — Lister tous les disjoncteurs

    /**
     * Retourne la liste de tous les disjoncteurs avec filtrage optionnel par état.
     *
     * @param etat filtre par état (FERME, OUVERT, MI_OUVERT) — optionnel
     * @return la liste des disjoncteurs
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Lister les disjoncteurs", description = "Retourne tous les disjoncteurs avec leur état actuel.")
    public ResponseEntity<Map<String, Object>> lister(
            @Parameter(description = "Filtre par état (FERME, OUVERT, MI_OUVERT)")
            @RequestParam(required = false) String etat) {

        log.debug("GET /api/disjoncteurs — Consultation (filtre état: {})", etat);

        List<EtatDisjoncteur> disjoncteurs;

        if (etat != null && !etat.isEmpty()) {
            try {
                Etat etatEnum = Etat.valueOf(etat.toUpperCase());
                disjoncteurs = serviceDisjoncteur.getParEtat(etatEnum);
            } catch (IllegalArgumentException e) {
                disjoncteurs = serviceDisjoncteur.getTous();
            }
        } else {
            disjoncteurs = serviceDisjoncteur.getTous();
        }

        List<ReponseDisjoncteur> contenu = disjoncteurs.stream()
                .map(this::mapperDisjoncteur)
                .collect(Collectors.toList());

        // Statistiques
        long ouverts = serviceDisjoncteur.compterParEtat(Etat.OUVERT);
        long miOuverts = serviceDisjoncteur.compterParEtat(Etat.MI_OUVERT);
        long fermes = serviceDisjoncteur.compterParEtat(Etat.FERME);

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("nombre", contenu.size());
        reponse.put("disjoncteurs", contenu);
        reponse.put("statistiques", Map.of(
                "ouverts", ouverts,
                "miOuverts", miOuverts,
                "fermes", fermes,
                "totalEchecs", serviceDisjoncteur.getTotalEchecs()
        ));
        reponse.put("horodatage", java.time.LocalDateTime.now().toString());

        log.debug("{} disjoncteur(s) retourné(s) — {} ouverts, {} mi-ouverts, {} fermés",
                contenu.size(), ouverts, miOuverts, fermes);

        return ResponseEntity.ok(reponse);
    }

    // GET /api/disjoncteurs/{id} — Détail d'un disjoncteur

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Consulter un disjoncteur", description = "Retourne le détail d'un disjoncteur spécifique.")
    public ResponseEntity<?> consulter(@PathVariable Long id) {
        log.debug("GET /api/disjoncteurs/{} — Consultation", id);

        return serviceDisjoncteur.getParId(id)
                .map(disjoncteur -> {
                    ReponseDisjoncteur reponseDisjoncteur = mapperDisjoncteur(disjoncteur);

                    Map<String, Object> reponse = new LinkedHashMap<>();
                    reponse.put("statut", "SUCCES");
                    reponse.put("disjoncteur", reponseDisjoncteur);
                    reponse.put("horodatage", java.time.LocalDateTime.now().toString());

                    return ResponseEntity.ok((Object) reponse);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ReponseErreur.introuvable("Disjoncteur", id)));
    }

    // PUT /api/disjoncteurs/{id}/reinitialiser — Réinitialiser

    /**
     * Réinitialise manuellement un disjoncteur (retour à l'état FERMÉ).
     * <p>
     * Action réservée aux SUPERVISEUR et ADMIN. Le compteur d'échecs
     * est remis à zéro et le disjoncteur repasse à l'état FERMÉ.
     * </p>
     *
     * @param id l'identifiant du disjoncteur
     * @return le disjoncteur réinitialisé
     */
    @PutMapping("/{id}/reinitialiser")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Réinitialiser un disjoncteur",
            description = "Réinitialise manuellement un disjoncteur à l'état FERMÉ. Réservé aux SUPERVISEUR et ADMIN.")
    @ApiResponse(responseCode = "400", description = "Disjoncteur introuvable ou déjà fermé",
            content = @Content(schema = @Schema(implementation = ReponseErreur.class)))
    public ResponseEntity<?> reinitialiser(@PathVariable Long id) {
        log.warn("PUT /api/disjoncteurs/{}/reinitialiser — Réinitialisation manuelle", id);

        try {
            EtatDisjoncteur disjoncteur = serviceDisjoncteur.reinitialiser(id);
            ReponseDisjoncteur reponseDisjoncteur = mapperDisjoncteur(disjoncteur);

            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("statut", "SUCCES");
            reponse.put("message", "Disjoncteur réinitialisé avec succès — état FERMÉ");
            reponse.put("disjoncteur", reponseDisjoncteur);
            reponse.put("horodatage", java.time.LocalDateTime.now().toString());

            log.info("Disjoncteur {} réinitialisé manuellement", id);

            return ResponseEntity.ok(reponse);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ReponseErreur.introuvable("Disjoncteur", id));
        }
    }

    // GET /api/disjoncteurs/ouverts — Disjoncteurs ouverts (raccourci)

    @GetMapping("/ouverts")
    @PreAuthorize("hasAnyRole('OPERATEUR', 'SUPERVISEUR', 'ADMIN')")
    @Operation(summary = "Disjoncteurs ouverts", description = "Retourne uniquement les disjoncteurs actuellement ouverts.")
    public ResponseEntity<Map<String, Object>> disjoncteursOuverts() {
        log.debug("GET /api/disjoncteurs/ouverts");

        List<EtatDisjoncteur> ouverts = serviceDisjoncteur.getDisjoncteursOuverts();
        List<ReponseDisjoncteur> contenu = ouverts.stream()
                .map(this::mapperDisjoncteur)
                .collect(Collectors.toList());

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("statut", "SUCCES");
        reponse.put("nombre", contenu.size());
        reponse.put("disjoncteurs", contenu);
        reponse.put("horodatage", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(reponse);
    }

    // Méthode privée de mapping

    /**
     * Mappe une entité {@link EtatDisjoncteur} vers un DTO {@link ReponseDisjoncteur}.
     *
     * @param disjoncteur l'entité disjoncteur
     * @return le DTO réponse
     */
    private ReponseDisjoncteur mapperDisjoncteur(EtatDisjoncteur disjoncteur) {
        if (disjoncteur == null) {
            return null;
        }

        // Calculer le temps restant avant passage automatique en MI_OUVERT
        Long tempsRestant = null;
        if (disjoncteur.estOuvert() && disjoncteur.getDateDerniereOuverture() != null) {
            LocalDateTime expiration = disjoncteur.getDateDerniereOuverture()
                    .plusMinutes(disjoncteur.getDelaiOuvertureMinutes());
            tempsRestant = java.time.Duration.between(LocalDateTime.now(), expiration).toMinutes();
            tempsRestant = Math.max(0, tempsRestant);
        }

        return ReponseDisjoncteur.builder()
                .id(disjoncteur.getId())
                .nom(disjoncteur.getNom() != null ? disjoncteur.getNom() : disjoncteur.genererNom())
                .typeCible(disjoncteur.getTypeCible())
                .identifiantCible(disjoncteur.getIdentifiantCible())
                .etat(disjoncteur.getEtat())
                .nombreEchecs(disjoncteur.getNombreEchecs())
                .seuilEchecs(disjoncteur.getSeuilEchecs())
                .delaiOuvertureMinutes(disjoncteur.getDelaiOuvertureMinutes())
                .fenetreHeures(disjoncteur.getFenetreHeures())
                .dateDerniereOuverture(disjoncteur.getDateDerniereOuverture())
                .dateDerniereFermeture(disjoncteur.getDateDerniereFermeture())
                .dateDernierEchec(disjoncteur.getDateDernierEchec())
                .dateCreation(disjoncteur.getDateCreation())
                .dateModification(disjoncteur.getDateModification())
                .peutEtreReinitialise(disjoncteur.estOuvert() || disjoncteur.estMiOuvert())
                .tempsRestantAvantMiOuvert(tempsRestant)
                .build();
    }
}