package com.bna.flux.service;

import com.bna.flux.entity.EntreeAudit;
import com.bna.flux.entity.Transaction;
import com.bna.flux.entity.Transaction.StatutTransaction;
import com.bna.flux.repository.EntreeAuditRepository;
import com.bna.flux.service.ServiceAudit.ResultatVerification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour le service {@link ServiceAudit}.
 * <p>
 * Teste la création d'entrées d'audit hash-chaînées, le calcul
 * des hashs SHA-256, la vérification de l'intégrité de la chaîne,
 * et la détection de corruption.
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceAudit — Piste d'audit hash-chaînée SHA-256")
class ServiceAuditTest {

    @Mock
    private EntreeAuditRepository entreeAuditRepository;

    private ObjectMapper objectMapper;

    @InjectMocks
    private ServiceAudit serviceAudit;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        serviceAudit = new ServiceAudit(entreeAuditRepository, objectMapper);
        ReflectionTestUtils.setField(serviceAudit, "auditActif", true);
        ReflectionTestUtils.setField(serviceAudit, "algorithmeHash", "SHA-256");

        transaction = Transaction.builder()
                .id(1L)
                .referenceTransaction("BNA-20260805-0001")
                .statut(StatutTransaction.ACCEPTE)
                .build();
    }

    // Tests de création d'entrée d'audit

    @Nested
    @DisplayName("Création d'entrée d'audit")
    class CreationEntree {

        @Test
        @DisplayName("Doit créer une entrée d'audit avec un hash SHA-256")
        void doitCreerEntreeAvecHash() {
            when(entreeAuditRepository.findLastByTransactionId(anyLong())).thenReturn(null);
            when(entreeAuditRepository.save(any(EntreeAudit.class))).thenAnswer(inv -> {
                EntreeAudit e = inv.getArgument(0);
                e.setId(1L);
                return e;
            });

            EntreeAudit entree = serviceAudit.enregistrer(
                    transaction, "VALIDATION", "RIB_VALIDE",
                    "{\"rib\": \"08601000191000748054\"}", "SYSTEME");

            assertNotNull(entree);
            assertEquals("VALIDATION", entree.getEtape());
            assertEquals("RIB_VALIDE", entree.getAction());
            assertNotNull(entree.getHashCourant());
            assertEquals(64, entree.getHashCourant().length(), "Le hash SHA-256 doit faire 64 caractères hex");
            assertEquals("0", entree.getHashPrecedent(), "La premiere entree a hashPrecedent = 0");
        }

        @Test
        @DisplayName("Doit chaîner les entrées avec le hash précédent")
        void doitChainerEntrees() {
            // Première entrée
            EntreeAudit premiereEntree = EntreeAudit.builder()
                    .id(1L)
                    .transaction(transaction)
                    .etape("VALIDATION")
                    .action("RIB_VALIDE")
                    .detail("{}")
                    .hashCourant("abc123def456")
                    .hashPrecedent(null)
                    .horodatage(LocalDateTime.now())
                    .operateur("SYSTEME")
                    .build();

            when(entreeAuditRepository.findLastByTransactionId(anyLong())).thenReturn(premiereEntree);
            when(entreeAuditRepository.save(any(EntreeAudit.class))).thenAnswer(inv -> {
                EntreeAudit e = inv.getArgument(0);
                e.setId(2L);
                return e;
            });

            EntreeAudit deuxiemeEntree = serviceAudit.enregistrer(
                    transaction, "ENRICHISSEMENT", "PAYS_DETERMINE",
                    "{\"pays\": \"Tunisie\"}", "SYSTEME");

            assertNotNull(deuxiemeEntree);
            assertEquals("abc123def456", deuxiemeEntree.getHashPrecedent(),
                    "Le hash précédent doit correspondre au hash courant de l'entrée précédente");
            assertNotNull(deuxiemeEntree.getHashCourant());
            assertFalse(deuxiemeEntree.getHashCourant().equals(deuxiemeEntree.getHashPrecedent()),
                    "Le hash courant doit être différent du hash précédent");
        }

        @Test
        @DisplayName("Doit créer une entrée avec un détail sous forme d'objet Map")
        void doitCreerEntreeAvecDetailMap() {
            when(entreeAuditRepository.findLastByTransactionId(anyLong())).thenReturn(null);
            when(entreeAuditRepository.save(any(EntreeAudit.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> details = Map.of(
                    "rib", "08601000191000748054",
                    "cle", "54",
                    "valide", true
            );

            EntreeAudit entree = serviceAudit.enregistrer(
                    transaction, "VALIDATION", "RIB_VALIDE", details, "SYSTEME");

            assertNotNull(entree);
            assertTrue(entree.getDetail().contains("rib"));
            assertTrue(entree.getDetail().contains("54"));
        }

        @Test
        @DisplayName("Ne doit pas créer d'entrée si l'audit est désactivé")
        void neDoitPasCreerEntreeSiAuditDesactive() {
            ReflectionTestUtils.setField(serviceAudit, "auditActif", false);

            EntreeAudit entree = serviceAudit.enregistrer(
                    transaction, "VALIDATION", "TEST", "{}", "SYSTEME");

            assertNull(entree);
        }
    }

    // Tests de vérification de la chaîne

    @Nested
    @DisplayName("Vérification de l'intégrité de la chaîne")
    class VerificationChaine {

        @Test
        @DisplayName("Doit retourner chaineIntacte=true pour une chaîne valide")
        void doitValiderChaineIntacte() {
            // Créer une chaîne valide de 3 entrées
            List<EntreeAudit> entrees = creerChaineValide(3);

            when(entreeAuditRepository.findByTransactionIdOrderByHorodatageAsc(anyLong()))
                    .thenReturn(entrees);

            ResultatVerification resultat = serviceAudit.verifierChaine(1L);

            assertTrue(resultat.isChaineIntacte(), "La chaîne devrait être intacte");
            assertEquals(3, resultat.getNombreEntrees());
            assertNull(resultat.getEntreeCorrompue());
            assertTrue(resultat.getMessage().contains("intacte"));
        }

        @Test
        @DisplayName("Doit détecter une entrée avec un hash modifié")
        void doitDetecterHashModifie() {
            List<EntreeAudit> entrees = creerChaineValide(3);

            // Modifier le hash d'une entrée
            entrees.get(1).setHashCourant("hash_corrompu_00000000000000000000000000000000");

            when(entreeAuditRepository.findByTransactionIdOrderByHorodatageAsc(anyLong()))
                    .thenReturn(entrees);

            ResultatVerification resultat = serviceAudit.verifierChaine(1L);

            assertFalse(resultat.isChaineIntacte(), "La chaîne devrait être corrompue");
            assertNotNull(resultat.getEntreeCorrompue());
        }

        @Test
        @DisplayName("Doit détecter une entrée avec un hash précédent modifié")
        void doitDetecterHashPrecedentModifie() {
            List<EntreeAudit> entrees = creerChaineValide(3);

            // Modifier le hash précédent d'une entrée
            entrees.get(2).setHashPrecedent("hash_precedent_corrompu_000000000000000000");

            when(entreeAuditRepository.findByTransactionIdOrderByHorodatageAsc(anyLong()))
                    .thenReturn(entrees);

            ResultatVerification resultat = serviceAudit.verifierChaine(1L);

            assertFalse(resultat.isChaineIntacte(), "La chaîne devrait être corrompue");
        }

        @Test
        @DisplayName("Doit gérer une liste vide")
        void doitGererListeVide() {
            when(entreeAuditRepository.findByTransactionIdOrderByHorodatageAsc(anyLong()))
                    .thenReturn(new ArrayList<>());

            ResultatVerification resultat = serviceAudit.verifierChaine(1L);

            assertTrue(resultat.isChaineIntacte());
            assertEquals(0, resultat.getNombreEntrees());
        }

        @Test
        @DisplayName("Doit inclure les détails de vérification par entrée")
        void doitInclureDetailsParEntree() {
            List<EntreeAudit> entrees = creerChaineValide(2);

            when(entreeAuditRepository.findByTransactionIdOrderByHorodatageAsc(anyLong()))
                    .thenReturn(entrees);

            ResultatVerification resultat = serviceAudit.verifierChaine(1L);

            assertNotNull(resultat.getEntrees());
            assertEquals(2, resultat.getEntrees().size());
            assertTrue(resultat.getEntrees().get(1).isHashVerifie());
            assertTrue(resultat.getEntrees().get(2).isHashVerifie());
        }
    }

    // Tests de consultation

    @Nested
    @DisplayName("Consultation de la piste d'audit")
    class Consultation {

        @Test
        @DisplayName("Doit retourner la piste d'audit complète")
        void doitRetournerPisteComplete() {
            List<EntreeAudit> entrees = creerChaineValide(5);

            when(entreeAuditRepository.findByTransactionIdOrderByHorodatageAsc(anyLong()))
                    .thenReturn(entrees);

            List<EntreeAudit> piste = serviceAudit.getPisteAudit(1L);

            assertEquals(5, piste.size());
        }

        @Test
        @DisplayName("Doit compter les entrées d'audit")
        void doitCompterEntrees() {
            when(entreeAuditRepository.countByTransactionId(anyLong())).thenReturn(5L);

            long count = serviceAudit.compterEntrees(1L);

            assertEquals(5L, count);
        }
    }

    // Tests d'unicité des hashs

    @Nested
    @DisplayName("Unicité des hashs")
    class UniciteHashs {

        @Test
        @DisplayName("Des entrées avec des données différentes doivent avoir des hashs différents")
        void donneesDifferentesHashsDifferents() {
            when(entreeAuditRepository.findLastByTransactionId(anyLong())).thenReturn(null);
            when(entreeAuditRepository.save(any(EntreeAudit.class))).thenAnswer(inv -> inv.getArgument(0));

            EntreeAudit entree1 = serviceAudit.enregistrer(
                    transaction, "VALIDATION", "RIB_VALIDE",
                    "{\"rib\": \"11111111111111111111\"}", "SYSTEME");

            EntreeAudit entree2 = serviceAudit.enregistrer(
                    transaction, "VALIDATION", "RIB_INVALIDE",
                    "{\"rib\": \"99999999999999999999\"}", "SYSTEME");

            assertNotNull(entree1);
            assertNotNull(entree2);
            assertFalse(entree1.getHashCourant().equals(entree2.getHashCourant()),
                    "Des entrées différentes doivent avoir des hashs différents");
        }
    }

    // Helper

    /**
     * Crée une chaîne d'audit valide avec le nombre d'entrées spécifié.
     * Les hashs sont calculés comme le ferait le vrai service.
     */
    private List<EntreeAudit> creerChaineValide(int nombreEntrees) {
        List<EntreeAudit> entrees = new ArrayList<>();
        String hashPrecedent = null;

        String[] etapes = {"VALIDATION", "ENRICHISSEMENT", "EVALUATION_REGLES", "NOTATION", "PERSISTANCE"};
        String[] actions = {"RIB_VALIDE", "PAYS_DETERMINE", "REGLES_EVALUEES", "SCORE_CALCULE", "TRANSACTION_SAUVEGARDEE"};

        for (int i = 0; i < nombreEntrees; i++) {
            // Construire manuellement une entrée avec un hash réaliste
            EntreeAudit entree = new EntreeAudit();
            entree.setId((long) (i + 1));
            entree.setTransaction(transaction);
            entree.setEtape(etapes[i % etapes.length]);
            entree.setAction(actions[i % actions.length]);
            entree.setDetail("{\"index\": " + i + "}");
            entree.setHorodatage(LocalDateTime.now().minusMinutes(nombreEntrees - i));
            entree.setOperateur("SYSTEME");
            entree.setHashPrecedent(hashPrecedent);

            // Générer un hash réaliste (SHA-256 simulé)
            String donnees = (entree.getHashPrecedent() != null ? entree.getHashPrecedent() : "0")
                    + "|" + transaction.getId()
                    + "|" + entree.getEtape()
                    + "|" + entree.getAction()
                    + "|" + entree.getDetail()
                    + "|" + entree.getHorodatage()
                    + "|" + entree.getOperateur();

            String hashCourant = calculerHashSimule(donnees);
            entree.setHashCourant(hashCourant);
            hashPrecedent = hashCourant;

            entrees.add(entree);
        }

        return entrees;
    }

    /**
     * Calcule un vrai hash SHA-256 pour les tests (cohérent avec ServiceAudit).
     */
    private String calculerHashSimule(String donnees) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(donnees.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}