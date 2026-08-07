package com.bna.flux.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration OpenAPI / Swagger pour BNA-FLUX.
 * <p>
 * Centralise les métadonnées de l'API et organise les endpoints
 * par domaine fonctionnel pour une navigation intuitive dans Swagger UI.
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @since 2026-08-08
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bnaFluxOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("BNA-FLUX API")
                .description("""
                    **Système de Surveillance des Transactions Bancaires en Temps Réel**
                    
                    ## Architecture
                    Pipeline 5 étapes : Validation RIB → Enrichissement → Évaluation règles SpEL → Notation → Persistance SHA-256
                    
                    ## Sécurité
                    Authentification JWT (HMAC-SHA512). Rôles : ADMIN, SUPERVISEUR, OPERATEUR.
                    
                    ## Fonctionnalités
                    - 🔐 Authentification JWT avec refresh token
                    - 📊 Tableau de bord temps réel
                    - 🔍 Surveillance par règles SpEL (10 règles par défaut)
                    - 🔗 Piste d'audit hash-chaînée SHA-256
                    - ⚡ Circuit breakers (disjoncteurs)
                    - 🔔 Notifications WebSocket temps réel
                    - 🧪 Testeur d'expressions SpEL
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("Slim Issa — Stagiaire BNA 2026")
                    .url("https://github.com/slimissa/BNA-Flux"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Développement local"),
                new Server().url("/api").description("Via Nginx (Docker)")
            ))
            .tags(List.of(
                new Tag().name("Authentification").description("Connexion JWT, rafraîchissement, déconnexion"),
                new Tag().name("Transactions").description("Soumission et consultation des transactions bancaires"),
                new Tag().name("Règles de Surveillance").description("CRUD des règles SpEL et test d'expressions"),
                new Tag().name("Alertes").description("Consultation et acquittement des alertes"),
                new Tag().name("Tableau de Bord").description("Statistiques et tendances pour le dashboard"),
                new Tag().name("Disjoncteurs").description("Circuit breakers — consultation et réinitialisation"),
                new Tag().name("Devises").description("Consultation des devises ISO 4217 (endpoint public)")
            ));
    }
}
