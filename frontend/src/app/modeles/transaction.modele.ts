/**
 * Modèles de données pour les transactions bancaires.
 *
 * Inclut les interfaces pour la soumission, la consultation,
 * le filtrage, et la pagination des transactions.
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */

import { ReponseAlerte } from './alerte.modele';

// Types énumérés

/** Types de transactions bancaires supportés. */
export type TypeTransaction = 'VIREMENT' | 'CHEQUE' | 'ESPECES' | 'CARTE' | 'PRELEVEMENT';

/** Canaux d'initiation des transactions. */
export type Canal = 'AGENCE' | 'DAB' | 'EN_LIGNE' | 'MOBILE';

/** Statuts possibles d'une transaction après le pipeline. */
export type StatutTransaction = 'ACCEPTE' | 'SURVEILLE' | 'BLOQUE';

/** Catégories de contrepartie. */
export type CategorieContrepartie = 'PARTICULIER' | 'ENTREPRISE' | 'GOUVERNEMENT';

// Labels et couleurs

/** Labels français des types de transactions. */
export const LABELS_TYPES_TRANSACTION: Record<TypeTransaction, string> = {
  VIREMENT: 'Virement',
  CHEQUE: 'Chèque',
  ESPECES: 'Espèces',
  CARTE: 'Carte bancaire',
  PRELEVEMENT: 'Prélèvement',
};

/** Labels français des canaux. */
export const LABELS_CANAUX: Record<Canal, string> = {
  AGENCE: 'Agence',
  DAB: 'Distributeur',
  EN_LIGNE: 'En ligne',
  MOBILE: 'Mobile',
};

/** Labels français des statuts. */
export const LABELS_STATUTS: Record<StatutTransaction, string> = {
  ACCEPTE: 'Acceptée',
  SURVEILLE: 'Surveillée',
  BLOQUE: 'Bloquée',
};

/** Couleurs des statuts pour les badges et icônes. */
export const COULEURS_STATUTS: Record<StatutTransaction, string> = {
  ACCEPTE: '#2ecc71',
  SURVEILLE: '#f39c12',
  BLOQUE: '#e74c3c',
};

/** Icônes FontAwesome associées aux statuts. */
export const ICONES_STATUTS: Record<StatutTransaction, string> = {
  ACCEPTE: 'fa-circle-check',
  SURVEILLE: 'fa-triangle-exclamation',
  BLOQUE: 'fa-circle-xmark',
};

/** Icônes FontAwesome associées aux types de transactions. */
export const ICONES_TYPES_TRANSACTION: Record<TypeTransaction, string> = {
  VIREMENT: 'fa-money-bill-transfer',
  CHEQUE: 'fa-file-invoice',
  ESPECES: 'fa-money-bill-wave',
  CARTE: 'fa-credit-card',
  PRELEVEMENT: 'fa-rotate',
};

/** Icônes FontAwesome associées aux canaux. */
export const ICONES_CANAUX: Record<Canal, string> = {
  AGENCE: 'fa-building-columns',
  DAB: 'fa-arrow-right-from-bracket',
  EN_LIGNE: 'fa-globe',
  MOBILE: 'fa-mobile-screen',
};

// Interfaces principales

/**
 * Requête de soumission d'une transaction au pipeline.
 * Envoyée au endpoint POST /api/transactions.
 */
export interface RequeteTransaction {
  /** RIB source (émetteur) — 20 chiffres. */
  ribSource: string;

  /** RIB destination (bénéficiaire) — 20 chiffres. */
  ribDestination: string;

  /** Montant de la transaction. */
  montant: number;

  /** Code ISO 4217 de la devise (ex: TND, EUR). */
  codeDevise: string;

  /** Type de transaction. */
  typeTransaction: TypeTransaction;

  /** Canal d'initiation. */
  canal: Canal;

  /** Date et heure d'exécution (ISO 8601). */
  dateTransaction: string;

  /** Description optionnelle. */
  description?: string;
}

