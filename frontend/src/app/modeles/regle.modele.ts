/**
 * Modèles de données pour les règles de surveillance.
 *
 * Inclut les interfaces pour la création, modification,
 * consultation et test des expressions SpEL.
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */

// Types énumérés

/** Niveaux de sévérité d'une règle. */
export type Severite = 'FAIBLE' | 'MOYEN' | 'ELEVE' | 'CRITIQUE';

/** Types de règle déterminant le comportement en cas de déclenchement. */
export type TypeRegle = 'PREVENTION' | 'ALERTE' | 'AUTO_REJET';

// Labels et couleurs

/** Labels français des sévérités. */
export const LABELS_SEVERITES: Record<Severite, string> = {
  FAIBLE: 'Faible',
  MOYEN: 'Moyen',
  ELEVE: 'Élevé',
  CRITIQUE: 'Critique',
};

/** Labels français des types de règles. */
export const LABELS_TYPES_REGLE: Record<TypeRegle, string> = {
  PREVENTION: 'Prévention',
  ALERTE: 'Alerte',
  AUTO_REJET: 'Auto-rejet',
};

/** Couleurs associées aux sévérités pour les badges. */
export const COULEURS_SEVERITES: Record<Severite, string> = {
  FAIBLE: '#3498db',
  MOYEN: '#f1c40f',
  ELEVE: '#e67e22',
  CRITIQUE: '#e74c3c',
};

/** Couleurs de fond associées aux sévérités. */
export const COULEURS_BG_SEVERITES: Record<Severite, string> = {
  FAIBLE: 'rgba(52, 152, 219, 0.12)',
  MOYEN: 'rgba(241, 196, 15, 0.12)',
  ELEVE: 'rgba(230, 126, 34, 0.12)',
  CRITIQUE: 'rgba(231, 76, 60, 0.12)',
};

/** Icônes FontAwesome associées aux sévérités. */
export const ICONES_SEVERITES: Record<Severite, string> = {
  FAIBLE: 'fa-circle-info',
  MOYEN: 'fa-circle-exclamation',
  ELEVE: 'fa-triangle-exclamation',
  CRITIQUE: 'fa-circle-radiation',
};

/** Couleurs associées aux types de règles. */
export const COULEURS_TYPES_REGLE: Record<TypeRegle, string> = {
  PREVENTION: '#3498db',
  ALERTE: '#f39c12',
  AUTO_REJET: '#e74c3c',
};

/** Descriptions des types de règles. */
export const DESCRIPTIONS_TYPES_REGLE: Record<TypeRegle, string> = {
  PREVENTION: 'Génère une alerte sans bloquer la transaction',
  ALERTE: 'Génère une alerte et place la transaction en surveillance',
  AUTO_REJET: 'Bloque automatiquement la transaction',
};

// Interfaces principales

/**
 * Requête de création ou modification d'une règle.
 * Envoyée aux endpoints POST /api/regles et PUT /api/regles/{id}.
 */
export interface RequeteRegle {
  /** Nom court et descriptif (5-200 caractères). */
  nom: string;

  /** Description détaillée (optionnelle, max 1000 caractères). */
  description?: string;

  /** Expression SpEL évaluée dynamiquement. */
  expressionCondition: string;

  /** Niveau de sévérité. */
  severite: Severite;

  /** Contribution au score de risque (1-100). */
  contributionScore: number;

  /** Type de règle. */
  typeRegle: TypeRegle;

  /** Catégorie fonctionnelle (optionnelle, max 100 caractères). */
  categorie?: string;

  /** Priorité d'évaluation (0 = maximale, 100 = minimale). */
  priorite: number;

  /** Activer la règle immédiatement après création ? */
  actif: boolean;
}

/**
 * Réponse après création, modification ou consultation d'une règle.
 */
export interface ReponseRegle {
  /** Identifiant unique de la règle. */
  id: number;

  /** Nom de la règle. */
  nom: string;

  /** Description. */
  description?: string;

  /** Expression SpEL. */
  expressionCondition: string;

