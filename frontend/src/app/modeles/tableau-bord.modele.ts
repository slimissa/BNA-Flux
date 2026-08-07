/**
 * Modèles de données pour le tableau de bord.
 *
 * Inclut les interfaces pour le résumé, les statistiques,
 * les tendances et les widgets du dashboard.
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */

import { ReponseDisjoncteur } from './disjoncteur.modele';

// Interface principale

/**
 * Résumé complet du tableau de bord.
 * Retourné par GET /api/tableau-bord/resume.
 */
export interface ResumeTableauBord {
  /** Période couverte par les données. */
  periode: Periode;

  /** Statistiques des transactions. */
  transactions: StatistiquesTransactions;

  /** Statistiques des alertes. */
  alertes: StatistiquesAlertes;

  /** État des disjoncteurs. */
  disjoncteurs: StatistiquesDisjoncteurs;

  /** Statistiques des règles. */
  regles: StatistiquesRegles;

  /** Tendance journalière. */
  tendance: TendanceJournaliere[];

  /** Score de risque moyen sur la période. */
  scoreRisqueMoyen: number | null;

  /** Montant total des transactions en TND. */
  montantTotalTND: number | null;

  /** Nombre de devises actives. */
  nombreDevisesActives: number;

  /** Nombre d'utilisateurs actifs. */
  nombreUtilisateursActifs: number;
}

// Période

export interface Periode {
  debut: string;
  fin: string;
}

// Transactions

export interface StatistiquesTransactions {
  total: number;
  acceptees: number;
  surveillees: number;
  bloquees: number;
  parCanal?: Record<string, number>;
  parType?: Record<string, number>;
  parDevise?: Record<string, number>;
}

// Alertes

export interface StatistiquesAlertes {
  total: number;
  parNiveau?: Record<string, number>;
  nonAcquittees: number;
  delaiMoyenAcquittementMinutes?: number | null;
  actionsRequises: number;
}

// Disjoncteurs

export interface StatistiquesDisjoncteurs {
  total: number;
  ouverts: number;
  miOuverts: number;
  fermes: number;
  totalEchecs: number;
  disjoncteursOuverts: ReponseDisjoncteur[];
}

// Règles

export interface StatistiquesRegles {
  totales: number;
  actives: number;
  topDeclenchees: RegleDeclenchee[];
}

export interface RegleDeclenchee {
  regleId: number;
  nom: string;
  nombre: number;
}

// Tendance

export interface TendanceJournaliere {
  date: string;
  acceptees: number;
  surveillees: number;
  bloquees: number;
  total: number;
  scoreRisqueMoyen?: number | null;
}

// Widgets — Statistiques rapides

/**
 * Statistiques rapides pour les widgets du dashboard.
 * Retourné par GET /api/tableau-bord/statistiques.
 */
export interface StatistiquesRapides {
  transactionsTotal: number;
  transactionsSurveillees: number;
  transactionsBloquees: number;
  alertesNonAcquittees: number;
  alertesActionsRequises: number;
  disjoncteursOuverts: number;
  scoreRisqueMoyen: number | null;
  reglesActives: number;
  devisesActives: number;
  utilisateursActifs: number;
}

// Périodes de tendance

export type PeriodeTendance = 'JOURNALIER' | 'HEBDOMADAIRE' | 'MENSUEL';

export const LABELS_PERIODES: Record<PeriodeTendance, string> = {
  JOURNALIER: '7 derniers jours',
  HEBDOMADAIRE: '4 dernières semaines',
  MENSUEL: '12 derniers mois',
};

// Couleurs des graphiques

export const COULEURS_GRAPHIQUE = {
  acceptees: '#2ecc71',
  surveillees: '#f39c12',
  bloquees: '#e74c3c',
  critiques: '#e74c3c',
  elevees: '#e67e22',
  moyennes: '#f1c40f',
  faibles: '#3498db',
  scoreRisque: '#1a8c4e',
  grille: 'rgba(74, 158, 255, 0.08)',
  texte: '#7ba07e',
};