/**
 * Réponse après traitement d'une transaction par le pipeline.
 * Retournée par GET /api/transactions/{id} et POST /api/transactions.
 */
export interface ReponseTransaction {
  /** Identifiant unique de la transaction. */
  id: number;

  /** Référence unique (format BNA-YYYYMMDD-XXXX). */
  referenceTransaction: string;

  /** RIB source (20 chiffres). */
  ribSource: string;

  /** RIB destination (20 chiffres). */
  ribDestination: string;

  /** Montant de la transaction. */
  montant: number;

  /** Code ISO 4217 de la devise. */
  codeDevise: string;

  /** Nom complet de la devise. */
  nomDevise?: string;

  /** Symbole de la devise. */
  symboleDevise?: string;

  /** Type de transaction. */
  typeTransaction: TypeTransaction;

  /** Canal d'initiation. */
  canal: Canal;

  /** Date et heure d'exécution. */
  dateTransaction: string;

  /** Description. */
  description?: string;

  /** Pays d'origine (déterminé au Stage 2). */
  paysOrigine?: string;

  /** Catégorie de contrepartie. */
  categorieContrepartie?: CategorieContrepartie;

  /** Score de risque (0.00 à 100.00). */
  scoreRisque: number;

  /** Statut final après le pipeline. */
  statutTransaction: StatutTransaction;

  /** Motif de rejet ou surveillance. */
  motif?: string;

  /** Date de traitement par le pipeline. */
  traiteLe?: string;

  /** Date de création en base. */
  dateCreation?: string;

  /** Liste des alertes générées. */
  alertes?: ReponseAlerte[];

  /** Nombre total d'alertes. */
  nombreAlertes: number;

  /** La piste d'audit est-elle disponible ? */
  pisteAuditDisponible: boolean;
}

/**
 * Résumé d'une transaction pour les listes (allégé).
 */
export interface ResumeTransaction {
  /** Identifiant unique. */
  id: number;

  /** Référence unique. */
  referenceTransaction: string;

  /** Montant. */
  montant: number;

  /** Code devise. */
  codeDevise: string;

  /** Type de transaction. */
  typeTransaction: TypeTransaction;

  /** Canal. */
  canal: Canal;

  /** Date d'exécution. */
  dateTransaction: string;

  /** Score de risque. */
  scoreRisque: number;

  /** Statut. */
  statutTransaction: StatutTransaction;

  /** Motif (optionnel). */
  motif?: string;

  /** Date de traitement (optionnel). */
  traiteLe?: string;
}

// Filtres et pagination

/**
 * Critères de filtrage pour la recherche de transactions.
 */
export interface FiltresTransaction {
  /** Filtre par statut. */
  statut?: StatutTransaction;

  /** Filtre par devise. */
  codeDevise?: string;

  /** Filtre par canal. */
  canal?: Canal;

  /** Filtre par type de transaction. */
  typeTransaction?: TypeTransaction;

  /** Montant minimum. */
  minMontant?: number;

  /** Montant maximum. */
  maxMontant?: number;

  /** Date de début (ISO 8601). */
  dateDebut?: string;

  /** Date de fin (ISO 8601). */
  dateFin?: string;

  /** Numéro de page (0-based). */
  page: number;

  /** Taille de la page. */
  taille: number;

  /** Champ de tri (ex: "dateTransaction,desc"). */
  tri: string;
}

/**
 * Réponse paginée de l'API.
 */
export interface PageReponse<T> {
  /** Statut de la réponse. */
  statut: string;

  /** Liste des éléments. */
  donnees: T[];

  /** Métadonnées de pagination. */
  pagination: Pagination;

  /** Horodatage de la réponse. */
  horodatage: string;
}

/**
 * Métadonnées de pagination.
 */
export interface Pagination {
  /** Page courante (0-based). */
  page: number;

  /** Taille de la page. */
  taille: number;

  /** Nombre total d'éléments. */
  totalElements: number;

  /** Nombre total de pages. */
  totalPages: number;
}