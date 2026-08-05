/**
 * Modèles de données pour les devises ISO 4217.
 *
 * Inclut l'interface de devise et les constantes
 * pour l'affichage formaté des montants.
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */

// Interface principale

/**
 * Devise ISO 4217 supportée par BNA-FLUX.
 * Retournée par GET /api/devises (endpoint public).
 */
export interface Devise {
  /** Code alphabétique ISO 4217 (3 lettres, ex: TND, EUR, USD). */
  code: string;

  /** Nom complet de la devise en français. */
  nom: string;

  /** Nombre d'unités mineures (0=JPY, 2=EUR/USD, 3=TND/KWD/BHD). */
  unitesMineures: number;

  /** Symbole monétaire (ex: "د.ت", "€", "$"). */
  symbole: string;

  /** Code numérique ISO 4217 (3 chiffres, ex: "788" pour TND). */
  codeNumerique: string;

  /** La devise est-elle active ? */
  actif: boolean;

  /** Date de création en base. */
  dateCreation?: string;

  /** Date de dernière modification. */
  dateModification?: string;
}

// Liste des devises supportées

/**
 * Codes des 17 devises supportées par BNA.
 */
export const CODES_DEVISES_SUPPORTEES: string[] = [
  'TND', 'EUR', 'KWD', 'USD', 'CAD', 'GBP', 'CHF',
  'BHD', 'SEK', 'SAR', 'QAR', 'NOK', 'JPY', 'DKK',
  'AED', 'CNY', 'LYD',
];

/**
 * Mapping rapide code → symbole pour le formatage.
 */
export const SYMBOLES_DEVISES: Record<string, string> = {
  TND: 'د.ت',
  EUR: '€',
  KWD: 'د.ك',
  USD: '$',
  CAD: 'CA$',
  GBP: '£',
  CHF: 'CHF',
  BHD: 'د.ب',
  SEK: 'kr',
  SAR: '﷼',
  QAR: 'ر.ق',
  NOK: 'kr',
  JPY: '¥',
  DKK: 'kr',
  AED: 'د.إ',
  CNY: '¥',
  LYD: 'ل.د',
};

/**
 * Mapping rapide code → nom pour l'affichage.
 */
export const NOMS_DEVISES: Record<string, string> = {
  TND: 'Dinar Tunisien',
  EUR: 'Euro',
  KWD: 'Dinar Koweïtien',
  USD: 'Dollar Américain',
  CAD: 'Dollar Canadien',
  GBP: 'Livre Sterling',
  CHF: 'Franc Suisse',
  BHD: 'Dinar Bahreïni',
  SEK: 'Couronne Suédoise',
  SAR: 'Riyal Saoudien',
  QAR: 'Riyal Qatari',
  NOK: 'Couronne Norvégienne',
  JPY: 'Yen Japonais',
  DKK: 'Couronne Danoise',
  AED: 'Dirham des Émirats',
  CNY: 'Yuan Chinois',
  LYD: 'Dinar Libyen',
};

// Utilitaires de formatage

/**
 * Formate un montant avec le symbole de la devise.
 *
 * @param montant Le montant à formater
 * @param codeDevise Le code ISO 4217 (optionnel)
 * @param unitesMineures Le nombre de décimales (optionnel, défaut : 2)
 * @returns Le montant formaté (ex: "د.ت 50,000.500")
 */
export function formaterMontant(
  montant: number,
  codeDevise?: string,
  unitesMineures?: number
): string {
  const decimales = unitesMineures ?? 2;
  const symbole = codeDevise ? SYMBOLES_DEVISES[codeDevise] || codeDevise : '';

  const formate = montant.toLocaleString('fr-FR', {
    minimumFractionDigits: decimales,
    maximumFractionDigits: decimales,
  });

  return symbole ? `${symbole} ${formate}` : formate;
}

/**
 * Formate un montant avec le nom de la devise.
 *
 * @param montant Le montant à formater
 * @param codeDevise Le code ISO 4217
 * @param unitesMineures Le nombre de décimales
 * @returns Le montant formaté avec le nom (ex: "50,000.500 Dinar Tunisien")
 */
export function formaterMontantAvecNom(
  montant: number,
  codeDevise: string,
  unitesMineures?: number
): string {
  const decimales = unitesMineures ?? 2;
  const nom = NOMS_DEVISES[codeDevise] || codeDevise;

  const formate = montant.toLocaleString('fr-FR', {
    minimumFractionDigits: decimales,
    maximumFractionDigits: decimales,
  });

  return `${formate} ${nom}`;
}

/**
 * Convertit un montant en unités mineures (centimes, millimes).
 *
 * @param montant Le montant dans l'unité principale
 * @param unitesMineures Le nombre d'unités mineures
 * @returns Le montant en unités mineures
 */
export function versUnitesMineures(montant: number, unitesMineures: number): number {
  return Math.round(montant * Math.pow(10, unitesMineures));
}

/**
 * Convertit un montant depuis les unités mineures vers l'unité principale.
 *
 * @param montant Le montant en unités mineures
 * @param unitesMineures Le nombre d'unités mineures
 * @returns Le montant dans l'unité principale
 */
export function depuisUnitesMineures(montant: number, unitesMineures: number): number {
  return montant / Math.pow(10, unitesMineures);
}