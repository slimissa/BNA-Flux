package com.bna.flux.service;

import com.bna.flux.dto.ReponseRegle;
import com.bna.flux.dto.RequeteRegle;
import com.bna.flux.entity.Regle;
import com.bna.flux.exception.AccesRefuseException;
import com.bna.flux.exception.ExpressionRegleInvalideException;
import com.bna.flux.repository.RegleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service de gestion des règles de surveillance.
 * <p>
 * Responsable du cycle de vie complet des règles : création, modification,
 * consultation, activation/désactivation, et validation syntaxique des
 * expressions SpEL.
 * </p>
 *
 * <p><b>Règles de gestion :</b></p>
 * <ul>
 *   <li>Le nom d'une règle doit être unique.</li>
 *   <li>L'expression SpEL doit être syntaxiquement valide avant sauvegarde.</li>
 *   <li>La suppression est logique (désactivation) — jamais physique.</li>
 *   <li>La modification d'une règle invalide son cache SpEL.</li>
 *   <li>Seuls les rôles SUPERVISEUR et ADMIN peuvent créer/modifier/supprimer.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Service
public class ServiceRegle {

    private final RegleRepository regleRepository;
    private final MoteurRegles moteurRegles;
    private final SpelExpressionParser spelParser;

    /**
     * Constructeur avec injection de dépendances.
     *
     * @param regleRepository le repository des règles
     * @param moteurRegles    le moteur de règles (pour l'invalidation du cache)
     */
    public ServiceRegle(RegleRepository regleRepository, MoteurRegles moteurRegles) {
        this.regleRepository = regleRepository;
        this.moteurRegles = moteurRegles;
        this.spelParser = new SpelExpressionParser();
    }

    // Création

    /**
     * Crée une nouvelle règle de surveillance.
     * <p>
     * Valide la syntaxe de l'expression SpEL avant sauvegarde.
     * Vérifie l'unicité du nom.
     * </p>
     *
     * @param requete le DTO contenant les données de la règle
     * @return la règle créée
     * @throws ExpressionRegleInvalideException si l'expression SpEL est invalide
     * @throws IllegalStateException si une règle avec le même nom existe déjà
     */
    @Transactional
    public Regle creer(RequeteRegle requete) {
        log.info("Création d'une nouvelle règle : {}", requete.getNom());

        // Vérifier l'unicité du nom
        if (regleRepository.existsByNom(requete.getNom())) {
            throw new IllegalStateException("Une règle avec le nom '" + requete.getNom() + "' existe déjà.");
        }

        // Valider l'expression SpEL
        validerExpressionSpEL(requete.getExpressionCondition());

        // Créer l'entité
        Regle regle = Regle.builder()
                .nom(requete.getNom())
                .description(requete.getDescription())
                .expressionCondition(requete.getExpressionCondition())
                .severite(requete.getSeverite())
                .contributionScore(requete.getContributionScore())
                .typeRegle(requete.getTypeRegle())
                .categorie(requete.getCategorie())
                .priorite(requete.getPriorite())
                .actif(requete.isActif())
                .build();

        Regle regleSauvegardee = regleRepository.save(regle);
        log.info("Règle créée avec succès — ID: {}, Nom: {}", regleSauvegardee.getId(), regleSauvegardee.getNom());

        // Invalider le cache SpEL si la règle est active
        if (regleSauvegardee.isActif()) {
            moteurRegles.invaliderExpression(regleSauvegardee.getExpressionCondition());
        }

        return regleSauvegardee;
    }

    // Modification

