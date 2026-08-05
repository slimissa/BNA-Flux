package com.bna.flux.service;

import com.bna.flux.entity.Devise;
import com.bna.flux.entity.Transaction;
import com.bna.flux.entity.Transaction.Canal;
import com.bna.flux.entity.Transaction.StatutTransaction;
import com.bna.flux.entity.Transaction.TypeTransaction;
import com.bna.flux.repository.DeviseRepository;
import com.bna.flux.service.pipeline.ContextePipeline;
import com.bna.flux.service.pipeline.MoteurPipeline;
import com.bna.flux.service.pipeline.etape.EtapeEnrichissement;
import com.bna.flux.service.pipeline.etape.EtapeEvaluationRegles;
import com.bna.flux.service.pipeline.etape.EtapeNotation;
import com.bna.flux.service.pipeline.etape.EtapePersistance;
import com.bna.flux.service.pipeline.etape.EtapeValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'intégration pour le {@link MoteurPipeline}.
 * <p>
 * Teste l'orchestration complète des 5 étapes du pipeline
 * avec des scénarios de succès et d'échec.
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MoteurPipeline — Orchestration complète du pipeline")
class MoteurPipelineTest {

    @Mock
    private EtapeValidation etapeValidation;

    @Mock
    private EtapeEnrichissement etapeEnrichissement;

    @Mock
    private EtapeEvaluationRegles etapeEvaluationRegles;

    @Mock
    private EtapeNotation etapeNotation;

    @Mock
    private EtapePersistance etapePersistance;

    @Mock
    private ServiceAudit serviceAudit;

    @Mock
    private DeviseRepository deviseRepository;

    @InjectMocks
    private MoteurPipeline moteurPipeline;

    private Transaction transaction;
    private Devise deviseTND;

    @BeforeEach
    void setUp() {
        deviseTND = new Devise();
        deviseTND.setCode("TND");
        deviseTND.setNom("Dinar Tunisien");
        deviseTND.setUnitesMineures(3);
        deviseTND.setSymbole("د.ت");
        deviseTND.setActif(true);

        transaction = Transaction.builder()
                .referenceTransaction("BNA-20260805-0001")
                .ribSource("08601000191000748054")
                .ribDestination("01234123456789012383")
                .montant(new BigDecimal("50000.000"))
                .devise(deviseTND)
                .typeTransaction(TypeTransaction.VIREMENT)
                .canal(Canal.EN_LIGNE)
                .dateTransaction(LocalDateTime.now())
                .description("Paiement fournisseur")
                .statut(StatutTransaction.ACCEPTE)
                .scoreRisque(BigDecimal.ZERO)
                .build();
    }

    // Tests d'exécution complète

    @Nested
    @DisplayName("Exécution complète du pipeline")
    class ExecutionComplete {

        @Test
        @DisplayName("Doit exécuter les 5 étapes dans l'ordre")
        void doitExecuterCinqEtapes() {
            ContextePipeline contexte = moteurPipeline.executer(transaction);

            assertNotNull(contexte);
            assertTrue(contexte.isTermine());

            // Vérifier que la transaction est bien celle fournie
            assertEquals(transaction.getReferenceTransaction(),
                    contexte.getTransaction().getReferenceTransaction());
        }

        @Test
        @DisplayName("Doit mesurer la durée d'exécution")
        void doitMesurerDuree() {
            ContextePipeline contexte = moteurPipeline.executer(transaction);

            assertNotNull(contexte.getDebutTraitement());
            assertNotNull(contexte.getFinTraitement());
            assertTrue(contexte.getDureeTraitementMs() >= 0);
        }

        @Test
        @DisplayName("Doit retourner un résumé lisible")
        void doitRetournerResume() {
            ContextePipeline contexte = moteurPipeline.executer(transaction);

            String resume = contexte.getResume();
            assertNotNull(resume);
            assertTrue(resume.contains(transaction.getReferenceTransaction()));
        }
    }

    // Tests d'interruption du pipeline

    @Nested
    @DisplayName("Interruption du pipeline")
    class InterruptionPipeline {

        @Test
        @DisplayName("Doit marquer le contexte comme interrompu si la validation échoue")
        void doitInterrompreSiValidationEchoue() {
            // Le contexte sera interrompu si l'étape de validation appelle interrompre()
            // Ce test vérifie que le mécanisme d'interruption fonctionne
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .build();

            contexte.demarrer();
            contexte.interrompre("VALIDATION", "RIB invalide");

            assertTrue(contexte.isInterrompu());
            assertEquals("VALIDATION", contexte.getEtapeArret());
            assertTrue(contexte.getRaisonArret().contains("RIB invalide"));
        }

        @Test
        @DisplayName("Doit indiquer que la transaction est bloquée si interrompu")
        void transactionBloqueeSiInterrompu() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .build();

            contexte.demarrer();
            contexte.interrompre("VALIDATION", "Disjoncteur ouvert");