  /** Niveau de sévérité. */
  severite: Severite;

  /** Label français de la sévérité. */
  severiteLabel?: string;

  /** Contribution au score. */
  contributionScore: number;

  /** Type de règle. */
  typeRegle: TypeRegle;

  /** Label français du type. */
  typeRegleLabel?: string;

  /** Catégorie. */
  categorie?: string;

  /** Priorité d'évaluation. */
  priorite: number;

  /** État actif/inactif. */
  actif: boolean;

  /** Label d'état ("Active" / "Inactive"). */
  etatLabel?: string;

  /** Date de création. */
  dateCreation: string;

  /** Date de dernière modification. */
  dateModification?: string;

  /** Nombre de fois que la règle a été déclenchée. */
  nombreDeclenchements?: number;
}

/**
 * Requête de test d'une expression SpEL.
 */
export interface RequeteTestRegle {
  /** Expression SpEL à tester. */
  expression: string;

  /** Identifiant de la transaction de test (optionnel). */
  transactionId?: number;
}

/**
 * Réponse du test d'expression SpEL.
 */
export interface ReponseTestRegle {
  /** Statut de la réponse. */
  statut: string;

  /** La syntaxe est-elle valide ? */
  syntaxeValide: boolean;

  /** Message de résultat. */
  message: string;

  /** Détails de l'erreur si syntaxe invalide. */
  erreur?: string;

  /** Détails structurés. */
  details?: {
    expression: string;
    erreurParser: string;
    positionErreur?: number;
  };

  /** Horodatage. */
  horodatage: string;
}

/**
 * Filtres pour la recherche de règles.
 */
export interface FiltresRegle {
  /** Filtre par catégorie. */
  categorie?: string;

  /** Filtre par sévérité. */
  severite?: Severite;

  /** Filtre par état actif/inactif. */
  actif?: boolean;
}

/**
 * Variables disponibles dans les expressions SpEL.
 * Documentées pour l'interface d'aide à la saisie.
 */
export const VARIABLES_SPEL: { nom: string; type: string; description: string }[] = [
  { nom: 'montant', type: 'BigDecimal', description: 'Montant de la transaction' },
  { nom: 'codeDevise', type: 'String', description: 'Code ISO 4217 (ex: TND, EUR, USD)' },
  { nom: 'typeTransaction', type: 'String', description: 'VIREMENT, CHEQUE, ESPECES, CARTE, PRELEVEMENT' },
  { nom: 'canal', type: 'String', description: 'AGENCE, DAB, EN_LIGNE, MOBILE' },
  { nom: 'paysOrigine', type: 'String', description: 'Pays d\'origine (peut être null)' },
  { nom: 'categorieContrepartie', type: 'String', description: 'PARTICULIER, ENTREPRISE, GOUVERNEMENT' },
  { nom: 'ribSource', type: 'String', description: 'RIB émetteur (20 chiffres)' },
  { nom: 'ribDestination', type: 'String', description: 'RIB bénéficiaire (20 chiffres)' },
  { nom: 'scoreRisque', type: 'BigDecimal', description: 'Score de risque actuel (0-100)' },
];

/**
 * Opérateurs SpEL supportés.
 */
export const OPERATEURS_SPEL: { operateur: string; description: string; exemple: string }[] = [
  { operateur: '==, !=', description: 'Égalité, différence', exemple: "codeDevise == 'EUR'" },
  { operateur: '<, <=, >, >=', description: 'Comparaison numérique', exemple: 'montant >= 50000' },
  { operateur: 'AND, OR, NOT', description: 'Opérateurs logiques', exemple: "montant >= 50000 AND codeDevise != 'TND'" },
  { operateur: 'IN', description: 'Appartenance à une liste', exemple: "canal IN {'EN_LIGNE', 'MOBILE'}" },
  { operateur: '!= null, == null', description: 'Test de nullité', exemple: 'paysOrigine != null' },
  { operateur: 'matches', description: 'Pattern matching regex', exemple: "ribSource matches '^[0-9]{20}$'" },
];