    /**
     * Modifie une règle existante.
     * <p>
     * Conserve l'ID et la date de création. Met à jour la date de modification.
     * Invalide le cache SpEL si l'expression a changé.
     * </p>
     *
     * @param id      l'identifiant de la règle à modifier
     * @param requete le DTO contenant les nouvelles données
     * @return la règle modifiée
     * @throws IllegalStateException si la règle n'existe pas
     * @throws ExpressionRegleInvalideException si la nouvelle expression est invalide
     */
    @Transactional
    public Regle modifier(Long id, RequeteRegle requete) {
        log.info("Modification de la règle — ID: {}", id);

        Regle regle = regleRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Règle introuvable avec l'ID : " + id));

        // Vérifier l'unicité du nom (en excluant cette règle)
        if (!regle.getNom().equals(requete.getNom())
                && regleRepository.existsByNomAndIdNot(requete.getNom(), id)) {
            throw new IllegalStateException("Une autre règle avec le nom '" + requete.getNom() + "' existe déjà.");
        }

        // Valider la nouvelle expression SpEL
        validerExpressionSpEL(requete.getExpressionCondition());

        // Invalider l'ancienne expression dans le cache
        String ancienneExpression = regle.getExpressionCondition();
        if (!ancienneExpression.equals(requete.getExpressionCondition())) {
            moteurRegles.invaliderExpression(ancienneExpression);
        }

        // Mettre à jour les champs
        regle.setNom(requete.getNom());
        regle.setDescription(requete.getDescription());
        regle.setExpressionCondition(requete.getExpressionCondition());
        regle.setSeverite(requete.getSeverite());
        regle.setContributionScore(requete.getContributionScore());
        regle.setTypeRegle(requete.getTypeRegle());
        regle.setCategorie(requete.getCategorie());
        regle.setPriorite(requete.getPriorite());
        regle.setActif(requete.isActif());

        Regle regleSauvegardee = regleRepository.save(regle);
        log.info("Règle modifiée avec succès — ID: {}, Nom: {}", regleSauvegardee.getId(), regleSauvegardee.getNom());

        // Invalider la nouvelle expression dans le cache pour forcer la recompilation
        moteurRegles.invaliderExpression(regleSauvegardee.getExpressionCondition());

        return regleSauvegardee;
    }

    // Consultation

    /**
     * Récupère une règle par son identifiant.
     *
     * @param id l'identifiant de la règle
     * @return la règle
     * @throws IllegalStateException si la règle n'existe pas
     */
    public Regle getParId(Long id) {
        return regleRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Règle introuvable avec l'ID : " + id));
    }

    /**
     * Récupère toutes les règles avec pagination.
     *
     * @param pageable la pagination
     * @return une page de règles
     */
    public Page<Regle> getToutes(Pageable pageable) {
        return regleRepository.findAll(pageable);
    }

    /**
     * Récupère toutes les règles (sans pagination).
     *
     * @return la liste complète des règles
     */
    public List<Regle> getToutes() {
        return regleRepository.findAllByOrderByPrioriteAsc();
    }

    /**
     * Recherche des règles par nom (partiel, insensible à la casse).
     *
     * @param nom le nom ou partie du nom
     * @return la liste des règles correspondantes
     */
    public List<Regle> rechercherParNom(String nom) {
        return regleRepository.findByNomContainingIgnoreCase(nom);
    }

    /**
     * Récupère les règles par sévérité.
     *
     * @param severite la sévérité
     * @return la liste des règles correspondantes
     */
    public List<Regle> getParSeverite(Regle.Severite severite) {
        return regleRepository.findBySeveriteOrderByPrioriteAsc(severite);
    }

    /**
     * Récupère les règles par catégorie.
     *
     * @param categorie la catégorie
     * @return la liste des règles correspondantes
     */
    public List<Regle> getParCategorie(String categorie) {
        return regleRepository.findByCategorieOrderByPrioriteAsc(categorie);
    }

    // Activation / Désactivation

    /**
     * Active une règle.
     *
     * @param id l'identifiant de la règle
     * @return la règle activée
     */
    @Transactional
    public Regle activer(Long id) {
        Regle regle = getParId(id);
        regle.activer();
        Regle regleSauvegardee = regleRepository.save(regle);
        log.info("Règle activée — ID: {}, Nom: {}", id, regleSauvegardee.getNom());
        return regleSauvegardee;
    }

    /**
     * Désactive une règle (suppression logique).
     *
     * @param id l'identifiant de la règle
     * @return la règle désactivée
     */
    @Transactional
    public Regle desactiver(Long id) {
        Regle regle = getParId(id);
        regle.desactiver();
        Regle regleSauvegardee = regleRepository.save(regle);
        // Invalider l'expression dans le cache
        moteurRegles.invaliderExpression(regleSauvegardee.getExpressionCondition());
        log.info("Règle désactivée — ID: {}, Nom: {}", id, regleSauvegardee.getNom());
        return regleSauvegardee;
    }

