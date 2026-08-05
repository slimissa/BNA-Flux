package com.bna.flux.service;

import com.bna.flux.config.SpelConfig;
import com.bna.flux.entity.Regle;
import com.bna.flux.entity.Transaction;
import com.bna.flux.entity.Regle.Severite;
import com.bna.flux.entity.Regle.TypeRegle;
import com.bna.flux.entity.Transaction.Canal;
import com.bna.flux.entity.Transaction.StatutTransaction;
import com.bna.flux.entity.Transaction.TypeTransaction;
import com.bna.flux.exception.ExpressionRegleInvalideException;
import com.bna.flux.repository.RegleRepository;
import com.bna.flux.service.MoteurRegles.ResultatEvaluation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour le service {@link MoteurRegles}.
 * <p>
 * Teste l'évaluation des expressions SpEL, la compilation avec cache,
 * la gestion des erreurs de syntaxe, et le calcul du score.
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MoteurRegles — Évaluation des règles SpEL")
class MoteurReglesTest {

    @Mock
    private RegleRepository regleRepository;

    @Mock
    private SpelConfig spelConfig;

    @InjectMocks
    private MoteurRegles moteurRegles;

    private Transaction transaction;
    private List<Regle> reglesActives;

    @BeforeEach
    void setUp() {
        // Créer une transaction de test
        transaction = Transaction.builder()
                .referenceTransaction("BNA-20260805-0001")
                .ribSource("08601000191000748054")
                .ribDestination("01234123456789012383")
                .montant(new BigDecimal("75000.000"))
                .devise(creerDeviseTest("EUR", "Euro", 2))
                .typeTransaction(TypeTransaction.VIREMENT)
                .canal(Canal.EN_LIGNE)
                .statut(StatutTransaction.ACCEPTE)
                .scoreRisque(BigDecimal.ZERO)
                .paysOrigine("France")
                .build();

        // Créer des règles de test
        reglesActives = new ArrayList<>();

        Regle regle1 = Regle.builder()
                .id(1L)
                .nom("Virement international ≥ 50k")
                .expressionCondition("montant >= 50000 AND codeDevise != 'TND'")
                .severite(Severite.ELEVE)
                .contributionScore(30)
                .typeRegle(TypeRegle.ALERTE)
                .priorite(10)
                .actif(true)
                .build();

        Regle regle2 = Regle.builder()
                .id(2L)
                .nom("Canal en ligne ≥ 5k")
                .expressionCondition("canal == 'EN_LIGNE' AND montant >= 5000")
                .severite(Severite.MOYEN)
                .contributionScore(15)
                .typeRegle(TypeRegle.ALERTE)
                .priorite(30)
                .actif(true)
                .build();

        Regle regle3 = Regle.builder()
                .id(3L)
                .nom("Pays étranger")
                .expressionCondition("paysOrigine != null AND paysOrigine != 'Tunisie'")
                .severite(Severite.MOYEN)
                .contributionScore(20)
                .typeRegle(TypeRegle.ALERTE)
                .priorite(25)
                .actif(true)
                .build();

        Regle regle4 = Regle.builder()
                .id(4L)
                .nom("Règle inactive")
                .expressionCondition("montant >= 1000000")
                .severite(Severite.FAIBLE)
                .contributionScore(5)
                .typeRegle(TypeRegle.PREVENTION)
                .priorite(90)
                .actif(false)
                .build();

        reglesActives.add(regle1);
        reglesActives.add(regle2);
        reglesActives.add(regle3);
        reglesActives.add(regle4);

        // Mocker le cache SpEL pour utiliser le vrai parser
        when(spelConfig.getOuCompiler(any())).thenAnswer(invocation -> {
            String expression = invocation.getArgument(0);
            org.springframework.expression.ExpressionParser parser =
                    new org.springframework.expression.spel.standard.SpelExpressionParser();
            return parser.parseExpression(expression);
        });
    }

    // Tests d'évaluation

    @Nested
    @DisplayName("Évaluation des règles")
    class EvaluationRegles {

        @Test
        @DisplayName("Doit évaluer toutes les règles actives et retourner un résultat")
        void doitEvaluerToutesLesRegles() {
            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(reglesActives);

            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);

            assertNotNull(resultat);
            assertTrue(resultat.getNombreReglesEvaluees() >= 3, "Au moins 3 règles actives devraient être évaluées");
            assertTrue(resultat.getDureeMs() >= 0);
        }

        @Test
        @DisplayName("Doit déclencher les règles dont l'expression est vraie")
        void doitDeclencherReglesVraies() {
            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(reglesActives);

            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);

