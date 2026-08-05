package com.bna.flux.service;

import com.bna.flux.entity.EtatDisjoncteur;
import com.bna.flux.entity.EtatDisjoncteur.Etat;
import com.bna.flux.entity.EtatDisjoncteur.TypeCible;
import com.bna.flux.exception.DisjoncteurOuvertException;
import com.bna.flux.repository.EtatDisjoncteurRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour le service {@link ServiceDisjoncteur}.
 * <p>
 * Teste les transitions d'état du circuit breaker (FERMÉ → OUVERT → MI_OUVERT → FERMÉ),
 * la vérification avant transaction, l'enregistrement des échecs,
 * la réinitialisation manuelle et les passages automatiques.
 * </p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceDisjoncteur — Circuit Breaker State Machine")
class ServiceDisjoncteurTest {

    @Mock
    private EtatDisjoncteurRepository disjoncteurRepository;

    @InjectMocks
    private ServiceDisjoncteur serviceDisjoncteur;

    private EtatDisjoncteur disjoncteurFerme;
    private EtatDisjoncteur disjoncteurOuvert;
    private EtatDisjoncteur disjoncteurMiOuvert;

    private static final TypeCible TYPE_COMPTE_SOURCE = TypeCible.COMPTE_SOURCE;
    private static final String RIB_SOURCE = "08601000191000748054";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(serviceDisjoncteur, "disjoncteursActifs", true);
        ReflectionTestUtils.setField(serviceDisjoncteur, "seuilEchecs", 3);
        ReflectionTestUtils.setField(serviceDisjoncteur, "delaiOuvertureMinutes", 60);
        ReflectionTestUtils.setField(serviceDisjoncteur, "fenetreHeures", 24);

        // Disjoncteur FERMÉ
        disjoncteurFerme = EtatDisjoncteur.builder()
                .id(1L)
                .typeCible(TYPE_COMPTE_SOURCE)
                .identifiantCible(RIB_SOURCE)
                .etat(Etat.FERME)
                .nombreEchecs(0)
                .seuilEchecs(3)
                .delaiOuvertureMinutes(60)
                .fenetreHeures(24)
                .nom("Compte source " + RIB_SOURCE)
                .build();

        // Disjoncteur OUVERT
        disjoncteurOuvert = EtatDisjoncteur.builder()
                .id(2L)
                .typeCible(TYPE_COMPTE_SOURCE)
                .identifiantCible(RIB_SOURCE)
                .etat(Etat.OUVERT)
                .nombreEchecs(3)
                .seuilEchecs(3)
                .delaiOuvertureMinutes(60)
                .fenetreHeures(24)
                .dateDerniereOuverture(LocalDateTime.now().minusMinutes(10))
                .nom("Compte source " + RIB_SOURCE)
                .build();

