package com.bna.flux.service;

import com.bna.flux.entity.EtatDisjoncteur;
import com.bna.flux.entity.EtatDisjoncteur.Etat;
import com.bna.flux.entity.EtatDisjoncteur.TypeCible;
import com.bna.flux.exception.DisjoncteurOuvertException;
import com.bna.flux.repository.EtatDisjoncteurRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service de gestion des disjoncteurs (Circuit Breakers) de BNA-FLUX.
 * <p>
 * Implémente le pattern Circuit Breaker pour protéger le système contre
 * les attaques coordonnées ou les défaillances en cascade. Lorsqu'un nombre
 * anormal de transactions est bloqué pour une cible (compte, agence, canal),
 * le disjoncteur s'ouvre et bloque automatiquement toutes les transactions
 * suivantes pour cette cible.
 * </p>
 *
 * <p><b>Cycle de vie :</b></p>
 * <pre>
 * FERMÉ ──(nb échecs ≥ seuil)──▶ OUVERT ──(délai écoulé)──▶ MI_OUVERT
 *   ▲                                                          │
 *   └────────────(test réussi)─────────────────────────────────┘
 *   └────────────(test échoué)──▶ OUVERT (retour)
 * </pre>
 *
 * <p><b>Utilisation dans le pipeline :</b></p>
 * <ul>
 *   <li><b>Stage 1 (Validation)</b> — {@link #verifierAvantTransaction(TypeCible, String)}
 *       bloque la transaction si le disjoncteur est OUVERT.</li>
 *   <li><b>Stage 4 (Notation)</b> — {@link #enregistrerEchec(TypeCible, String)}
 *       incrémente le compteur d'échecs quand une transaction est BLOQUEE.</li>
 * </ul>
 *
 * <p><b>Tâche planifiée :</b> Toutes les minutes, vérifie si des disjoncteurs
 * OUVERT doivent passer en MI_OUVERT (délai écoulé).</p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Service
public class ServiceDisjoncteur {

    private final EtatDisjoncteurRepository disjoncteurRepository;

    /**
     * Seuil d'échecs par défaut avant ouverture.
     */
    @Value("${bna.disjoncteur.seuil-echecs:3}")
    private int seuilEchecs;

    /**
     * Fenêtre de temps pour le comptage des échecs (en heures).
     */
    @Value("${bna.disjoncteur.fenetre-heures:24}")
    private int fenetreHeures;

    /**
     * Délai avant passage automatique en MI_OUVERT (en minutes).
     */
    @Value("${bna.disjoncteur.delai-ouverture-minutes:60}")
    private int delaiOuvertureMinutes;

    /**
     * Activation globale des disjoncteurs.
     */
    @Value("${bna.disjoncteur.actif:true}")
    private boolean disjoncteursActifs;

    // Constructeur

    public ServiceDisjoncteur(EtatDisjoncteurRepository disjoncteurRepository) {
        this.disjoncteurRepository = disjoncteurRepository;
    }

    // Vérification avant transaction (Stage 1 du pipeline)

    /**
     * Vérifie si un disjoncteur bloque les transactions pour une cible donnée.
     * <p>
     * Appelé au Stage 1 (Validation) du pipeline avant tout traitement.
     * Si le disjoncteur est OUVERT, une exception est levée immédiatement.
     * Si le disjoncteur est MI_OUVERT, une transaction test est autorisée.
     * </p>
     *
     * @param typeCible        le type de cible (COMPTE_SOURCE, COMPTE_DESTINATION, AGENCE, CANAL)
     * @param identifiantCible l'identifiant de la cible (RIB, code agence, canal)
     * @throws DisjoncteurOuvertException si le disjoncteur est OUVERT
     */
    public void verifierAvantTransaction(TypeCible typeCible, String identifiantCible)
            throws DisjoncteurOuvertException {

        if (!disjoncteursActifs) {
            return;
        }

        Optional<EtatDisjoncteur> disjoncteurOpt = disjoncteurRepository
                .findByTypeCibleAndIdentifiantCible(typeCible, identifiantCible);

        if (disjoncteurOpt.isPresent()) {
            EtatDisjoncteur disjoncteur = disjoncteurOpt.get();

            // Tenter un passage automatique OUVERT → MI_OUVERT si le délai est écoulé
            if (disjoncteur.getEtat() == Etat.OUVERT && disjoncteur.tenterPassageMiOuvert()) {
                disjoncteurRepository.passerEnMiOuvert(disjoncteur.getId());
                log.info("Disjoncteur passé automatiquement en MI_OUVERT : {} {}", typeCible, identifiantCible);
                // En MI_OUVERT, on autorise la transaction test
                return;
            }

            // Si le disjoncteur est OUVERT, bloquer la transaction
            if (disjoncteur.estOuvert()) {
                LocalDateTime dateOuverture = disjoncteur.getDateDerniereOuverture();
                Long minutesRestantes = null;

                if (dateOuverture != null) {
                    LocalDateTime delaiExpiration = dateOuverture.plusMinutes(delaiOuvertureMinutes);
                    minutesRestantes = java.time.Duration.between(LocalDateTime.now(), delaiExpiration).toMinutes();
                    minutesRestantes = Math.max(0, minutesRestantes);
                }

                log.warn("Transaction bloquée — disjoncteur OUVERT : {} {} ({} échecs, test dans {} min)",
                        typeCible, identifiantCible, disjoncteur.getNombreEchecs(), minutesRestantes);

                throw new DisjoncteurOuvertException(
                        typeCible.name(),
                        identifiantCible,
                        dateOuverture,
                        minutesRestantes
                );
            }
        }
        // Si pas de disjoncteur trouvé, tout va bien — circuit fermé implicitement
    }

    /**
     * Vérifie les deux RIBs d'une transaction (source et destination).
     *
     * @param ribSource      le RIB source
     * @param ribDestination le RIB destination
     * @throws DisjoncteurOuvertException si un disjoncteur est ouvert
     */
    public void verifierRibsTransaction(String ribSource, String ribDestination)
            throws DisjoncteurOuvertException {
        verifierAvantTransaction(TypeCible.COMPTE_SOURCE, ribSource);
        verifierAvantTransaction(TypeCible.COMPTE_DESTINATION, ribDestination);
    }

    // Enregistrement d'échec (Stage 4 du pipeline)

    /**
     * Enregistre un échec pour une cible après qu'une transaction a été bloquée.
     * <p>
     * Appelé au Stage 4 (Notation) quand le score de risque classe la transaction
     * comme BLOQUEE. Incrémente le compteur et ouvre le disjoncteur si le seuil
     * est atteint.
     * </p>
     *
     * @param typeCible        le type de cible
     * @param identifiantCible l'identifiant de la cible
     * @return le disjoncteur mis à jour ou créé
     */
    @Transactional
    public EtatDisjoncteur enregistrerEchec(TypeCible typeCible, String identifiantCible) {
        if (!disjoncteursActifs) {
            return null;
        }

        EtatDisjoncteur disjoncteur = obtenirOuCreer(typeCible, identifiantCible);

        boolean vientDeSouvrir = disjoncteur.enregistrerEchec();

        if (vientDeSouvrir) {
            disjoncteurRepository.incrementerEchecs(disjoncteur.getId());
            log.warn("DISJONCTEUR OUVERT — {} {} : {}/{} échecs",
                    typeCible, identifiantCible,
                    disjoncteur.getNombreEchecs() + 1,
                    disjoncteur.getSeuilEchecs());
        } else {
            disjoncteurRepository.incrementerEchecs(disjoncteur.getId());
            log.debug("Échec enregistré — {} {} : {}/{}",
                    typeCible, identifiantCible,
                    disjoncteur.getNombreEchecs() + 1,
                    disjoncteur.getSeuilEchecs());
        }

        return disjoncteur;
    }

    /**
     * Enregistre un échec pour les deux RIBs d'une transaction bloquée.
     *
     * @param ribSource      le RIB source
     * @param ribDestination le RIB destination
     */
    public void enregistrerEchecTransaction(String ribSource, String ribDestination) {
        enregistrerEchec(TypeCible.COMPTE_SOURCE, ribSource);
        enregistrerEchec(TypeCible.COMPTE_DESTINATION, ribDestination);
    }

    // Gestion du test MI_OUVERT (après la transaction test)

    /**
     * Confirme que le test en MI_OUVERT a réussi — le disjoncteur repasse à FERMÉ.
     *
     * @param typeCible        le type de cible
     * @param identifiantCible l'identifiant de la cible
     */
    @Transactional
    public void confirmerTestReussi(TypeCible typeCible, String identifiantCible) {
        Optional<EtatDisjoncteur> disjoncteurOpt = disjoncteurRepository
                .findByTypeCibleAndIdentifiantCible(typeCible, identifiantCible);

        if (disjoncteurOpt.isPresent() && disjoncteurOpt.get().estMiOuvert()) {
            disjoncteurRepository.confirmerTestReussi(disjoncteurOpt.get().getId());
            log.info("Disjoncteur refermé après test réussi : {} {}", typeCible, identifiantCible);
        }
    }

    /**
     * Confirme que le test en MI_OUVERT a échoué — le disjoncteur retourne à OUVERT.
     *
     * @param typeCible        le type de cible
     * @param identifiantCible l'identifiant de la cible
     */
    @Transactional
    public void confirmerTestEchoue(TypeCible typeCible, String identifiantCible) {
        Optional<EtatDisjoncteur> disjoncteurOpt = disjoncteurRepository
                .findByTypeCibleAndIdentifiantCible(typeCible, identifiantCible);

        if (disjoncteurOpt.isPresent() && disjoncteurOpt.get().estMiOuvert()) {
            disjoncteurRepository.confirmerTestEchoue(disjoncteurOpt.get().getId());
            log.warn("Disjoncteur retourné à OUVERT après échec du test : {} {}", typeCible, identifiantCible);
        }
    }

    // Tâche planifiée — Passage automatique OUVERT → MI_OUVERT

    /**
     * Vérifie périodiquement si des disjoncteurs OUVERT doivent passer en MI_OUVERT.
     * <p>
     * Exécuté toutes les 60 secondes. Parcourt les disjoncteurs ouverts depuis
     * plus de {@code delaiOuvertureMinutes} minutes et les fait passer en MI_OUVERT.
     * </p>
     */
    @Scheduled(fixedRateString = "${bna.disjoncteur.intervalle-verification-ms:60000}")
    @Transactional
    public void verifierPassagesAutoMiOuvert() {
        if (!disjoncteursActifs) {
            return;
        }

        LocalDateTime dateLimite = LocalDateTime.now().minusMinutes(delaiOuvertureMinutes);

        List<EtatDisjoncteur> eligibles = disjoncteurRepository
                .findDisjoncteursOuvertsEligiblesMiOuvert(dateLimite);

        for (EtatDisjoncteur disjoncteur : eligibles) {
            disjoncteurRepository.passerEnMiOuvert(disjoncteur.getId());
            log.info("Disjoncteur passé automatiquement en MI_OUVERT : {} {} (ouvert depuis {})",
                    disjoncteur.getTypeCible(),
                    disjoncteur.getIdentifiantCible(),
                    disjoncteur.getDateDerniereOuverture());
        }

        if (!eligibles.isEmpty()) {
            log.info("{} disjoncteur(s) passé(s) en MI_OUVERT", eligibles.size());
        }
    }

    // Réinitialisation manuelle

    /**
     * Réinitialise manuellement un disjoncteur (retour à FERMÉ).
     * <p>
     * Action réservée aux rôles SUPERVISEUR et ADMIN.
     * </p>
     *
     * @param id l'identifiant du disjoncteur
     * @return le disjoncteur réinitialisé
     * @throws IllegalStateException si le disjoncteur n'existe pas
     */
    @Transactional
    public EtatDisjoncteur reinitialiser(Long id) {
        EtatDisjoncteur disjoncteur = disjoncteurRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Disjoncteur introuvable : " + id));

        disjoncteurRepository.reinitialiser(id);
        disjoncteur.reinitialiser();

        log.info("Disjoncteur réinitialisé manuellement : {} {}", 
                disjoncteur.getTypeCible(), disjoncteur.getIdentifiantCible());

        return disjoncteur;
    }

    // Consultation

    /**
     * Récupère tous les disjoncteurs.
     *
     * @return la liste de tous les disjoncteurs
     */
    public List<EtatDisjoncteur> getTous() {
        return disjoncteurRepository.findAll();
    }

    /**
     * Récupère un disjoncteur par son ID.
     *
     * @param id l'identifiant
     * @return le disjoncteur
     */
    public Optional<EtatDisjoncteur> getParId(Long id) {
        return disjoncteurRepository.findById(id);
    }

    /**
     * Récupère les disjoncteurs par état.
     *
     * @param etat l'état (FERME, OUVERT, MI_OUVERT)
     * @return la liste des disjoncteurs dans cet état
     */
    public List<EtatDisjoncteur> getParEtat(Etat etat) {
        return disjoncteurRepository.findByEtat(etat);
    }

    /**
     * Récupère les disjoncteurs actuellement ouverts.
     *
     * @return la liste des disjoncteurs ouverts
     */
    public List<EtatDisjoncteur> getDisjoncteursOuverts() {
        return disjoncteurRepository.findByEtat(Etat.OUVERT);
    }

    /**
     * Compte le nombre de disjoncteurs par état.
     *
     * @param etat l'état
     * @return le nombre
     */
    public long compterParEtat(Etat etat) {
        return disjoncteurRepository.countByEtat(etat);
    }

    /**
     * Compte le nombre total d'échecs enregistrés.
     *
     * @return la somme des échecs
     */
    public long getTotalEchecs() {
        return disjoncteurRepository.sumNombreEchecs();
    }

    // Méthodes privées

    /**
     * Récupère un disjoncteur existant ou en crée un nouveau.
     *
     * @param typeCible        le type de cible
     * @param identifiantCible l'identifiant de la cible
     * @return le disjoncteur existant ou nouvellement créé
     */
    private EtatDisjoncteur obtenirOuCreer(TypeCible typeCible, String identifiantCible) {
        return disjoncteurRepository
                .findByTypeCibleAndIdentifiantCible(typeCible, identifiantCible)
                .orElseGet(() -> {
                    EtatDisjoncteur nouveau = EtatDisjoncteur.builder()
                            .typeCible(typeCible)
                            .identifiantCible(identifiantCible)
                            .etat(Etat.FERME)
                            .nombreEchecs(0)
                            .seuilEchecs(seuilEchecs)
                            .delaiOuvertureMinutes(delaiOuvertureMinutes)
                            .fenetreHeures(fenetreHeures)
                            .nom(typeCible.name() + " " + identifiantCible)
                            .build();
                    log.info("Nouveau disjoncteur créé : {} {}", typeCible, identifiantCible);
                    return disjoncteurRepository.save(nouveau);
                });
    }
}