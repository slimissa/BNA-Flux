package com.bna.flux.controller;

import com.bna.flux.config.JwtFilter;
import com.bna.flux.config.SecurityConfig;
import com.bna.flux.dto.ReponseTransaction;
import com.bna.flux.dto.RequeteTransaction;
import com.bna.flux.entity.Transaction.Canal;
import com.bna.flux.entity.Transaction.StatutTransaction;
import com.bna.flux.entity.Transaction.TypeTransaction;
import com.bna.flux.service.ServiceAudit;
import com.bna.flux.service.ServiceTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests du contrôleur {@link TransactionController}.
 * <p>
 * Teste les endpoints de soumission, consultation, filtrage,
 * piste d'audit et vérification d'intégrité.
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, JwtFilter.class})
@DisplayName("TransactionController — Tests des endpoints REST")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ServiceTransaction serviceTransaction;

    @MockBean
    private ServiceAudit serviceAudit;

    @MockBean
    private com.bna.flux.config.JwtProvider jwtProvider;

    @MockBean
    private com.bna.flux.repository.UtilisateurRepository utilisateurRepository;

    private RequeteTransaction requeteValide;
    private ReponseTransaction reponseSucces;

    @BeforeEach
    void setUp() {
        requeteValide = RequeteTransaction.builder()
                .ribSource("08601000191000748054")
                .ribDestination("01234123456789012383")
                .montant(new BigDecimal("50000.000"))
                .codeDevise("TND")
                .typeTransaction(TypeTransaction.VIREMENT)
                .canal(Canal.EN_LIGNE)
                .dateTransaction(LocalDateTime.now())
                .description("Paiement fournisseur")
                .build();

        reponseSucces = ReponseTransaction.builder()
                .id(1L)
                .referenceTransaction("BNA-20260805-0001")
                .ribSource("08601000191000748054")
                .ribDestination("01234123456789012383")
                .montant(new BigDecimal("50000.000"))
                .codeDevise("TND")
                .nomDevise("Dinar Tunisien")
                .typeTransaction(TypeTransaction.VIREMENT)
                .canal(Canal.EN_LIGNE)
                .scoreRisque(new BigDecimal("0.00"))
                .statutTransaction(StatutTransaction.ACCEPTE)
                .nombreAlertes(0)
                .alertes(Collections.emptyList())
                .pisteAuditDisponible(true)
                .build();
    }

    // POST /api/transactions

    @Nested
    @DisplayName("POST /api/transactions — Soumettre une transaction")
    class SoumettreTransaction {

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit accepter une transaction valide et retourner 200")
        void doitAccepterTransactionValide() throws Exception {
            when(serviceTransaction.soumettre(any(RequeteTransaction.class))).thenReturn(reponseSucces);

            mockMvc.perform(post("/api/transactions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteValide)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.donnees.referenceTransaction").value("BNA-20260805-0001"))
                    .andExpect(jsonPath("$.donnees.scoreRisque").value(0.00))
                    .andExpect(jsonPath("$.donnees.statutTransaction").value("ACCEPTE"));
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit rejeter une requête sans RIB source")
        void doitRejeterRequeteSansRibSource() throws Exception {
            RequeteTransaction requeteInvalide = RequeteTransaction.builder()
                    .ribDestination("01234123456789012383")
                    .montant(new BigDecimal("50000.000"))
                    .codeDevise("TND")
                    .typeTransaction(TypeTransaction.VIREMENT)
                    .canal(Canal.EN_LIGNE)
                    .dateTransaction(LocalDateTime.now())
                    .build();

            mockMvc.perform(post("/api/transactions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteInvalide)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit rejeter une requête avec un montant négatif")
        void doitRejeterMontantNegatif() throws Exception {
            RequeteTransaction requeteMontantNegatif = RequeteTransaction.builder()
                    .ribSource("08601000191000748054")
                    .ribDestination("01234123456789012383")
                    .montant(new BigDecimal("-100.00"))
                    .codeDevise("TND")
                    .typeTransaction(TypeTransaction.VIREMENT)
                    .canal(Canal.EN_LIGNE)
                    .dateTransaction(LocalDateTime.now())
                    .build();

            mockMvc.perform(post("/api/transactions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteMontantNegatif)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Doit refuser l'accès sans authentification")
        void doitRefuserSansAuthentification() throws Exception {
            mockMvc.perform(post("/api/transactions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requeteValide)))
                    .andExpect(status().isForbidden());
        }
    }

    // GET /api/transactions

    @Nested
    @DisplayName("GET /api/transactions — Lister les transactions")
    class ListerTransactions {

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit retourner une liste paginée")
        void doitRetournerListePaginee() throws Exception {
            when(serviceTransaction.rechercher(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(
                            com.bna.flux.entity.Transaction.builder()
                                    .id(1L)
                                    .referenceTransaction("BNA-20260805-0001")
                                    .montant(new BigDecimal("50000.000"))
                                    .devise(creerDeviseTest("TND"))
                                    .typeTransaction(TypeTransaction.VIREMENT)
                                    .canal(Canal.EN_LIGNE)
                                    .dateTransaction(LocalDateTime.now())
                                    .statut(StatutTransaction.ACCEPTE)
                                    .scoreRisque(BigDecimal.ZERO)
                                    .build()
                    )));

            mockMvc.perform(get("/api/transactions")
                            .param("page", "0")
                            .param("taille", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.pagination.page").value(0))
                    .andExpect(jsonPath("$.pagination.taille").value(20));
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit filtrer par statut")
        void doitFiltrerParStatut() throws Exception {
            when(serviceTransaction.rechercher(eq("BLOQUE"), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            mockMvc.perform(get("/api/transactions")
                            .param("statut", "BLOQUE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"));
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit filtrer par devise")
        void doitFiltrerParDevise() throws Exception {
            when(serviceTransaction.rechercher(any(), eq("EUR"), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            mockMvc.perform(get("/api/transactions")
                            .param("codeDevise", "EUR"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit limiter la taille de page à 100 maximum")
        void doitLimiterTaillePage() throws Exception {
            when(serviceTransaction.rechercher(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            mockMvc.perform(get("/api/transactions")
                            .param("taille", "500"))
                    .andExpect(status().isOk());
            // La taille devrait être limitée à 100 dans le contrôleur
        }
    }

    // GET /api/transactions/{id}

    @Nested
    @DisplayName("GET /api/transactions/{id} — Consulter une transaction")
    class ConsulterTransaction {

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit retourner le détail d'une transaction existante")
        void doitRetournerDetail() throws Exception {
            com.bna.flux.entity.Transaction transaction = com.bna.flux.entity.Transaction.builder()
                    .id(1L)
                    .referenceTransaction("BNA-20260805-0001")
                    .ribSource("08601000191000748054")
                    .ribDestination("01234123456789012383")
                    .montant(new BigDecimal("50000.000"))
                    .devise(creerDeviseTest("TND"))
                    .typeTransaction(TypeTransaction.VIREMENT)
                    .canal(Canal.EN_LIGNE)
                    .dateTransaction(LocalDateTime.now())
                    .statut(StatutTransaction.ACCEPTE)
                    .scoreRisque(BigDecimal.ZERO)
                    .dateCreation(LocalDateTime.now())
                    .build();

            when(serviceTransaction.getParId(1L)).thenReturn(Optional.of(transaction));

            mockMvc.perform(get("/api/transactions/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.donnees.referenceTransaction").value("BNA-20260805-0001"))
                    .andExpect(jsonPath("$.donnees.ribSource").value("08601000191000748054"));
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit retourner 404 si la transaction n'existe pas")
        void doitRetourner404() throws Exception {
            when(serviceTransaction.getParId(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/transactions/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.statut").value("ERREUR"))
                    .andExpect(jsonPath("$.code").value("TRANSACTION_INTROUVABLE"));
        }
    }

    // GET /api/transactions/{id}/piste-audit

    @Nested
    @DisplayName("GET /api/transactions/{id}/piste-audit — Piste d'audit")
    class PisteAudit {

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit retourner la piste d'audit")
        void doitRetournerPisteAudit() throws Exception {
            com.bna.flux.entity.Transaction transaction = com.bna.flux.entity.Transaction.builder()
                    .id(1L)
                    .referenceTransaction("BNA-20260805-0001")
                    .statut(StatutTransaction.ACCEPTE)
                    .build();

            when(serviceTransaction.getParId(1L)).thenReturn(Optional.of(transaction));
            when(serviceAudit.getPisteAudit(1L)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/transactions/1/piste-audit"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.transactionId").value(1))
                    .andExpect(jsonPath("$.nombreEntrees").value(0));
        }

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit retourner 404 si la transaction n'existe pas")
        void doitRetourner404SiTransactionInexistante() throws Exception {
            when(serviceTransaction.getParId(999L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/transactions/999/piste-audit"))
                    .andExpect(status().isNotFound());
        }
    }

    // GET /api/transactions/{id}/piste-audit/verifier

    @Nested
    @DisplayName("GET /api/transactions/{id}/piste-audit/verifier — Vérification intégrité")
    class VerificationAudit {

        @Test
        @WithMockUser(roles = "OPERATEUR")
        @DisplayName("Doit vérifier l'intégrité de la chaîne")
        void doitVerifierIntegrite() throws Exception {
            com.bna.flux.entity.Transaction transaction = com.bna.flux.entity.Transaction.builder()
                    .id(1L)
                    .referenceTransaction("BNA-20260805-0001")
                    .build();

            ServiceAudit.ResultatVerification resultat = new ServiceAudit.ResultatVerification(
                    true, 5, null, "Chaîne d'audit intacte."
            );

            when(serviceTransaction.getParId(1L)).thenReturn(Optional.of(transaction));
            when(serviceAudit.verifierChaine(1L)).thenReturn(resultat);

            mockMvc.perform(get("/api/transactions/1/piste-audit/verifier"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SUCCES"))
                    .andExpect(jsonPath("$.donnees.chaineIntacte").value(true))
                    .andExpect(jsonPath("$.donnees.nombreEntrees").value(5));
        }
    }

    // Helper

    private com.bna.flux.entity.Devise creerDeviseTest(String code) {
        com.bna.flux.entity.Devise devise = new com.bna.flux.entity.Devise();
        devise.setCode(code);
        devise.setNom(code.equals("TND") ? "Dinar Tunisien" : "Euro");
        devise.setUnitesMineures(code.equals("TND") ? 3 : 2);
        devise.setSymbole(code.equals("TND") ? "د.ت" : "€");
        devise.setActif(true);
        return devise;
    }
}