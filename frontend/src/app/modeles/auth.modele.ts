/**
 * Modèles de données pour l'authentification.
 *
 * Inclut les interfaces pour la requête de connexion,
 * la réponse JWT, et les informations de l'utilisateur connecté.
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */

/**
 * Requête de connexion envoyée au endpoint POST /api/auth/connexion.
 */
export interface RequeteConnexion {
  /** Adresse email de l'utilisateur (identifiant de connexion). */
  email: string;

  /** Mot de passe en clair. Ne jamais logger. */
  motDePasse: string;
}

/**
 * Réponse de connexion réussie retournée par POST /api/auth/connexion.
 */
export interface ReponseConnexion {
  /** Statut de la réponse ("SUCCES"). */
  statut: string;

  /** Token d'accès JWT (durée : 60 minutes). */
  tokenAcces: string;

  /** Token de rafraîchissement JWT (durée : 24 heures). */
  tokenRafraichissement: string;

  /** Type de token ("Bearer"). */
  typeToken: string;

  /** Durée de validité du token d'accès en secondes. */
  expireDans: number;

  /** Informations de l'utilisateur connecté. */
  utilisateur: UtilisateurInfo;
}

/**
 * Informations de l'utilisateur connecté (incluses dans la réponse de connexion).
 */
export interface UtilisateurInfo {
  /** Email de l'utilisateur. */
  email: string;

  /** Nom complet de l'utilisateur. */
  nom: string;

  /** Rôle de l'utilisateur (OPERATEUR, SUPERVISEUR, ADMIN). */
  role: RoleUtilisateur;

  /** Code agence de l'utilisateur (null pour ADMIN). */
  agence: string | null;
}

/**
 * Rôles disponibles dans BNA-FLUX.
 */
export type RoleUtilisateur = 'OPERATEUR' | 'SUPERVISEUR' | 'ADMIN';

/**
 * Réponse de rafraîchissement de token.
 */
export interface ReponseRafraichissement {
  /** Statut de la réponse. */
  statut: string;

  /** Nouveau token d'accès. */
  tokenAcces: string;

  /** Type de token. */
  typeToken: string;

  /** Durée de validité en secondes. */
  expireDans: number;
}

/**
 * État d'authentification stocké dans le service AuthService.
 */
export interface EtatAuthentification {
  /** L'utilisateur est-il authentifié ? */
  estConnecte: boolean;

  /** Informations de l'utilisateur (null si non connecté). */
  utilisateur: UtilisateurInfo | null;

  /** Token d'accès JWT (null si non connecté). */
  tokenAcces: string | null;

  /** Token de rafraîchissement (null si non connecté). */
  tokenRafraichissement: string | null;
}

/**
 * Labels français des rôles pour l'affichage.
 */
export const LABELS_ROLES: Record<RoleUtilisateur, string> = {
  ADMIN: 'Administrateur',
  SUPERVISEUR: 'Superviseur',
  OPERATEUR: 'Opérateur',
};

/**
 * Couleurs associées aux rôles pour les badges.
 */
export const COULEURS_ROLES: Record<RoleUtilisateur, string> = {
  ADMIN: '#e74c3c',
  SUPERVISEUR: '#f39c12',
  OPERATEUR: '#3498db',
};