    /**
     * Bascule l'état actif/inactif d'une règle.
     *
     * @param id l'identifiant de la règle
     * @return la règle avec son nouvel état
     */
    @Transactional
    public Regle basculer(Long id) {
        Regle regle = getParId(id);
        regle.basculer();
        Regle regleSauvegardee = regleRepository.save(regle);
        // Invalider l'expression dans le cache
        moteurRegles.invaliderExpression(regleSauvegardee.getExpressionCondition());
        log.info("Règle basculée — ID: {}, Nouvel état: {}", id, regleSauvegardee.isActif() ? "Active" : "Inactive");
        return regleSauvegardee;
    }

    // Suppression

    /**
     * Supprime définitivement une règle.
     * <p>
     * <b>Attention :</b> Cette opération est irréversible.
     * Préférer la désactivation pour conserver l'historique.
     * </p>
     *
     * @param id l'identifiant de la règle
     */
    @Transactional
    public void supprimer(Long id) {
        Regle regle = getParId(id);
        // Invalider l'expression dans le cache avant suppression
        moteurRegles.invaliderExpression(regle.getExpressionCondition());
        regleRepository.delete(regle);
        log.warn("Règle supprimée définitivement — ID: {}, Nom: {}", id, regle.getNom());
    }

    // Validation SpEL

    /**
     * Valide la syntaxe d'une expression SpEL.
     * <p>
     * Tente de compiler l'expression avec le parser SpEL.
     * Si la compilation échoue, une exception est levée avec le détail de l'erreur.
     * </p>
     *
     * @param expression l'expression SpEL à valider
     * @throws ExpressionRegleInvalideException si l'expression est syntaxiquement invalide
     */
    public void validerExpressionSpEL(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new ExpressionRegleInvalideException(expression, "L'expression ne peut pas être vide.");
        }

        try {
            // Tenter de compiler l'expression
            spelParser.parseExpression(expression.trim());
            log.debug("Expression SpEL valide : {}", expression);
        } catch (ParseException e) {
            log.warn("Expression SpEL invalide : {} — Erreur : {}", expression, e.getMessage());
            throw new ExpressionRegleInvalideException(
                    expression,
                    e.getMessage(),
                    e.getPosition()
            );
        }
    }

    // Mapping DTO

    /**
     * Mappe une entité {@link Regle} vers un DTO {@link ReponseRegle}.
     *
     * @param regle l'entité règle
     * @return le DTO réponse
     */
    public ReponseRegle mapperVersReponse(Regle regle) {
        if (regle == null) {
            return null;
        }

        return ReponseRegle.builder()
                .id(regle.getId())
                .nom(regle.getNom())
                .description(regle.getDescription())
                .expressionCondition(regle.getExpressionCondition())
                .severite(regle.getSeverite())
                .severiteLabel(getSeveriteLabel(regle.getSeverite()))
                .contributionScore(regle.getContributionScore())
                .typeRegle(regle.getTypeRegle())
                .typeRegleLabel(getTypeRegleLabel(regle.getTypeRegle()))
                .categorie(regle.getCategorie())
                .priorite(regle.getPriorite())
                .actif(regle.isActif())
                .etatLabel(regle.isActif() ? "Active" : "Inactive")
                .dateCreation(regle.getDateCreation())
                .dateModification(regle.getDateModification())
                .build();
    }

    /**
     * Retourne le libellé français d'une sévérité.
     *
     * @param severite la sévérité
     * @return le libellé
     */
    private String getSeveriteLabel(Regle.Severite severite) {
        return switch (severite) {
            case FAIBLE -> "Faible";
            case MOYEN -> "Moyen";
            case ELEVE -> "Élevé";
            case CRITIQUE -> "Critique";
        };
    }

    /**
     * Retourne le libellé français d'un type de règle.
     *
     * @param typeRegle le type de règle
     * @return le libellé
     */
    private String getTypeRegleLabel(Regle.TypeRegle typeRegle) {
        return switch (typeRegle) {
            case PREVENTION -> "Prévention";
            case ALERTE -> "Alerte";
            case AUTO_REJET -> "Auto-rejet";
        };
    }

    /**
     * Récupère les catégories distinctes de règles.
     *
     * @return la liste des catégories
     */
    public List<String> getCategories() {
        return regleRepository.findDistinctCategories();
    }

    /**
     * Compte le nombre total de règles.
     *
     * @return le nombre de règles
     */
    public long compter() {
        return regleRepository.count();
    }

    /**
     * Compte le nombre de règles actives.
     *
     * @return le nombre de règles actives
     */
    public long compterActives() {
        return regleRepository.countByActifTrue();
    }
}