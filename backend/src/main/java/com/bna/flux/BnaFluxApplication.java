package com.bna.flux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Point d'entrée principal de l'application BNA-FLUX.
 * <p>
 * Système de surveillance des transactions bancaires en temps réel
 * avec pipeline de validation, enrichissement, évaluation de règles,
 * notation du risque, circuit breaker et piste d'audit hash-chaînée.
 * </p>
 *
 * <p><b>Architecture :</b></p>
 * <ul>
 *   <li>{@code @SpringBootApplication} — Active la configuration automatique Spring Boot</li>
 *   <li>{@code @EnableAsync} — Permet le traitement asynchrone des emails et notifications</li>
 *   <li>{@code @EnableScheduling} — Active les tâches planifiées (envoi groupé d'alertes)</li>
 *   <li>{@code @EnableTransactionManagement} — Gestion déclarative des transactions</li>
 *   <li>{@code @EntityScan} — Scan des entités JPA dans le package entity</li>
 *   <li>{@code @EnableJpaRepositories} — Scan des repositories Spring Data JPA</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@SpringBootApplication(
    scanBasePackages = {
        "com.bna.flux.config",
        "com.bna.flux.controller",
        "com.bna.flux.service",
        "com.bna.flux.exception",
        "com.bna.flux.dto"
    }
)
@EntityScan(basePackages = "com.bna.flux.entity")
@EnableJpaRepositories(basePackages = "com.bna.flux.repository")
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@EnableConfigurationProperties
public class BnaFluxApplication {

    /**
     * Point d'entrée Java.
     * <p>
     * Démarre le contexte Spring Boot, initialise le pipeline de
     * traitement des transactions, les seeds de devises et règles,
     * et active l'infrastructure de sécurité JWT.
     * </p>
     *
     * @param args arguments de la ligne de commande (non utilisés)
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BnaFluxApplication.class);

        // Désactiver la bannière Spring pour gagner du temps au démarrage
        application.setBanner((environment, sourceClass, out) -> {
            out.println("╔══════════════════════════════════════════════════════════════╗");
            out.println("║                    BNA-FLUX v1.0.0                          ║");
            out.println("║   Surveillance des Transactions Bancaires en Temps Réel     ║");
            out.println("║   BNA — Banque Nationale Agricole                          ║");
            out.println("╚══════════════════════════════════════════════════════════════╝");
        });

        // Propriétés additionnelles avant démarrage
        application.setAdditionalProfiles("dev");
        application.setAllowBeanDefinitionOverriding(false);
        application.setLogStartupInfo(true);

        // Démarrage du contexte Spring
        application.run(args);
    }
}