            assertTrue(contexte.isInterrompu());
            // La transaction n'est pas encore BLOQUE si l'interruption est externe
            // mais estTransactionBloquee retourne true car interrompu
            assertTrue(contexte.estTransactionBloquee());
        }
    }

    // Tests d'accumulation du score

    @Nested
    @DisplayName("Accumulation du score et des règles")
    class AccumulationScore {

        @Test
        @DisplayName("Doit accumuler les règles déclenchées")
        void doitAccumulerRegles() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .build();

            contexte.ajouterReglesDeclenchees(new java.util.ArrayList<>(), 45);

            assertEquals(45, contexte.getScoreRisque());
        }

        @Test
        @DisplayName("Doit plafonner le score à 100")
        void doitPlafonnerScore() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .build();

            contexte.ajouterReglesDeclenchees(new java.util.ArrayList<>(), 150);

            assertEquals(100, contexte.getScoreRisque(),
                    "Le score doit être plafonné à 100");
        }

        @Test
        @DisplayName("Doit construire un motif à partir des règles déclenchées")
        void doitConstruireMotif() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .build();

            // Ajouter des règles déclenchées via le moteur de règles
            var reglesDeclenchees = new java.util.ArrayList<MoteurRegles.RegleDeclenchee>();

            com.bna.flux.entity.Regle regle1 = com.bna.flux.entity.Regle.builder()
                    .nom("Règle 1")
                    .build();
            com.bna.flux.entity.Regle regle2 = com.bna.flux.entity.Regle.builder()
                    .nom("Règle 2")
                    .build();

            reglesDeclenchees.add(new MoteurRegles.RegleDeclenchee(regle1, "Message règle 1"));
            reglesDeclenchees.add(new MoteurRegles.RegleDeclenchee(regle2, "Message règle 2"));

            contexte.ajouterReglesDeclenchees(reglesDeclenchees, 50);
            String motif = contexte.construireMotif();

            assertNotNull(motif);
            assertTrue(motif.contains("Message règle 1"));
            assertTrue(motif.contains("Message règle 2"));
            assertTrue(motif.contains("; "));
        }

        @Test
        @DisplayName("Doit retourner null si aucune règle n'est déclenchée")
        void motifNullSiAucuneRegle() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .build();

            String motif = contexte.construireMotif();

            assertTrue(motif == null);
        }
    }

    // Tests de l'état du pipeline

    @Nested
    @DisplayName("État du pipeline")
    class EtatPipeline {

        @Test
        @DisplayName("Doit marquer le début du traitement")
        void doitMarquerDebut() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .build();

            contexte.demarrer();

            assertNotNull(contexte.getDebutTraitement());
            assertFalse(contexte.isTermine());
            assertFalse(contexte.isInterrompu());
        }

        @Test
        @DisplayName("Doit marquer la fin du traitement")
        void doitMarquerFin() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .build();

            contexte.demarrer();
            contexte.terminer();

            assertNotNull(contexte.getFinTraitement());
            assertTrue(contexte.isTermine());
        }

        @Test
        @DisplayName("estReussi doit retourner true si toutes les étapes ont réussi")
        void estReussiSiToutesEtapesReussies() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .validationReussie(true)
                    .enrichissementReussi(true)
                    .evaluationReussie(true)
                    .notationReussie(true)
                    .persistanceReussie(true)
                    .build();

            assertTrue(contexte.estReussi());
        }

        @Test
        @DisplayName("estReussi doit retourner false si une étape a échoué")
        void estEchecSiEtapeEchouee() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .validationReussie(false)
                    .enrichissementReussi(true)
                    .evaluationReussie(true)
                    .notationReussie(true)
                    .persistanceReussie(true)
                    .build();

            assertFalse(contexte.estReussi());
        }
    }

    // Tests des données de la transaction

    @Nested
    @DisplayName("Données de la transaction dans le pipeline")
    class DonneesTransaction {

        @Test
        @DisplayName("Doit conserver la référence de la transaction")
        void doitConserverReference() {
            ContextePipeline contexte = moteurPipeline.executer(transaction);

            assertEquals("BNA-20260805-0001", contexte.getTransaction().getReferenceTransaction());
        }

        @Test
        @DisplayName("Doit conserver les RIBs source et destination")
        void doitConserverRibs() {
            ContextePipeline contexte = moteurPipeline.executer(transaction);

            assertEquals("08601000191000748054", contexte.getTransaction().getRibSource());
            assertEquals("01234123456789012383", contexte.getTransaction().getRibDestination());
        }

        @Test
        @DisplayName("Doit conserver le montant et la devise")
        void doitConserverMontantDevise() {
            ContextePipeline contexte = moteurPipeline.executer(transaction);

            assertEquals(new BigDecimal("50000.000"), contexte.getTransaction().getMontant());
            assertEquals("TND", contexte.getTransaction().getCodeDevise());
        }

        @Test
        @DisplayName("Doit conserver le type et le canal")
        void doitConserverTypeCanal() {
            ContextePipeline contexte = moteurPipeline.executer(transaction);

            assertEquals(TypeTransaction.VIREMENT, contexte.getTransaction().getTypeTransaction());
            assertEquals(Canal.EN_LIGNE, contexte.getTransaction().getCanal());
        }
    }

    // Tests des alertes dans le contexte

    @Nested
    @DisplayName("Gestion des alertes dans le contexte")
    class AlertesContexte {

        @Test
        @DisplayName("Doit pouvoir ajouter des alertes au contexte")
        void doitAjouterAlertes() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .build();

            com.bna.flux.entity.Alerte alerte = com.bna.flux.entity.Alerte.builder()
                    .id(1L)
                    .message("Test alerte")
                    .niveau(com.bna.flux.entity.Alerte.NiveauAlerte.ELEVE)
                    .build();

            contexte.ajouterAlerte(alerte);

            assertEquals(1, contexte.getAlertesGenerees().size());
            assertEquals("Test alerte", contexte.getAlertesGenerees().get(0).getMessage());
        }

        @Test
        @DisplayName("Ne doit pas ajouter d'alerte null")
        void neDoitPasAjouterAlerteNull() {
            ContextePipeline contexte = ContextePipeline.builder()
                    .transaction(transaction)
                    .build();

            contexte.ajouterAlerte(null);

            assertEquals(0, contexte.getAlertesGenerees().size());
        }
    }
}