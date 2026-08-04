package com.bna.flux.service;

import com.bna.flux.entity.Devise;
import com.bna.flux.repository.DeviseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Initialisateur des devises au démarrage de l'application.
 * <p>
 * Charge le fichier {@code devises.json} depuis le classpath et insère
 * les devises manquantes dans la base de données. Les devises déjà
 * existantes ne sont pas modifiées (insertion idempotente).
 * </p>
 *
 * <p><b>Comportement :</b></p>
 * <ul>
 *   <li>Si une devise existe déjà en base (même code), elle est ignorée.</li>
 *   <li>Si une devise n'existe pas, elle est créée avec les données du JSON.</li>
 *   <li>Les devises en base mais absentes du JSON ne sont pas supprimées.</li>
 *   <li>Le fichier JSON est la source de référence pour les nouvelles devises.</li>
 * </ul>
 *
 * <p><b>Format du fichier devises.json :</b></p>
 * <pre>
 * [
 *   {
 *     "code": "TND",
 *     "nom": "Dinar Tunisien",
 *     "unitesMineures": 3,
 *     "symbole": "د.ت",
 *     "codeNumerique": "788",
 *     "actif": true
 *   },
 *   ...
 * ]
 * </pre>
 *
 * <p><b>Devises chargées (17) :</b>
 * TND, EUR, KWD, USD, CAD, GBP, CHF, BHD, SEK, SAR, QAR, NOK, JPY, DKK, AED, CNY, LYD
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Component
public class InitialisateurDevises implements CommandLineRunner {

    private final DeviseRepository deviseRepository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    /**
     * Chemin du fichier JSON contenant les devises.
     */
    @Value("${bna.devises.fichier:devises.json}")
    private String fichierDevises;

    /**
     * Emplacement complet : classpath:devises.json
     */
    @Value("${bna.devises.chemin:classpath:devises.json}")
    private String cheminDevises;

    /**
     * Activation du chargement automatique.
     */
    @Value("${app.initialisation.devises.charger-au-demarrage:true}")
    private boolean chargerAuDemarrage;

    public InitialisateurDevises(DeviseRepository deviseRepository,
                                  ResourceLoader resourceLoader,
                                  ObjectMapper objectMapper) {
        this.deviseRepository = deviseRepository;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    // Exécution au démarrage

    /**
     * Point d'entrée appelé automatiquement par Spring Boot au démarrage.
     * <p>
     * Charge les devises depuis le fichier JSON et les insère en base
     * si elles n'existent pas déjà.
     * </p>
     *
     * @param args arguments de la ligne de commande (non utilisés)
     */
    @Override
    public void run(String... args) {
        if (!chargerAuDemarrage) {
            log.info("Chargement automatique des devises désactivé (app.initialisation.devises.charger-au-demarrage=false)");
            return;
        }

        try {
            long nombreDevisesExistantes = deviseRepository.count();
            log.info("Initialisation des devises — {} devise(s) déjà en base", nombreDevisesExistantes);

            List<Devise> devises = chargerDepuisJson();

            if (devises.isEmpty()) {
                log.warn("Aucune devise trouvée dans le fichier {}", cheminDevises);
                return;
            }

            int creees = 0;
            int ignorees = 0;

            for (Devise devise : devises) {
                if (deviseRepository.existsByCode(devise.getCode())) {
                    ignorees++;
                    log.debug("Devise {} déjà existante — ignorée", devise.getCode());
                } else {
                    deviseRepository.save(devise);
                    creees++;
                    log.info("Devise créée : {} ({}) — {} unités mineures",
                            devise.getCode(), devise.getNom(), devise.getUnitesMineures());
                }
            }

            long nombreFinal = deviseRepository.count();
            log.info("Initialisation des devises terminée — {} créée(s), {} ignorée(s), {} total en base",
                    creees, ignorees, nombreFinal);

        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation des devises : {}", e.getMessage(), e);
            // Ne pas bloquer le démarrage de l'application
        }
    }

    // Chargement du fichier JSON

    /**
     * Charge et parse le fichier JSON des devises.
     *
     * @return la liste des devises parsées
     * @throws Exception si le fichier est introuvable ou mal formé
     */
    private List<Devise> chargerDepuisJson() throws Exception {
        Resource resource = resourceLoader.getResource(cheminDevises);

        if (!resource.exists()) {
            log.error("Fichier de devises introuvable : {}", cheminDevises);
            throw new IllegalStateException("Fichier de devises introuvable : " + cheminDevises);
        }

        log.info("Chargement des devises depuis : {}", cheminDevises);

        try (InputStream inputStream = resource.getInputStream()) {
            List<Devise> devises = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<Devise>>() {}
            );
            log.info("{} devise(s) chargée(s) depuis le fichier JSON", devises.size());
            return devises;
        }
    }

    // Méthodes utilitaires publiques

    /**
     * Force le rechargement des devises depuis le fichier JSON.
     * <p>
     * Utile pour les tests ou l'administration.
     * Les devises déjà existantes ne sont pas écrasées.
     * </p>
     *
     * @return le nombre de devises nouvellement créées
     */
    public int recharger() {
        try {
            List<Devise> devises = chargerDepuisJson();
            int creees = 0;

            for (Devise devise : devises) {
                if (!deviseRepository.existsByCode(devise.getCode())) {
                    deviseRepository.save(devise);
                    creees++;
                }
            }

            log.info("Rechargement terminé — {} nouvelle(s) devise(s)", creees);
            return creees;
        } catch (Exception e) {
            log.error("Erreur lors du rechargement des devises : {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Retourne le nombre de devises actuellement en base.
     *
     * @return le nombre total de devises
     */
    public long getNombreDevises() {
        return deviseRepository.count();
    }

    /**
     * Retourne le nombre de devises actives.
     *
     * @return le nombre de devises actives
     */
    public long getNombreDevisesActives() {
        return deviseRepository.countByActifTrue();
    }
}