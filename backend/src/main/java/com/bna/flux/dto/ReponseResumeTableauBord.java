package com.bna.flux.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO pour la réponse du tableau de bord.
 * <p>
 * Retourné par {@link com.bna.flux.controller.TableauBordController}
 * et consommé par le frontend Angular pour afficher les cartes de résumé,
 * les graphiques et les tendances.
 * </p>
 *
 * <p><b>Structure de la réponse :</b></p>
 * <pre>
 * {
 *     "statut": "SUCCES",
 *     "donnees": {
 *         "periode": { "debut": "2026-08-04", "fin": "2026-08-04" },
 *         "transactions": {
 *             "total": 1247,
 *             "acceptees": 1198,
 *             "surveillees": 43,
 *             "bloquees": 6
 *         },
 *         "alertes": {
 *             "total": 52,
 *             "parNiveau": { "FAIBLE": 10, "MOYEN": 25, "ELEVE": 15, "CRITIQUE": 2 },
 *             "nonAcquittees": 8
 *         },
 *         "disjoncteurs": {
 *             "total": 12,
 *             "ouverts": 2,
 *             "miOuverts": 0,
 *             "fermes": 10
 *         },
 *         "regles": {
 *             "totales": 25,
 *             "actives": 20,
 *             "topDeclenchees": [
 *                 { "nom": "Virement > 50k TND", "nombre": 18 },
 *                 { "nom": "Dépôt espèces suspect", "nombre": 12 }
 *             ]
 *         },
 *         "tendance": [
 *             { "date": "2026-07-29", "acceptees": 195, "surveillees": 6, "bloquees": 1 },
 *             { "date": "2026-07-30", "acceptees": 210, "surveillees": 8, "bloquees": 2 }
 *         ],
 *         "scoreRisqueMoyen": 12.5,
 *         "montantTotalTND": 15420000.500
 *     }
 * }
 * </pre>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReponseResumeTableauBord {

    /**
     * Statut global de la réponse.
     */
    @Builder.Default
    private String statut = "SUCCES";

    /**
     * Période couverte par les données.
     */
    private Periode periode;

    /**
     * Statistiques des transactions.
     */
    private StatistiquesTransactions transactions;

    /**
     * Statistiques des alertes.
     */
    private StatistiquesAlertes alertes;

    /**
     * État des disjoncteurs.
     */
    private EtatDisjoncteurs disjoncteurs;

    /**
     * Statistiques des règles.
     */
    private StatistiquesRegles regles;

    /**
     * Tendance des transactions (par jour).
     */
    private List<TendanceJournaliere> tendance;

    /**
     * Score de risque moyen sur la période.
     */
    private BigDecimal scoreRisqueMoyen;

    /**
     * Montant total des transactions en TND sur la période.
     */
    private BigDecimal montantTotalTND;

    /**
     * Nombre total de devises actives.
     */
    private int nombreDevisesActives;

    /**
     * Nombre total d'utilisateurs actifs.
     */
    private int nombreUtilisateursActifs;

    // Classes internes

    /**
     * Période couverte par les statistiques.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Periode {
        private LocalDate debut;
        private LocalDate fin;
    }

    /**
     * Statistiques des transactions.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StatistiquesTransactions {
        private long total;
        private long acceptees;
        private long surveillees;
        private long bloquees;

        /** Transactions par canal (AGENCE, DAB, EN_LIGNE, MOBILE). */
        private Map<String, Long> parCanal;

        /** Transactions par type (VIREMENT, CHEQUE, ESPECES, CARTE, PRELEVEMENT). */
        private Map<String, Long> parType;

        /** Transactions par devise. */
        private Map<String, Long> parDevise;
    }

    /**
     * Statistiques des alertes.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StatistiquesAlertes {
        private long total;
        private Map<String, Long> parNiveau;
        private long nonAcquittees;
        private Double delaiMoyenAcquittementMinutes;

        /** Alertes nécessitant une action (ELEVE ou CRITIQUE non acquittées). */
        private long actionsRequises;
    }

    /**
     * État des disjoncteurs.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EtatDisjoncteurs {
        private long total;
        private long ouverts;
        private long miOuverts;
        private long fermes;
        private long totalEchecs;

        /** Liste des disjoncteurs actuellement ouverts (détail). */
        private List<ReponseDisjoncteur> disjoncteursOuverts;
    }

    /**
     * Statistiques des règles.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StatistiquesRegles {
        private long totales;
        private long actives;

        /** Top 5 des règles les plus déclenchées. */
        private List<RegleDeclenchee> topDeclenchees;
    }

    /**
     * Règle avec son nombre de déclenchements.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RegleDeclenchee {
        private Long regleId;
        private String nom;
        private long nombre;
    }

    /**
     * Tendance journalière des transactions.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TendanceJournaliere {
        private LocalDate date;
        private long acceptees;
        private long surveillees;
        private long bloquees;
        private long total;
        private BigDecimal scoreRisqueMoyen;
    }
}