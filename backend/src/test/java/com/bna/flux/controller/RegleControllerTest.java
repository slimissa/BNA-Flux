package com.bna.flux.controller;

import com.bna.flux.config.JwtFilter;
import com.bna.flux.config.SecurityConfig;
import com.bna.flux.dto.ReponseRegle;
import com.bna.flux.dto.RequeteRegle;
import com.bna.flux.entity.Regle;
import com.bna.flux.entity.Regle.Severite;
import com.bna.flux.entity.Regle.TypeRegle;
import com.bna.flux.exception.ExpressionRegleInvalideException;
import com.bna.flux.service.ServiceRegle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests du contrôleur {@link RegleController}.
 * <p>
 * Teste les endpoints CRUD des règles, l'activation/désactivation,
 * le basculement et le test d'expressions SpEL.
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@WebMvcTest(RegleController.class)
@Import({SecurityConfig.class, JwtFilter.class})
@DisplayName("RegleController — Tests des endpoints REST")
class RegleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ServiceRegle serviceRegle;

    @MockBean
    private com.bna.flux.config.JwtProvider jwtProvider;

    @MockBean
    private com.bna.flux.repository.UtilisateurRepository utilisateurRepository;

    private RequeteRegle requeteCreation;
    private Regle regleExistante;
    private ReponseRegle reponseRegle;

    @BeforeEach
    void setUp() {
        requeteCreation = RequeteRegle.builder()
                .nom("Virement international ≥ 50k TND")
                .description("Surveille les virements sortants en devise étrangère")
                .expressionCondition("montant >= 50000 AND codeDevise != 'TND'")
                .severite(Severite.ELEVE)
                .contributionScore(30)
                .typeRegle(TypeRegle.ALERTE)
                .categorie("Virements internationaux")
                .priorite(10)
                .actif(true)
                .build();

        regleExistante = Regle.builder()
                .id(1L)
                .nom("Virement international ≥ 50k TND")
                .description("Surveille les virements sortants en devise étrangère")
                .expressionCondition("montant >= 50000 AND codeDevise != 'TND'")
                .severite(Severite.ELEVE)
                .contributionScore(30)
                .typeRegle(TypeRegle.ALERTE)
                .categorie("Virements internationaux")
                .priorite(10)
                .actif(true)
                .dateCreation(LocalDateTime.now())
                .build();

        reponseRegle = ReponseRegle.builder()
                .id(1L)
                .nom("Virement international ≥ 50k TND")
                .severite(Severite.ELEVE)
                .typeRegle(TypeRegle.ALERTE)
                .actif(true)
                .dateCreation(LocalDateTime.now())
                .build();
    }

    // GET /api/regles

    @Nested
    @DisplayName("GET /api/regles — Lister les règles")
    class ListerRegles {

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit retourner la liste des règles")
        void doitRetournerListe() throws Exception {
            when(serviceRegle.getToutes()).thenReturn(List.of(regleExistante));
            when(serviceRegle.mapperVersReponse(any(Regle.class))).thenReturn(reponseRegle);

            mockMvc.perform(get("/api/regles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.nombre").value(1))
                    .andExpect(jsonPath("$.regles", hasSize(1)))
                    .andExpect(jsonPath("$.regles[0].nom").value("Virement international ≥ 50k TND"));
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit filtrer par catégorie")
        void doitFiltrerParCategorie() throws Exception {
            when(serviceRegle.getParCategorie("Virements internationaux")).thenReturn(List.of(regleExistante));
            when(serviceRegle.mapperVersReponse(any(Regle.class))).thenReturn(reponseRegle);

            mockMvc.perform(get("/api/regles")
                            .param("categorie", "Virements internationaux"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.regles", hasSize(1)));
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit retourner une liste vide si aucune règle")
        void doitRetournerListeVide() throws Exception {
            when(serviceRegle.getToutes()).thenReturn(List.of());

            mockMvc.perform(get("/api/regles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value(0))
                    .andExpect(jsonPath("$.regles", hasSize(0)));
        }

        @Test
        @DisplayName("Doit refuser l'accès sans authentification")
        void doitRefuserSansAuth() throws Exception {
            mockMvc.perform(get("/api/regles"))
                    .andExpect(status().isForbidden());
        }
    }

    // GET /api/regles/{id}

    @Nested
    @DisplayName("GET /api/regles/{id} — Consulter une règle")
    class ConsulterRegle {

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit retourner le détail d'une règle")
        void doitRetournerDetail() throws Exception {
            when(serviceRegle.getParId(1L)).thenReturn(regleExistante);
            when(serviceRegle.mapperVersReponse(regleExistante)).thenReturn(reponseRegle);

            mockMvc.perform(get("/api/regles/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.regle.nom").value("Virement international ≥ 50k TND"));
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit retourner 404 si la règle n'existe pas")
        void doitRetourner404() throws Exception {
            when(serviceRegle.getParId(999L)).thenThrow(new IllegalStateException("Règle introuvable"));

            mockMvc.perform(get("/api/regles/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REGLE_INTROUVABLE"));
        }
    }

    // POST /api/regles

    @Nested
    @DisplayName("POST /api/regles — Créer une règle")
    class CreerRegle {

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit créer une règle et retourner 201")
        void doitCreerRegle() throws Exception {
            when(serviceRegle.creer(any(RequeteRegle.class))).thenReturn(regleExistante);
            when(serviceRegle.mapperVersReponse(regleExistante)).thenReturn(reponseRegle);

            mockMvc.perform(post("/api/regles")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteCreation)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.message").value("Règle créée avec succès"));
        }

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit rejeter une expression SpEL invalide")
        void doitRejeterExpressionInvalide() throws Exception {
            when(serviceRegle.creer(any(RequeteRegle.class)))
                    .thenThrow(new ExpressionRegleInvalideException("montant >=", "Unexpected end of expression"));

            mockMvc.perform(post("/api/regles")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteCreation)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REGLE_SYNTAXE_INVALIDE"));
        }

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit rejeter un nom dupliqué")
        void doitRejeterNomDuplique() throws Exception {
            when(serviceRegle.creer(any(RequeteRegle.class)))
                    .thenThrow(new IllegalStateException("Une règle avec ce nom existe déjà."));

            mockMvc.perform(post("/api/regles")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteCreation)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REGLE_DUPLIQUEE"));
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit refuser si l'utilisateur n'est pas SUPERVISEUR")
        void doitRefuserSiPasSuperviseur() throws Exception {
            mockMvc.perform(post("/api/regles")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteCreation)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Doit refuser sans authentification")
        void doitRefuserSansAuth() throws Exception {
            mockMvc.perform(post("/api/regles")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteCreation)))
                    .andExpect(status().isForbidden());
        }
    }

    // PUT /api/regles/{id}

    @Nested
    @DisplayName("PUT /api/regles/{id} — Modifier une règle")
    class ModifierRegle {

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit modifier une règle existante")
        void doitModifierRegle() throws Exception {
            when(serviceRegle.modifier(eq(1L), any(RequeteRegle.class))).thenReturn(regleExistante);
            when(serviceRegle.mapperVersReponse(regleExistante)).thenReturn(reponseRegle);

            mockMvc.perform(put("/api/regles/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteCreation)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.message").value("Règle modifiée avec succès"));
        }

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit retourner 400 si la règle n'existe pas")
        void doitRetournerErreurSiInexistante() throws Exception {
            when(serviceRegle.modifier(eq(999L), any(RequeteRegle.class)))
                    .thenThrow(new IllegalStateException("Règle introuvable"));

            mockMvc.perform(put("/api/regles/999")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteCreation)))
                    .andExpect(status().isBadRequest());
        }
    }

    // DELETE /api/regles/{id}

    @Nested
    @DisplayName("DELETE /api/regles/{id} — Supprimer une règle")
    class SupprimerRegle {

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit supprimer une règle")
        void doitSupprimerRegle() throws Exception {
            mockMvc.perform(delete("/api/regles/1")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.message").value("Règle supprimée avec succès"));

            verify(serviceRegle).supprimer(1L);
        }

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit retourner 404 si la règle n'existe pas")
        void doitRetourner404() throws Exception {
            doThrow(new IllegalStateException("Règle introuvable")).when(serviceRegle).supprimer(999L);

            mockMvc.perform(delete("/api/regles/999")
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    // PUT /api/regles/{id}/basculer

    @Nested
    @DisplayName("PUT /api/regles/{id}/basculer — Activer/Désactiver")
    class BasculerRegle {

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit basculer l'état d'une règle")
        void doitBasculerRegle() throws Exception {
            Regle regleDesactivee = Regle.builder()
                    .id(1L)
                    .nom("Test")
                    .actif(false)
                    .build();
            when(serviceRegle.basculer(1L)).thenReturn(regleDesactivee);

            mockMvc.perform(put("/api/regles/1/basculer")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.actif").value(false));
        }
    }

    // POST /api/regles/tester

    @Nested
    @DisplayName("POST /api/regles/tester — Tester une expression")
    class TesterExpression {

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit valider une expression syntaxiquement correcte")
        void doitValiderExpressionCorrecte() throws Exception {
            String corps = objectMapper.writeValueAsString(
                    java.util.Map.of("expression", "montant >= 50000", "transactionId", 1)
            );

            mockMvc.perform(post("/api/regles/tester")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corps))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.syntaxeValide").value(true));
        }

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit retourner les détails d'erreur pour une expression invalide")
        void doitRetournerErreurPourExpressionInvalide() throws Exception {
            doThrow(new ExpressionRegleInvalideException("montant >=", "Unexpected end", 10))
                    .when(serviceRegle).validerExpressionSpEL("montant >=");

            String corps = objectMapper.writeValueAsString(
                    java.util.Map.of("expression", "montant >=")
            );

            mockMvc.perform(post("/api/regles/tester")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corps))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.syntaxeValide").value(false))
                    .andExpect(jsonPath("$.erreur").isNotEmpty());
        }

        @Test
        @WithMockUser(roles = "SUPERVISEUR")
        @DisplayName("Doit rejeter une expression vide")
        void doitRejeterExpressionVide() throws Exception {
            String corps = objectMapper.writeValueAsString(
                    java.util.Map.of("expression", "")
            );

            mockMvc.perform(post("/api/regles/tester")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corps))
                    .andExpect(status().isBadRequest());
        }
    }

    // GET /api/regles/categories

    @Nested
    @DisplayName("GET /api/regles/categories — Catégories distinctes")
    class Categories {

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit retourner les catégories distinctes")
        void doitRetournerCategories() throws Exception {
            when(serviceRegle.getCategories()).thenReturn(Arrays.asList(
                    "Virements internationaux", "Lutte anti-blanchiment", "Sécurité des canaux en ligne"
            ));

            mockMvc.perform(get("/api/regles/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.categories", hasSize(3)))
                    .andExpect(jsonPath("$.categories[0]").value("Virements internationaux"));
        }
    }
}