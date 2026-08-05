/**
 * Modèles de données pour les alertes de surveillance.
 *
 * Inclut les interfaces pour la consultation et l'acquittement
 * des alertes générées par les règles déclenchées.
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */

// Types énumérés

/** Niveaux de sévérité d'une alerte. */
export type NiveauAlerte = 'FAIBLE' | 'MOYEN' | 'ELEVE' | 'CRITIQUE';

// Labels et couleurs

/** Labels français des niveaux d'alerte. */
export const LABELS_NIVEAUX_ALERTE: Record<NiveauAlerte, string> = {
  FAIBLE: 'Faible',
  MOYEN: 'Moyen',
  ELEVE: 'Élevé',
  CRITIQUE: 'Critique',
};

/** Couleurs associées aux niveaux d'alerte pour les badges. */
export const COULEURS_NIVEAUX_ALERTE: Record<NiveauAlerte, string> = {
  FAIBLE: '#3498db',
  MOYEN: '#f1c40f',
  ELEVE: '#e67e22',
  CRITIQUE: '#e74c3c',
};

/** Couleurs de fond associées aux niveaux d'alerte. */
export const COULEURS_BG_NIVEAUX_ALERTE: Record<NiveauAlerte, string> = {
  FAIBLE: 'rgba(52, 152, 219, 0.10)',
  MOYEN: 'rgba(241, 196, 15, 0.10)',
  ELEVE: 'rgba(230, 126, 34, 0.10)',
  CRITIQUE: 'rgba(231, 76, 60, 0.10)',
};

/** Icônes FontAwesome associées aux niveaux d'alerte. */
export const ICONES_NIVEAUX_ALERTE: Record<NiveauAlerte, string> = {
  FAIBLE: 'fa-circle-info',
  MOYEN: 'fa-circle-exclamation',
  ELEVE: 'fa-triangle-exclamation',
  CRITIQUE: 'fa-circle-radiation',
};

// Interfaces principales

/**
 * Réponse après consultation d'une alerte.
 * Retournée par GET /api/alertes et GET /api/alertes/{id}.
 */
export interface ReponseAlerte {
  /** Identifiant unique de l'alerte. */
  id: number;

  /** Identifiant de la transaction ayant déclenché l'alerte. */
  transactionId: number;

  /** Référence de la transaction. */
  referenceTransaction?: string;

  /** Identifiant de la règle déclenchée. */
  regleId: number;

  /** Nom de la règle déclenchée. */
  nomRegle?: string;

  /** Message descriptif de l'alerte. */
  message: string;

  /** Niveau de sévérité. */
  niveau: NiveauAlerte;

  /** Date et heure de déclenchement. */
  dateCreation: string;

  /** L'alerte a-t-elle été acquittée ? */
  acquittee: boolean;

  /** Identifiant de l'opérateur ayant acquitté. */
  acquitteePar?: string;

  /** Date d'acquittement. */
  acquitteeLe?: string;

  /** Un email a-t-il été envoyé ? */
  emailEnvoye: boolean;

  /** Date d'envoi de l'email. */
  emailEnvoyeLe?: string;

  /** Destinataire de l'email. */
  emailDestinataire?: string;

  /** Délai écoulé depuis le déclenchement (minutes). */
  delaiMinutes?: number;
}

/**
 * Résumé d'une alerte pour les listes compactes.
 */
export interface ResumeAlerte {
  /** Identifiant unique. */
  id: number;

  /** Nom de la règle déclenchée. */
  nomRegle: string;

  /** Niveau de sévérité. */
  niveau: NiveauAlerte;

  /** L'alerte est-elle acquittée ? */
  acquittee: boolean;

  /** Délai en minutes. */
  delaiMinutes: number;
}

/**
 * Filtres pour la recherche d'alertes.
 */
export interface FiltresAlerte {
  /** Filtre par niveau de sévérité. */
  niveau?: NiveauAlerte;

  /** Filtre par état d'acquittement. */
  acquittee?: boolean;

  /** Date de début (ISO 8601). */
  dateDebut?: string;

  /** Date de fin (ISO 8601). */
  dateFin?: string;

  /** Numéro de page (0-based). */
  page: number;

  /** Taille de la page. */
  taille: number;
}

/**
 * Réponse d'acquittement d'une alerte.
 */
export interface ReponseAcquittement {
  /** Statut de la réponse. */
  statut: string;

  /** Message de confirmation. */
  message: string;

  /** Identifiant de l'opérateur. */
  acquitteePar: string;

  /** Date d'acquittement. */
  acquitteeLe: string;

  /** Horodatage. */
  horodatage: string;
}

/**
 * Email envoyé (historique).
 */
export interface EmailEnvoye {
  /** Identifiant de l'alerte. */
  alerteId: number;

  /** Destinataire. */
  destinataire: string;

  /** Date d'envoi. */
  dateEnvoi: string;

  /** Niveau de l'alerte. */
  niveau: NiveauAlerte;

  /** Message de l'alerte. */
  message: string;
}