        // Disjoncteur MI_OUVERT
        disjoncteurMiOuvert = EtatDisjoncteur.builder()
                .id(3L)
                .typeCible(TYPE_COMPTE_SOURCE)
                .identifiantCible(RIB_SOURCE)
                .etat(Etat.MI_OUVERT)
                .nombreEchecs(3)
                .seuilEchecs(3)
                .delaiOuvertureMinutes(60)
                .fenetreHeures(24)
                .dateDerniereOuverture(LocalDateTime.now().minusMinutes(70))
                .nom("Compte source " + RIB_SOURCE)
                .build();
    }

    // Tests de vérification avant transaction (Stage 1)

    @Nested
    @DisplayName("Vérification avant transaction")
    class VerificationAvantTransaction {

        @Test
        @DisplayName("Doit autoriser la transaction si aucun disjoncteur n'existe")
        void doitAutoriserSiPasDeDisjoncteur() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.empty());

            assertDoesNotThrow(() ->
                    serviceDisjoncteur.verifierAvantTransaction(TYPE_COMPTE_SOURCE, RIB_SOURCE));
        }

        @Test
        @DisplayName("Doit autoriser la transaction si le disjoncteur est FERMÉ")
        void doitAutoriserSiFerme() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.of(disjoncteurFerme));

            assertDoesNotThrow(() ->
                    serviceDisjoncteur.verifierAvantTransaction(TYPE_COMPTE_SOURCE, RIB_SOURCE));
        }

        @Test
        @DisplayName("Doit bloquer la transaction si le disjoncteur est OUVERT")
        void doitBloquerSiOuvert() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.of(disjoncteurOuvert));

            DisjoncteurOuvertException exception = assertThrows(DisjoncteurOuvertException.class,
                    () -> serviceDisjoncteur.verifierAvantTransaction(TYPE_COMPTE_SOURCE, RIB_SOURCE));

            assertTrue(exception.getMessage().contains("circuit breaker est ouvert"));
            assertEquals(TYPE_COMPTE_SOURCE.name(), exception.getTypeCible());
            assertEquals(RIB_SOURCE, exception.getIdentifiantCible());
        }

        @Test
        @DisplayName("Doit autoriser en MI_OUVERT (transaction test)")
        void doitAutoriserSiMiOuvert() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.of(disjoncteurMiOuvert));

            assertDoesNotThrow(() ->
                    serviceDisjoncteur.verifierAvantTransaction(TYPE_COMPTE_SOURCE, RIB_SOURCE));
        }

        @Test
        @DisplayName("Doit passer automatiquement de OUVERT à MI_OUVERT si le délai est écoulé")
        void doitPasserAutoEnMiOuvert() {
            // Disjoncteur ouvert depuis plus de 60 minutes
            EtatDisjoncteur disjoncteurOuvertLongtemps = EtatDisjoncteur.builder()
                    .id(4L)
                    .typeCible(TYPE_COMPTE_SOURCE)
                    .identifiantCible(RIB_SOURCE)
                    .etat(Etat.OUVERT)
                    .nombreEchecs(3)
                    .seuilEchecs(3)
                    .delaiOuvertureMinutes(60)
                    .dateDerniereOuverture(LocalDateTime.now().minusMinutes(90))
                    .build();

            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.of(disjoncteurOuvertLongtemps));

            // La transaction devrait être autorisée (passage auto en MI_OUVERT)
            assertDoesNotThrow(() ->
                    serviceDisjoncteur.verifierAvantTransaction(TYPE_COMPTE_SOURCE, RIB_SOURCE));

            verify(disjoncteurRepository).passerEnMiOuvert(anyLong());
        }

        @Test
        @DisplayName("Ne doit pas vérifier si les disjoncteurs sont désactivés")
        void neDoitPasVerifierSiDesactive() {
            ReflectionTestUtils.setField(serviceDisjoncteur, "disjoncteursActifs", false);

            assertDoesNotThrow(() ->
                    serviceDisjoncteur.verifierAvantTransaction(TYPE_COMPTE_SOURCE, RIB_SOURCE));

            verify(disjoncteurRepository, never()).findByTypeCibleAndIdentifiantCible(any(), anyString());
        }
    }

    // Tests d'enregistrement d'échec (Stage 4)

    @Nested
    @DisplayName("Enregistrement d'échec")
    class EnregistrementEchec {

        @Test
        @DisplayName("Doit créer un disjoncteur s'il n'existe pas")
        void doitCreerDisjoncteurSiInexistant() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.empty());
            when(disjoncteurRepository.save(any(EtatDisjoncteur.class))).thenAnswer(inv -> inv.getArgument(0));

            EtatDisjoncteur resultat = serviceDisjoncteur.enregistrerEchec(TYPE_COMPTE_SOURCE, RIB_SOURCE);

            assertNotNull(resultat);
            assertEquals(TYPE_COMPTE_SOURCE, resultat.getTypeCible());
            assertEquals(RIB_SOURCE, resultat.getIdentifiantCible());
            verify(disjoncteurRepository).save(any(EtatDisjoncteur.class));
        }

        @Test
        @DisplayName("Doit incrémenter le compteur d'échecs")
        void doitIncrementerCompteur() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.of(disjoncteurFerme));

            serviceDisjoncteur.enregistrerEchec(TYPE_COMPTE_SOURCE, RIB_SOURCE);

            verify(disjoncteurRepository).incrementerEchecs(disjoncteurFerme.getId());
        }

        @Test
        @DisplayName("Doit ouvrir le disjoncteur si le seuil est atteint")
        void doitOuvrirSiSeuilAtteint() {
            // Disjoncteur avec 2 échecs (le 3ème va déclencher l'ouverture)
            EtatDisjoncteur presqueOuvert = EtatDisjoncteur.builder()
                    .id(5L)
                    .typeCible(TYPE_COMPTE_SOURCE)
                    .identifiantCible(RIB_SOURCE)
                    .etat(Etat.FERME)
                    .nombreEchecs(2)
                    .seuilEchecs(3)
                    .delaiOuvertureMinutes(60)
                    .build();

            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.of(presqueOuvert));

            serviceDisjoncteur.enregistrerEchec(TYPE_COMPTE_SOURCE, RIB_SOURCE);

            // La méthode incrementerEchecs est appelée, et comme nombreEchecs+1 >= seuil,
            // le disjoncteur devrait passer à OUVERT dans la requête SQL
            verify(disjoncteurRepository).incrementerEchecs(presqueOuvert.getId());
        }

        @Test
        @DisplayName("Ne doit pas enregistrer si les disjoncteurs sont désactivés")
        void neDoitPasEnregistrerSiDesactive() {
            ReflectionTestUtils.setField(serviceDisjoncteur, "disjoncteursActifs", false);

            EtatDisjoncteur resultat = serviceDisjoncteur.enregistrerEchec(TYPE_COMPTE_SOURCE, RIB_SOURCE);

            assertTrue(resultat == null);
            verify(disjoncteurRepository, never()).findByTypeCibleAndIdentifiantCible(any(), anyString());
        }
    }

    // Tests de test MI_OUVERT

    @Nested
    @DisplayName("Confirmation de test MI_OUVERT")
    class ConfirmationTest {

        @Test
        @DisplayName("Doit refermer le disjoncteur si le test réussit")
        void doitRefermerSiTestReussi() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.of(disjoncteurMiOuvert));

            serviceDisjoncteur.confirmerTestReussi(TYPE_COMPTE_SOURCE, RIB_SOURCE);

            verify(disjoncteurRepository).confirmerTestReussi(disjoncteurMiOuvert.getId());
        }

        @Test
        @DisplayName("Doit rouvrir le disjoncteur si le test échoue")
        void doitRouvrirSiTestEchoue() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.of(disjoncteurMiOuvert));

            serviceDisjoncteur.confirmerTestEchoue(TYPE_COMPTE_SOURCE, RIB_SOURCE);

            verify(disjoncteurRepository).confirmerTestEchoue(disjoncteurMiOuvert.getId());
        }

        @Test
        @DisplayName("Ne doit rien faire si le disjoncteur n'est pas en MI_OUVERT")
        void neDoitRienFaireSiPasMiOuvert() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(any(), anyString()))
                    .thenReturn(Optional.of(disjoncteurFerme));

            serviceDisjoncteur.confirmerTestReussi(TYPE_COMPTE_SOURCE, RIB_SOURCE);

            verify(disjoncteurRepository, never()).confirmerTestReussi(anyLong());
            verify(disjoncteurRepository, never()).confirmerTestEchoue(anyLong());
        }
    }

    // Tests de réinitialisation manuelle

    @Nested
    @DisplayName("Réinitialisation manuelle")
    class Reinitialisation {

        @Test
        @DisplayName("Doit réinitialiser un disjoncteur ouvert")
        void doitReinitialiserDisjoncteurOuvert() {
            when(disjoncteurRepository.findById(anyLong())).thenReturn(Optional.of(disjoncteurOuvert));

            EtatDisjoncteur resultat = serviceDisjoncteur.reinitialiser(disjoncteurOuvert.getId());

            verify(disjoncteurRepository).reinitialiser(disjoncteurOuvert.getId());
            assertEquals(Etat.FERME, resultat.getEtat());
        }

        @Test
        @DisplayName("Doit lever une exception si le disjoncteur n'existe pas")
        void doitLeverExceptionSiInexistant() {
            when(disjoncteurRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThrows(IllegalStateException.class,
                    () -> serviceDisjoncteur.reinitialiser(999L));
        }
    }

    // Tests de consultation


    @Nested
    @DisplayName("Consultation")
    class Consultation {

        @Test
        @DisplayName("Doit retourner les disjoncteurs par état")
        void doitRetournerParEtat() {
            serviceDisjoncteur.getParEtat(Etat.OUVERT);
            verify(disjoncteurRepository).findByEtat(Etat.OUVERT);
        }

        @Test
        @DisplayName("Doit compter par état")
        void doitCompterParEtat() {
            when(disjoncteurRepository.countByEtat(Etat.OUVERT)).thenReturn(5L);

            long count = serviceDisjoncteur.compterParEtat(Etat.OUVERT);
            assertEquals(5L, count);
        }

        @Test
        @DisplayName("Doit retourner le total des échecs")
        void doitRetournerTotalEchecs() {
            when(disjoncteurRepository.sumNombreEchecs()).thenReturn(42L);

            long total = serviceDisjoncteur.getTotalEchecs();
            assertEquals(42L, total);
        }
    }

    // Tests des transitions d'état sur l'entité

    @Nested
    @DisplayName("Transitions d'état de l'entité EtatDisjoncteur")
    class TransitionsEntite {

        @Test
        @DisplayName("FERMÉ → OUVERT quand le seuil est atteint")
        void fermeVersOuvert() {
            EtatDisjoncteur disjoncteur = EtatDisjoncteur.builder()
                    .etat(Etat.FERME)
                    .nombreEchecs(2)
                    .seuilEchecs(3)
                    .build();

            boolean ouvert = disjoncteur.enregistrerEchec();

            assertTrue(ouvert, "Le disjoncteur devrait s'ouvrir");
            assertEquals(Etat.OUVERT, disjoncteur.getEtat());
            assertEquals(3, disjoncteur.getNombreEchecs());
            assertNotNull(disjoncteur.getDateDerniereOuverture());
        }

        @Test
        @DisplayName("FERMÉ reste FERMÉ si le seuil n'est pas atteint")
        void fermeResteFerme() {
            EtatDisjoncteur disjoncteur = EtatDisjoncteur.builder()
                    .etat(Etat.FERME)
                    .nombreEchecs(0)
                    .seuilEchecs(3)
                    .build();

            boolean ouvert = disjoncteur.enregistrerEchec();

            assertFalse(ouvert, "Le disjoncteur ne devrait pas s'ouvrir");
            assertEquals(Etat.FERME, disjoncteur.getEtat());
            assertEquals(1, disjoncteur.getNombreEchecs());
        }

        @Test
        @DisplayName("OUVERT → MI_OUVERT après le délai écoulé")
        void ouvertVersMiOuvert() {
            EtatDisjoncteur disjoncteur = EtatDisjoncteur.builder()
                    .etat(Etat.OUVERT)
                    .dateDerniereOuverture(LocalDateTime.now().minusMinutes(90))
                    .delaiOuvertureMinutes(60)
                    .build();

            boolean transition = disjoncteur.tenterPassageMiOuvert();

            assertTrue(transition, "Le disjoncteur devrait passer en MI_OUVERT");
            assertEquals(Etat.MI_OUVERT, disjoncteur.getEtat());
        }

        @Test
        @DisplayName("OUVERT reste OUVERT si le délai n'est pas écoulé")
        void ouvertResteOuvert() {
            EtatDisjoncteur disjoncteur = EtatDisjoncteur.builder()
                    .etat(Etat.OUVERT)
                    .dateDerniereOuverture(LocalDateTime.now().minusMinutes(10))
                    .delaiOuvertureMinutes(60)
                    .build();

            boolean transition = disjoncteur.tenterPassageMiOuvert();

            assertFalse(transition, "Le disjoncteur ne devrait pas passer en MI_OUVERT");
            assertEquals(Etat.OUVERT, disjoncteur.getEtat());
        }

        @Test
        @DisplayName("MI_OUVERT → FERMÉ si test réussi")
        void miOuvertVersFerme() {
            EtatDisjoncteur disjoncteur = EtatDisjoncteur.builder()
                    .etat(Etat.MI_OUVERT)
                    .nombreEchecs(3)
                    .build();

            boolean reussi = disjoncteur.testReussi();

            assertTrue(reussi);
            assertEquals(Etat.FERME, disjoncteur.getEtat());
            assertEquals(0, disjoncteur.getNombreEchecs());
        }

        @Test
        @DisplayName("MI_OUVERT → OUVERT si test échoué")
        void miOuvertVersOuvert() {
            EtatDisjoncteur disjoncteur = EtatDisjoncteur.builder()
                    .etat(Etat.MI_OUVERT)
                    .nombreEchecs(3)
                    .build();

            boolean echoue = disjoncteur.testEchoue();

            assertTrue(echoue);
            assertEquals(Etat.OUVERT, disjoncteur.getEtat());
            assertEquals(4, disjoncteur.getNombreEchecs());
        }

        @Test
        @DisplayName("Réinitialisation remet à FERMÉ avec compteur à zéro")
        void reinitialisation() {
            EtatDisjoncteur disjoncteur = EtatDisjoncteur.builder()
                    .etat(Etat.OUVERT)
                    .nombreEchecs(5)
                    .build();

            disjoncteur.reinitialiser();

            assertEquals(Etat.FERME, disjoncteur.getEtat());
            assertEquals(0, disjoncteur.getNombreEchecs());
            assertNotNull(disjoncteur.getDateDerniereFermeture());
        }

        @Test
        @DisplayName("estOuvert() doit retourner true uniquement pour OUVERT")
        void estOuvert() {
            assertFalse(disjoncteurFerme.estOuvert());
            assertTrue(disjoncteurOuvert.estOuvert());
            assertFalse(disjoncteurMiOuvert.estOuvert());
        }

        @Test
        @DisplayName("transactionsAutorisees() doit retourner true pour FERMÉ et MI_OUVERT")
        void transactionsAutorisees() {
            assertTrue(disjoncteurFerme.transactionsAutorisees());
            assertFalse(disjoncteurOuvert.transactionsAutorisees());
            assertTrue(disjoncteurMiOuvert.transactionsAutorisees());
        }
    }

    // Tests de vérification des deux RIBs

    @Nested
    @DisplayName("Vérification des deux RIBs")
    class VerificationDeuxRibs {

        @Test
        @DisplayName("Doit vérifier les deux RIBs source et destination")
        void doitVerifierDeuxRibs() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(eq(TypeCible.COMPTE_SOURCE), anyString()))
                    .thenReturn(Optional.empty());
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(eq(TypeCible.COMPTE_DESTINATION), anyString()))
                    .thenReturn(Optional.empty());

            assertDoesNotThrow(() ->
                    serviceDisjoncteur.verifierRibsTransaction(RIB_SOURCE, "01234123456789012383"));
        }

        @Test
        @DisplayName("Doit bloquer si le RIB destination a un disjoncteur ouvert")
        void doitBloquerSiDestinationOuverte() {
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(eq(TypeCible.COMPTE_SOURCE), anyString()))
                    .thenReturn(Optional.empty());
            when(disjoncteurRepository.findByTypeCibleAndIdentifiantCible(eq(TypeCible.COMPTE_DESTINATION), anyString()))
                    .thenReturn(Optional.of(disjoncteurOuvert));

            assertThrows(DisjoncteurOuvertException.class,
                    () -> serviceDisjoncteur.verifierRibsTransaction(RIB_SOURCE, "01234123456789012383"));
        }
    }
}