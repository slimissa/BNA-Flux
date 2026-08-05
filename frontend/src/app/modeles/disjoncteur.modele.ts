/**
 * Modèles de données pour les disjoncteurs (Circuit Breakers).
 *
 * Inclut les interfaces pour la consultation et la réinitialisation
 * des disjoncteurs qui protègent le système contre les attaques coordonnées.
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */

// Types énumérés

/** Types de cibles surveillées par les disjoncteurs. */
export type TypeCible = 'COMPTE_SOURCE' | 'COMPTE_DESTINATION' | 'AGENCE' | 'CANAL';

/** États possibles d'un disjoncteur. */
export type EtatDisjoncteur = 'FERME' | 'OUVERT' | 'MI_OUVERT';

// Labels et couleurs

/** Labels français des types de cibles. */
export const LABELS_TYPES_CIBLE: Record<TypeCible, string> = {
  COMPTE_SOURCE: 'Compte source',
  COMPTE_DESTINATION: 'Compte destination',
  AGENCE: 'Agence',
  CANAL: 'Canal',
};

/** Icônes FontAwesome associées aux types de cibles. */
export const ICONES_TYPES_CIBLE: Record<TypeCible, string> = {
  COMPTE_SOURCE: 'fa-arrow-right-from-bracket',
  COMPTE_DESTINATION: 'fa-arrow-right-to-bracket',
  AGENCE: 'fa-building-columns',
  CANAL: 'fa-route',
};

/** Labels français des états. */
export const LABELS_ETATS_DISJONCTEUR: Record<EtatDisjoncteur, string> = {
  FERME: 'Fermé',
  OUVERT: 'Ouvert',
  MI_OUVERT: 'Mi-ouvert',
};

/** Couleurs associées aux états pour les badges. */
export const COULEURS_ETATS_DISJONCTEUR: Record<EtatDisjoncteur, string> = {
  FERME: '#2ecc71',
  OUVERT: '#e74c3c',
  MI_OUVERT: '#f39c12',
};

/** Couleurs de fond associées aux états. */
export const COULEURS_BG_ETATS_DISJONCTEUR: Record<EtatDisjoncteur, string> = {
  FERME: 'rgba(46, 204, 113, 0.10)',
  OUVERT: 'rgba(231, 76, 60, 0.12)',
  MI_OUVERT: 'rgba(243, 156, 18, 0.10)',
};

/** Icônes FontAwesome associées aux états. */
export const ICONES_ETATS_DISJONCTEUR: Record<EtatDisjoncteur, string> = {
  FERME: 'fa-circle-check',
  OUVERT: 'fa-circle-xmark',
  MI_OUVERT: 'fa-circle-half-stroke',
};

// Interfaces principales

/**
 * Réponse après consultation d'un disjoncteur.
 * Retournée par GET /api/disjoncteurs et GET /api/disjoncteurs/{id}.
 */
export interface ReponseDisjoncteur {
  /** Identifiant unique du disjoncteur. */
  id: number;

  /** Nom descriptif. */
  nom: string;

  /** Type de cible surveillée. */
  typeCible: TypeCible;

  /** Libellé français du type de cible. */
  typeCibleLabel?: string;

  /** Identifiant de la cible (RIB, code agence, canal). */
  identifiantCible: string;

  /** État actuel. */
  etat: EtatDisjoncteur;

  /** Libellé français de l'état. */
  etatLabel?: string;

  /** Nombre d'échecs enregistrés. */
  nombreEchecs: number;

  /** Seuil d'échecs avant ouverture. */
  seuilEchecs: number;

  /** Délai avant passage en MI_OUVERT (minutes). */
  delaiOuvertureMinutes: number;

  /** Fenêtre de comptage des échecs (heures). */
  fenetreHeures: number;

  /** Date de dernière ouverture. */
  dateDerniereOuverture?: string;

  /** Date de dernière fermeture. */
  dateDerniereFermeture?: string;

  /** Date du dernier échec. */
  dateDernierEchec?: string;

  /** Date de création. */
  dateCreation?: string;

  /** Date de modification. */
  dateModification?: string;

  /** Peut être réinitialisé manuellement ? */
  peutEtreReinitialise: boolean;

  /** Temps restant avant passage auto en MI_OUVERT (minutes). */
  tempsRestantAvantMiOuvert?: number;
}

/**
 * Résumé d'un disjoncteur pour les listes compactes.
 */
export interface ResumeDisjoncteur {
  /** Identifiant unique. */
  id: number;

  /** Nom descriptif. */
  nom: string;

  /** État actuel. */
  etat: EtatDisjoncteur;

  /** Peut être réinitialisé ? */
  peutEtreReinitialise: boolean;
}

/**
 * Statistiques des disjoncteurs (incluses dans le tableau de bord).
 */
export interface StatistiquesDisjoncteurs {
  /** Nombre total de disjoncteurs. */
  total: number;

  /** Nombre de disjoncteurs ouverts. */
  ouverts: number;

  /** Nombre de disjoncteurs mi-ouverts. */
  miOuverts: number;

  /** Nombre de disjoncteurs fermés. */
  fermes: number;

  /** Total des échecs enregistrés. */
  totalEchecs: number;

  /** Liste des disjoncteurs ouverts (détail). */
  disjoncteursOuverts: ReponseDisjoncteur[];
}