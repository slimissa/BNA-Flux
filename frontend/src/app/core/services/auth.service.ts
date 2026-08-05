import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, catchError, map, tap, throwError } from 'rxjs';
import {
  RequeteConnexion,
  ReponseConnexion,
  ReponseRafraichissement,
  UtilisateurInfo,
  EtatAuthentification,
} from '@modeles/auth.modele';

/**
 * Service d'authentification et de gestion des tokens JWT.
 *
 * Responsabilités :
 * - Authentification par email/mot de passe
 * - Stockage des tokens dans le localStorage
 * - Rafraîchissement automatique du token d'accès
 * - Exposition de l'état de connexion (Observable)
 * - Déconnexion avec nettoyage du localStorage
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  /** URL de base de l'API d'authentification */
  private readonly apiUrl = '/api/auth';

  /** Clés de stockage dans le localStorage */
  private readonly CLE_TOKEN_ACCES = 'bna_token_acces';
  private readonly CLE_TOKEN_RAFRAICHISSEMENT = 'bna_token_rafraichissement';
  private readonly CLE_UTILISATEUR = 'bna_utilisateur';

  /** État d'authentification — BehaviorSubject pour les souscriptions */
  private readonly etatSubject = new BehaviorSubject<EtatAuthentification>(
    this.chargerEtatInitial()
  );

  /** Observable public de l'état d'authentification */
  readonly estConnecte$: Observable<boolean> = this.etatSubject.pipe(
    map((etat) => etat.estConnecte)
  );

  /** Observable des informations utilisateur */
  readonly utilisateur$: Observable<UtilisateurInfo | null> = this.etatSubject.pipe(
    map((etat) => etat.utilisateur)
  );

  // Connexion

  /**
   * Authentifie l'utilisateur avec email et mot de passe.
   *
   * Stocke les tokens et les infos utilisateur dans le localStorage
   * et met à jour l'état d'authentification.
   *
   * @param email L'adresse email
   * @param motDePasse Le mot de passe en clair
   * @returns Observable de la réponse de connexion
   */
  connexion(email: string, motDePasse: string): Observable<ReponseConnexion> {
    const requete: RequeteConnexion = {
      email: email.trim().toLowerCase(),
      motDePasse,
    };

    return this.http.post<ReponseConnexion>(`${this.apiUrl}/connexion`, requete).pipe(
      tap((reponse) => {
        // Stocker les tokens et infos utilisateur
        this.stockerTokens(reponse);
        this.stockerUtilisateur(reponse.utilisateur);

        // Mettre à jour l'état
        this.mettreAJourEtat({
          estConnecte: true,
          utilisateur: reponse.utilisateur,
          tokenAcces: reponse.tokenAcces,
          tokenRafraichissement: reponse.tokenRafraichissement,
        });
      }),
      catchError(this.gererErreur.bind(this))
    );
  }

  // Déconnexion

  /**
   * Déconnecte l'utilisateur.
   *
   * Supprime les tokens et infos du localStorage,
   * met à jour l'état, et redirige vers la page de connexion.
   */
  deconnexion(): void {
    // Appeler l'API de déconnexion (optionnel — juste pour le log serveur)
    this.http.post(`${this.apiUrl}/deconnexion`, {}).subscribe();

    // Nettoyer le localStorage
    localStorage.removeItem(this.CLE_TOKEN_ACCES);
    localStorage.removeItem(this.CLE_TOKEN_RAFRAICHISSEMENT);
    localStorage.removeItem(this.CLE_UTILISATEUR);

    // Mettre à jour l'état
    this.mettreAJourEtat({
      estConnecte: false,
      utilisateur: null,
      tokenAcces: null,
      tokenRafraichissement: null,
    });
  }

  // Rafraîchissement du token

  /**
   * Rafraîchit le token d'accès en utilisant le token de rafraîchissement.
   *
   * Appelé automatiquement par l'intercepteur HTTP en cas d'erreur 401.
   *
   * @param tokenRafraichissement Le token de rafraîchissement
   * @returns Observable du nouveau token d'accès (string)
   */
  rafraichirToken(tokenRafraichissement: string): Observable<string> {
    return this.http
      .post<ReponseRafraichissement>(
        `${this.apiUrl}/rafraichir`,
        {},
        {
          headers: {
            Authorization: `Bearer ${tokenRafraichissement}`,
          },
        }
      )
      .pipe(
        map((reponse) => {
          // Stocker le nouveau token d'accès
          localStorage.setItem(this.CLE_TOKEN_ACCES, reponse.tokenAcces);

          // Mettre à jour l'état
          const etatActuel = this.etatSubject.getValue();
          this.mettreAJourEtat({
            ...etatActuel,
            tokenAcces: reponse.tokenAcces,
          });

          return reponse.tokenAcces;
        }),
        catchError((erreur) => {
          // Échec du rafraîchissement → déconnexion
          this.deconnexion();
          return throwError(() => erreur);
        })
      );
  }

  // Accesseurs

  /** Retourne le token d'accès stocké. */
  getTokenAcces(): string | null {
    return localStorage.getItem(this.CLE_TOKEN_ACCES);
  }

  /** Retourne le token de rafraîchissement stocké. */
  getTokenRafraichissement(): string | null {
    return localStorage.getItem(this.CLE_TOKEN_RAFRAICHISSEMENT);
  }

  /** Retourne les informations de l'utilisateur connecté. */
  getUtilisateur(): UtilisateurInfo | null {
    const data = localStorage.getItem(this.CLE_UTILISATEUR);
    if (!data) return null;
    try {
      return JSON.parse(data);
    } catch {
      return null;
    }
  }

  /** Vérifie si l'utilisateur est actuellement connecté. */
  estConnecte(): boolean {
    return this.etatSubject.getValue().estConnecte;
  }

  /** Vérifie si l'utilisateur a un rôle spécifique. */
  aRole(role: string): boolean {
    const utilisateur = this.getUtilisateur();
    return utilisateur?.role === role;
  }

  /** Vérifie si l'utilisateur a un accès SUPERVISEUR ou ADMIN. */
  peutGererRegles(): boolean {
    const utilisateur = this.getUtilisateur();
    return utilisateur?.role === 'SUPERVISEUR' || utilisateur?.role === 'ADMIN';
  }

  /** Vérifie si l'utilisateur a un accès ADMIN. */
  estAdmin(): boolean {
    return this.aRole('ADMIN');
  }

  /** Met à jour le token d'accès (utilisé par l'intercepteur). */
  setTokenAcces(token: string): void {
    localStorage.setItem(this.CLE_TOKEN_ACCES, token);
  }

  // Méthodes privées
  

  /**
   * Stocke les tokens JWT dans le localStorage.
   */
  private stockerTokens(reponse: ReponseConnexion): void {
    localStorage.setItem(this.CLE_TOKEN_ACCES, reponse.tokenAcces);
    localStorage.setItem(this.CLE_TOKEN_RAFRAICHISSEMENT, reponse.tokenRafraichissement);
  }

  /**
   * Stocke les informations utilisateur dans le localStorage.
   */
  private stockerUtilisateur(utilisateur: UtilisateurInfo): void {
    localStorage.setItem(this.CLE_UTILISATEUR, JSON.stringify(utilisateur));
  }

  /**
   * Émet le nouvel état d'authentification aux abonnés.
   */
  private mettreAJourEtat(etat: EtatAuthentification): void {
    this.etatSubject.next(etat);
  }

  /**
   * Charge l'état initial depuis le localStorage.
   */
  private chargerEtatInitial(): EtatAuthentification {
    const tokenAcces = this.getTokenAcces();
    const tokenRafraichissement = this.getTokenRafraichissement();
    const utilisateur = this.getUtilisateur();

    return {
      estConnecte: !!tokenAcces && !!utilisateur,
      utilisateur,
      tokenAcces,
      tokenRafraichissement,
    };
  }

  /**
   * Gère les erreurs HTTP d'authentification.
   */
  private gererErreur(erreur: HttpErrorResponse): Observable<never> {
    let message = 'Une erreur est survenue lors de la connexion.';

    if (erreur.status === 401) {
      message = 'Email ou mot de passe incorrect.';
    } else if (erreur.status === 0) {
      message = 'Impossible de contacter le serveur. Vérifiez votre connexion.';
    } else if (erreur.error?.message) {
      message = erreur.error.message;
    }

    return throwError(() => new Error(message));
  }
}