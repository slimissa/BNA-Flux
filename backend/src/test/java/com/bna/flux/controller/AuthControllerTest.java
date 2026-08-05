package com.bna.flux.controller;

import com.bna.flux.config.JwtFilter;
import com.bna.flux.config.JwtProvider;
import com.bna.flux.config.SecurityConfig;
import com.bna.flux.dto.ReponseConnexion;
import com.bna.flux.dto.RequeteConnexion;
import com.bna.flux.entity.Utilisateur;
import com.bna.flux.entity.Utilisateur.Role;
import com.bna.flux.repository.UtilisateurRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests du contrôleur {@link AuthController}.
 * <p>
 * Teste les endpoints d'authentification JWT : connexion,
 * rafraîchissement de token et déconnexion.
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtFilter.class})
@DisplayName("AuthController — Tests des endpoints d'authentification")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UtilisateurRepository utilisateurRepository;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private PasswordEncoder passwordEncoder;

    private RequeteConnexion requeteConnexion;
    private Utilisateur utilisateurActif;
    private static final String TOKEN_ACCES = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJvcGVyYXRldXJAYm5hLmNvbS50biIsInJvbGUiOiJPUEVSQVRFVVIiLCJ0eXBlIjoiQUNDRVNTIn0.signature";
    private static final String TOKEN_RAFRAICHISSEMENT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJvcGVyYXRldXJAYm5hLmNvbS50biIsInR5cGUiOiJSRUZSRVNIIn0.signature";

    @BeforeEach
    void setUp() {
        requeteConnexion = RequeteConnexion.builder()
                .email("operateur@bna.com.tn")
                .motDePasse("MotDePasse123!")
                .build();

        utilisateurActif = Utilisateur.builder()
                .id(1L)
                .email("operateur@bna.com.tn")
                .motDePasse("$2a$12$LJ3m4ys3Gql.ZHxHRrGI5eFh5vX5qP9G9G9G9G9G9G9G9G9G9G9G")
                .nom("Ahmed Ben Salah")
                .role(Role.OPERATEUR)
                .codeAgence("601")
                .actif(true)
                .build();
    }

    // POST /api/auth/connexion

    @Nested
    @DisplayName("POST /api/auth/connexion — Authentification")
    class Connexion {

        @Test
        @DisplayName("Doit authentifier un utilisateur avec des identifiants valides")
        void doitAuthentifierUtilisateurValide() throws Exception {
            when(utilisateurRepository.findByEmail("operateur@bna.com.tn"))
                    .thenReturn(Optional.of(utilisateurActif));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(jwtProvider.genererTokenAcces(any(Utilisateur.class))).thenReturn(TOKEN_ACCES);
            when(jwtProvider.genererTokenRafraichissement(any(Utilisateur.class)))
                    .thenReturn(TOKEN_RAFRAICHISSEMENT);
            when(jwtProvider.getDureeAccesMinutes()).thenReturn(60L);

            mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteConnexion)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.tokenAcces").value(TOKEN_ACCES))
                    .andExpect(jsonPath("$.tokenRafraichissement").value(TOKEN_RAFRAICHISSEMENT))
                    .andExpect(jsonPath("$.typeToken").value("Bearer"))
                    .andExpect(jsonPath("$.expireDans").value(3600))
                    .andExpect(jsonPath("$.utilisateur.email").value("operateur@bna.com.tn"))
                    .andExpect(jsonPath("$.utilisateur.nom").value("Ahmed Ben Salah"))
                    .andExpect(jsonPath("$.utilisateur.role").value("OPERATEUR"))
                    .andExpect(jsonPath("$.utilisateur.agence").value("601"));
        }

        @Test
        @DisplayName("Doit normaliser l'email en minuscules")
        void doitNormaliserEmail() throws Exception {
            RequeteConnexion requeteMajuscules = RequeteConnexion.builder()
                    .email("Operateur@BNA.COM.TN")
                    .motDePasse("MotDePasse123!")
                    .build();

            when(utilisateurRepository.findByEmail("operateur@bna.com.tn"))
                    .thenReturn(Optional.of(utilisateurActif));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(jwtProvider.genererTokenAcces(any(Utilisateur.class))).thenReturn(TOKEN_ACCES);
            when(jwtProvider.genererTokenRafraichissement(any(Utilisateur.class)))
                    .thenReturn(TOKEN_RAFRAICHISSEMENT);
            when(jwtProvider.getDureeAccesMinutes()).thenReturn(60L);

            mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteMajuscules)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.utilisateur.email").value("operateur@bna.com.tn"));
        }

        @Test
        @DisplayName("Doit rejeter un email inexistant avec un message générique")
        void doitRejeterEmailInexistant() throws Exception {
            when(utilisateurRepository.findByEmail("inconnu@bna.com.tn"))
                    .thenReturn(Optional.empty());

            RequeteConnexion requeteInconnue = RequeteConnexion.builder()
                    .email("inconnu@bna.com.tn")
                    .motDePasse("MotDePasse123!")
                    .build();

            mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteInconnue)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.statut").value("ERREUR"))
                    .andExpect(jsonPath("$.code").value("AUTHENTIFICATION_ECHOUEE"))
                    .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
        }

        @Test
        @DisplayName("Doit rejeter un mot de passe incorrect avec un message générique")
        void doitRejeterMotDePasseIncorrect() throws Exception {
            when(utilisateurRepository.findByEmail("operateur@bna.com.tn"))
                    .thenReturn(Optional.of(utilisateurActif));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteConnexion)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTIFICATION_ECHOUEE"))
                    .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
        }

        @Test
        @DisplayName("Doit rejeter un compte inactif")
        void doitRejeterCompteInactif() throws Exception {
            Utilisateur utilisateurInactif = Utilisateur.builder()
                    .id(2L)
                    .email("inactif@bna.com.tn")
                    .motDePasse("hash")
                    .nom("Inactif")
                    .role(Role.OPERATEUR)
                    .actif(false)
                    .build();

            when(utilisateurRepository.findByEmail("inactif@bna.com.tn"))
                    .thenReturn(Optional.of(utilisateurInactif));

            RequeteConnexion requeteInactif = RequeteConnexion.builder()
                    .email("inactif@bna.com.tn")
                    .motDePasse("MotDePasse123!")
                    .build();

            mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteInactif)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTIFICATION_ECHOUEE"));
        }

        @Test
        @DisplayName("Ne doit pas révéler si l'email existe ou si le mot de passe est incorrect")
        void neDoitPasRevelerExistenceEmail() throws Exception {
            // Test que le message est identique pour email inexistant et mot de passe incorrect
            when(utilisateurRepository.findByEmail("inconnu@bna.com.tn"))
                    .thenReturn(Optional.empty());

            RequeteConnexion requeteInconnue = RequeteConnexion.builder()
                    .email("inconnu@bna.com.tn")
                    .motDePasse("MotDePasse123!")
                    .build();

            String reponseEmailInconnu = mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteInconnue)))
                    .andExpect(status().isUnauthorized())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            when(utilisateurRepository.findByEmail("operateur@bna.com.tn"))
                    .thenReturn(Optional.of(utilisateurActif));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            String reponseMdpIncorrect = mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteConnexion)))
                    .andExpect(status().isUnauthorized())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Les deux réponses doivent contenir le même message
            assert reponseEmailInconnu.contains("Email ou mot de passe incorrect");
            assert reponseMdpIncorrect.contains("Email ou mot de passe incorrect");
        }

        @Test
        @DisplayName("Doit rejeter une requête sans email")
        void doitRejeterRequeteSansEmail() throws Exception {
            RequeteConnexion requeteSansEmail = RequeteConnexion.builder()
                    .motDePasse("MotDePasse123!")
                    .build();

            mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteSansEmail)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Doit rejeter une requête sans mot de passe")
        void doitRejeterRequeteSansMotDePasse() throws Exception {
            RequeteConnexion requeteSansMdp = RequeteConnexion.builder()
                    .email("operateur@bna.com.tn")
                    .build();

            mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteSansMdp)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Doit rejeter un mot de passe trop court")
        void doitRejeterMotDePasseTropCourt() throws Exception {
            RequeteConnexion requeteMdpCourt = RequeteConnexion.builder()
                    .email("operateur@bna.com.tn")
                    .motDePasse("123")
                    .build();

            mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteMdpCourt)))
                    .andExpect(status().isBadRequest());
        }
    }

    // POST /api/auth/rafraichir

    @Nested
    @DisplayName("POST /api/auth/rafraichir — Rafraîchir le token")
    class RafraichirToken {

        @Test
        @DisplayName("Doit générer un nouveau token d'accès avec un refresh token valide")
        void doitRafraichirTokenValide() throws Exception {
            when(jwtProvider.estSyntaxiquementValide(TOKEN_RAFRAICHISSEMENT)).thenReturn(true);
            when(jwtProvider.estTokenRafraichissement(TOKEN_RAFRAICHISSEMENT)).thenReturn(true);
            when(jwtProvider.validerEtExtraireUtilisateur(TOKEN_RAFRAICHISSEMENT))
                    .thenReturn(Optional.of(utilisateurActif));
            when(jwtProvider.genererTokenAcces(utilisateurActif)).thenReturn("nouveau_" + TOKEN_ACCES);
            when(jwtProvider.getDureeAccesMinutes()).thenReturn(60L);

            mockMvc.perform(post("/api/auth/rafraichir")
                            .with(csrf())
                            .header("Authorization", "Bearer " + TOKEN_RAFRAICHISSEMENT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.tokenAcces").value("nouveau_" + TOKEN_ACCES))
                    .andExpect(jsonPath("$.typeToken").value("Bearer"))
                    .andExpect(jsonPath("$.expireDans").value(3600));
        }

        @Test
        @DisplayName("Doit rejeter un token manquant")
        void doitRejeterTokenManquant() throws Exception {
            mockMvc.perform(post("/api/auth/rafraichir")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
        }

        @Test
        @DisplayName("Doit rejeter un token qui n'est pas un refresh token")
        void doitRejeterTokenAcces() throws Exception {
            when(jwtProvider.estSyntaxiquementValide(TOKEN_ACCES)).thenReturn(true);
            when(jwtProvider.estTokenRafraichissement(TOKEN_ACCES)).thenReturn(false);

            mockMvc.perform(post("/api/auth/rafraichir")
                            .with(csrf())
                            .header("Authorization", "Bearer " + TOKEN_ACCES))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("JETON_INVALIDE"));
        }

        @Test
        @DisplayName("Doit rejeter un refresh token expiré")
        void doitRejeterTokenExpire() throws Exception {
            when(jwtProvider.estSyntaxiquementValide(TOKEN_RAFRAICHISSEMENT)).thenReturn(true);
            when(jwtProvider.estTokenRafraichissement(TOKEN_RAFRAICHISSEMENT)).thenReturn(true);
            when(jwtProvider.validerEtExtraireUtilisateur(TOKEN_RAFRAICHISSEMENT))
                    .thenReturn(Optional.empty());

            mockMvc.perform(post("/api/auth/rafraichir")
                            .with(csrf())
                            .header("Authorization", "Bearer " + TOKEN_RAFRAICHISSEMENT))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("JETON_EXPIRE"));
        }
    }

    // POST /api/auth/deconnexion

    @Nested
    @DisplayName("POST /api/auth/deconnexion — Déconnexion")
    class Deconnexion {

        @Test
        @DisplayName("Doit confirmer la déconnexion")
        void doitConfirmerDeconnexion() throws Exception {
            mockMvc.perform(post("/api/auth/deconnexion")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.message").value(
                            "Déconnecté avec succès. Veuillez supprimer le token côté client."));
        }
    }

    // Tests de validation des DTOs

    @Nested
    @DisplayName("Validation des DTOs")
    class ValidationDTO {

        @Test
        @DisplayName("Doit rejeter un email au format invalide")
        void doitRejeterEmailInvalide() throws Exception {
            RequeteConnexion requeteEmailInvalide = RequeteConnexion.builder()
                    .email("pas-un-email")
                    .motDePasse("MotDePasse123!")
                    .build();

            mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteEmailInvalide)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Doit rejeter un email trop long")
        void doitRejeterEmailTropLong() throws Exception {
            String emailLong = "a".repeat(140) + "@bna.com.tn";
            RequeteConnexion requeteEmailLong = RequeteConnexion.builder()
                    .email(emailLong)
                    .motDePasse("MotDePasse123!")
                    .build();

            mockMvc.perform(post("/api/auth/connexion")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteEmailLong)))
                    .andExpect(status().isBadRequest());
        }
    }
}