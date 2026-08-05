import { Injectable, inject } from '@angular/core';
import {
  HttpRequest,
  HttpHandlerFn,
  HttpEvent,
  HttpInterceptorFn,
  HttpErrorResponse,
} from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, throwError, switchMap } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Intercepteur HTTP fonctionnel pour l'authentification JWT.
 *
 * Responsabilités :
 * 1. Ajoute le header Authorization: Bearer <token> à toutes les requêtes
 *    sortantes (sauf endpoints publics).
 * 2. Intercepte les erreurs 401 (token expiré) :
 *    - Tente un rafraîchissement automatique du token
 *    - Réessaie la requête originale avec le nouveau token
 *    - Si le rafraîchissement échoue, redirige vers la page de connexion
 * 3. Ne modifie pas les requêtes vers les endpoints publics
 *    (/api/auth, /api/devises GET, /actuator/health).
 *
 * Utilisé dans app.config.ts via withInterceptors([authInterceptor]).
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */

/**
 * Endpoints publics qui ne nécessitent pas de token JWT.
 */
const ENDPOINTS_PUBLICS = [
  '/api/auth/',
  '/api/devises',
  '/actuator/health',
  '/swagger-ui',
  '/api-docs',
];

/**
 * Vérifie si une URL correspond à un endpoint public.
 */
function estEndpointPublic(url: string): boolean {
  return ENDPOINTS_PUBLICS.some((endpoint) => url.includes(endpoint));
}

/**
 * Flag pour éviter les boucles infinies de rafraîchissement.
 */
let estEnCoursDeRafraichissement = false;

/**
 * Intercepteur fonctionnel.
 *
 * @param req La requête HTTP sortante
 * @param next Le handler suivant dans la chaîne
 * @returns Observable de l'événement HTTP
 */
export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
): Observable<HttpEvent<unknown>> => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Ne pas ajouter le token pour les endpoints publics
  if (estEndpointPublic(req.url)) {
    return next(req);
  }

  // Récupérer le token d'accès
  const tokenAcces = authService.getTokenAcces();

  // Cloner la requête avec le header Authorization si le token existe
  let requeteAuthentifiee = req;
  if (tokenAcces) {
    requeteAuthentifiee = req.clone({
      setHeaders: {
        Authorization: `Bearer ${tokenAcces}`,
      },
    });
  }

  // Exécuter la requête et gérer les erreurs
  return next(requeteAuthentifiee).pipe(
    catchError((erreur: HttpErrorResponse) => {
      // Si ce n'est pas une erreur 401 ou si on est déjà en train de rafraîchir,
      // on propage l'erreur telle quelle
      if (erreur.status !== 401 || estEnCoursDeRafraichissement) {
        return throwError(() => erreur);
      }

      // Tenter un rafraîchissement du token
      estEnCoursDeRafraichissement = true;
      const tokenRafraichissement = authService.getTokenRafraichissement();

      if (!tokenRafraichissement) {
        // Pas de refresh token → déconnexion forcée
        estEnCoursDeRafraichissement = false;
        authService.deconnexion();
        router.navigate(['/connexion'], {
          queryParams: { expired: 'true' },
        });
        return throwError(() => erreur);
      }

      // Appeler le service de rafraîchissement
      return authService.rafraichirToken(tokenRafraichissement).pipe(
        switchMap((nouveauToken) => {
          estEnCoursDeRafraichissement = false;

          // Mettre à jour le token dans le service
          authService.setTokenAcces(nouveauToken);

          // Réessayer la requête originale avec le nouveau token
          const requeteReessayee = req.clone({
            setHeaders: {
              Authorization: `Bearer ${nouveauToken}`,
            },
          });

          return next(requeteReessayee);
        }),
        catchError((erreurRafraichissement) => {
          // Le rafraîchissement a échoué → déconnexion
          estEnCoursDeRafraichissement = false;
          authService.deconnexion();
          router.navigate(['/connexion'], {
            queryParams: { expired: 'true' },
          });
          return throwError(() => erreurRafraichissement);
        })
      );
    })
  );
};

/**
 * Intercepteur à l'ancienne (basé sur la classe HttpInterceptor).
 * Conservé pour compatibilité avec les modules Angular qui utilisent
 * encore le provider HTTP_INTERCEPTORS (comme ToastrModule).
 *
 * @deprecated Utiliser authInterceptor (fonctionnel) dans les nouvelles configurations.
 */
@Injectable()
export class AuthInterceptor {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  intercept(
    req: HttpRequest<unknown>,
    next: HttpHandlerFn
  ): Observable<HttpEvent<unknown>> {
    return authInterceptor(req, next);
  }
}