            // Règle 1 : montant=75000 >= 50000 ET devise=EUR != 'TND' → TRUE
            // Règle 2 : canal=EN_LIGNE ET montant=75000 >= 5000 → TRUE
            // Règle 3 : paysOrigine=France != Tunisie → TRUE
            // Règle 4 : inactive → pas évaluée
            assertEquals(3, resultat.getReglesDeclenchees().size(),
                    "Les 3 règles actives dont la condition est vraie devraient être déclenchées");
        }

        @Test
        @DisplayName("Doit calculer le score total comme la somme des contributions")
        void doitCalculerScoreTotal() {
            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(reglesActives);

            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);

            // Règle 1 (30) + Règle 2 (15) + Règle 3 (20) = 65
            assertEquals(65, resultat.getScoreTotal());
        }

        @Test
        @DisplayName("Doit plafonner le score à 100")
        void doitPlafonnerScore() {
            // Créer des règles avec un score total dépassant 100
            List<Regle> reglesHautScore = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                reglesHautScore.add(Regle.builder()
                        .id((long) i)
                        .nom("Règle " + i)
                        .expressionCondition("montant > 0")
                        .severite(Severite.CRITIQUE)
                        .contributionScore(30)
                        .typeRegle(TypeRegle.ALERTE)
                        .priorite(i * 10)
                        .actif(true)
                        .build());
            }

            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(reglesHautScore);

            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);

            assertTrue(resultat.getScoreTotal() <= 100,
                    "Le score ne doit pas dépasser 100. Actuel : " + resultat.getScoreTotal());
        }

        @Test
        @DisplayName("Doit retourner un score de 0 si aucune règle n'est déclenchée")
        void doitRetournerScoreZeroSiAucuneRegle() {
            // Transaction qui ne déclenche aucune règle
            Transaction txFaible = Transaction.builder()
                    .montant(new BigDecimal("100"))
                    .devise(creerDeviseTest("TND", "Dinar Tunisien", 3))
                    .typeTransaction(TypeTransaction.VIREMENT)
                    .canal(Canal.AGENCE)
                    .paysOrigine("Tunisie")
                    .statut(StatutTransaction.ACCEPTE)
                    .scoreRisque(BigDecimal.ZERO)
                    .build();

            // Règles qui ne se déclenchent pas pour cette transaction
            List<Regle> reglesStrictes = new ArrayList<>();
            reglesStrictes.add(Regle.builder()
                    .id(1L)
                    .nom("Montant > 100k")
                    .expressionCondition("montant > 100000")
                    .severite(Severite.ELEVE)
                    .contributionScore(30)
                    .typeRegle(TypeRegle.ALERTE)
                    .priorite(10)
                    .actif(true)
                    .build());

            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(reglesStrictes);

            ResultatEvaluation resultat = moteurRegles.evaluer(txFaible);

            assertEquals(0, resultat.getScoreTotal());
            assertTrue(resultat.isAucuneRegleDeclenchee());
        }
    }

    // Tests de gestion d'erreurs

    @Nested
    @DisplayName("Gestion des erreurs")
    class GestionErreurs {

        @Test
        @DisplayName("Doit continuer l'évaluation si une règle échoue")
        void doitContinuerSiRegleEchoue() {
            List<Regle> reglesAvecErreur = new ArrayList<>();

            // Règle avec une expression invalide
            Regle regleInvalide = Regle.builder()
                    .id(1L)
                    .nom("Règle invalide")
                    .expressionCondition("variableInconnue > 100")
                    .severite(Severite.FAIBLE)
                    .contributionScore(5)
                    .typeRegle(TypeRegle.PREVENTION)
                    .priorite(10)
                    .actif(true)
                    .build();

            // Règle valide
            Regle regleValide = Regle.builder()
                    .id(2L)
                    .nom("Règle valide")
                    .expressionCondition("montant > 1000")
                    .severite(Severite.MOYEN)
                    .contributionScore(15)
                    .typeRegle(TypeRegle.ALERTE)
                    .priorite(20)
                    .actif(true)
                    .build();

            reglesAvecErreur.add(regleInvalide);
            reglesAvecErreur.add(regleValide);

            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(reglesAvecErreur);

            // L'évaluation ne doit pas planter
            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);

            assertNotNull(resultat);
            assertTrue(resultat.getReglesDeclenchees().size() >= 1,
                    "La règle valide devrait être déclenchée malgré l'échec de la règle invalide");
        }

        @Test
        @DisplayName("Doit gérer une liste de règles vide")
        void doitGererListeVide() {
            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(new ArrayList<>());

            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);

            assertEquals(0, resultat.getScoreTotal());
            assertEquals(0, resultat.getNombreReglesEvaluees());
            assertTrue(resultat.isAucuneRegleDeclenchee());
        }
    }

    // Tests de test d'expression

    @Nested
    @DisplayName("Test d'expression individuelle")
    class TestExpression {

        @Test
        @DisplayName("Doit retourner true pour une expression valide et vraie")
        void doitRetournerTrue() {
            when(spelConfig.compilerExpression("montant >= 50000")).thenReturn(
                    new org.springframework.expression.spel.standard.SpelExpressionParser()
                            .parseExpression("montant >= 50000"));

            boolean resultat = moteurRegles.testerExpression("montant >= 50000", transaction);
            assertTrue(resultat);
        }

        @Test
        @DisplayName("Doit retourner false pour une expression valide et fausse")
        void doitRetournerFalse() {
            when(spelConfig.compilerExpression("montant >= 1000000")).thenReturn(
                    new org.springframework.expression.spel.standard.SpelExpressionParser()
                            .parseExpression("montant >= 1000000"));

            boolean resultat = moteurRegles.testerExpression("montant >= 1000000", transaction);
            assertFalse(resultat);
        }

        @Test
        @DisplayName("Doit lever une exception pour une expression syntaxiquement invalide")
        void doitLeverExceptionPourExpressionInvalide() {
            when(spelConfig.compilerExpression("montant >=")).thenThrow(
                    new org.springframework.expression.ParseException(10, "Unexpected end of expression"));

            assertThrows(ExpressionRegleInvalideException.class,
                    () -> moteurRegles.testerExpression("montant >=", transaction));
        }

        @Test
        @DisplayName("Doit gérer les comparaisons avec null")
        void doitGererComparaisonNull() {
            when(spelConfig.compilerExpression("paysOrigine != null")).thenReturn(
                    new org.springframework.expression.spel.standard.SpelExpressionParser()
                            .parseExpression("paysOrigine != null"));

            boolean resultat = moteurRegles.testerExpression("paysOrigine != null", transaction);
            assertTrue(resultat, "paysOrigine='France' donc != null est vrai");
        }
    }

    // Tests de variables disponibles

    @Nested
    @DisplayName("Variables disponibles dans les expressions")
    class VariablesDisponibles {

        @Test
        @DisplayName("La variable 'montant' doit être accessible")
        void variableMontantAccessible() {
            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(
                    List.of(Regle.builder()
                            .id(1L)
                            .nom("Test montant")
                            .expressionCondition("montant == 75000")
                            .severite(Severite.FAIBLE)
                            .contributionScore(5)
                            .typeRegle(TypeRegle.PREVENTION)
                            .priorite(10)
                            .actif(true)
                            .build()));

            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);
            assertEquals(1, resultat.getReglesDeclenchees().size());
        }

        @Test
        @DisplayName("La variable 'codeDevise' doit être accessible")
        void variableCodeDeviseAccessible() {
            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(
                    List.of(Regle.builder()
                            .id(1L)
                            .nom("Test devise")
                            .expressionCondition("codeDevise == 'EUR'")
                            .severite(Severite.FAIBLE)
                            .contributionScore(5)
                            .typeRegle(TypeRegle.PREVENTION)
                            .priorite(10)
                            .actif(true)
                            .build()));

            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);
            assertEquals(1, resultat.getReglesDeclenchees().size());
        }

        @Test
        @DisplayName("La variable 'typeTransaction' doit être accessible")
        void variableTypeTransactionAccessible() {
            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(
                    List.of(Regle.builder()
                            .id(1L)
                            .nom("Test type")
                            .expressionCondition("typeTransaction == 'VIREMENT'")
                            .severite(Severite.FAIBLE)
                            .contributionScore(5)
                            .typeRegle(TypeRegle.PREVENTION)
                            .priorite(10)
                            .actif(true)
                            .build()));

            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);
            assertEquals(1, resultat.getReglesDeclenchees().size());
        }

        @Test
        @DisplayName("La variable 'canal' doit être accessible")
        void variableCanalAccessible() {
            when(regleRepository.findActiveRulesForEvaluation()).thenReturn(
                    List.of(Regle.builder()
                            .id(1L)
                            .nom("Test canal")
                            .expressionCondition("canal == 'EN_LIGNE'")
                            .severite(Severite.FAIBLE)
                            .contributionScore(5)
                            .typeRegle(TypeRegle.PREVENTION)
                            .priorite(10)
                            .actif(true)
                            .build()));

            ResultatEvaluation resultat = moteurRegles.evaluer(transaction);
            assertEquals(1, resultat.getReglesDeclenchees().size());
        }
    }

    // Tests de gestion du cache

    @Nested
    @DisplayName("Gestion du cache SpEL")
    class CacheSpEL {

        @Test
        @DisplayName("viderCache doit appeler spelConfig.viderCache()")
        void viderCacheDoitDeleguer() {
            moteurRegles.viderCache();
            verify(spelConfig).viderCache();
        }

        @Test
        @DisplayName("invaliderExpression doit appeler spelConfig.invaliderExpression()")
        void invaliderExpressionDoitDeleguer() {
            String expression = "montant >= 50000";
            moteurRegles.invaliderExpression(expression);
            verify(spelConfig).invaliderExpression(expression);
        }

        @Test
        @DisplayName("getTailleCache doit retourner la taille du cache")
        void getTailleCacheDoitRetournerTaille() {
            when(spelConfig.tailleCache()).thenReturn(5);
            assertEquals(5, moteurRegles.getTailleCache());
        }
    }

    // Helper

    /**
     * Crée une devise de test sans passer par le repository.
     */
    private com.bna.flux.entity.Devise creerDeviseTest(String code, String nom, int unitesMineures) {
        com.bna.flux.entity.Devise devise = new com.bna.flux.entity.Devise();
        devise.setCode(code);
        devise.setNom(nom);
        devise.setUnitesMineures(unitesMineures);
        devise.setSymbole(code.equals("TND") ? "د.ت" : code.equals("EUR") ? "€" : "$");
        devise.setActif(true);
        return devise;
    }
}