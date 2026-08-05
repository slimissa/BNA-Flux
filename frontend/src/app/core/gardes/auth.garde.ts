import { Injectable, inject } from '@angular/core';
import {
  CanActivateFn,
  Router,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Observable, map, take } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Guard d'authentification fonctionnel.
 *
 * Protège les routes de l'application en vérifiant que l'utilisateur
 * est authentifié avant d'autoriser l'accès.
 *
 * Comportement :
 * - Si l'utilisateur est connecté → autorise l'accès à la route
 * - Si l'utilisateur n'est pas connecté → redirige vers /connexion
 *   avec l'URL demandée en paramètre pour redirection après login
 *
 * Utilisé dans app.routes.ts via canActivate: [AuthGuard].
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */

/**
 * Guard fonctionnel.
 *
 * @param route La route activée
 * @param state L'état du router
 * @returns true si l'accès est autorisé, un UrlTree de redirection sinon
 */
export const authGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): Observable<boolean | UrlTree> => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.estConnecte$.pipe(
    take(1),
    map((estConnecte) => {
      if (estConnecte) {
        // Utilisateur authentifié — accès autorisé
        return true;
      }

      // Utilisateur non authentifié — redirection vers la page de connexion
      // avec l'URL demandée en paramètre pour redirection après login
      return router.createUrlTree(['/connexion'], {
        queryParams: {
          redirectUrl: state.url !== '/connexion' ? state.url : undefined,
        },
      });
    })
  );
};

/**
 * Guard basé sur les rôles.
 *
 * Vérifie que l'utilisateur a le rôle requis pour accéder à la route.
 * La route doit définir data.roles avec la liste des rôles autorisés.
 *
 * Exemple d'utilisation dans les routes :
 * {
 *   path: 'regles',
 *   canActivate: [roleGuard],
 *   data: { roles: ['SUPERVISEUR', 'ADMIN'] }
 * }
 *
 * @param rolesRequis Liste des rôles autorisés (définie dans data.roles)
 */
export const roleGuard = (rolesRequis: string[]): CanActivateFn => {
  return (
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean | UrlTree> => {
    const authService = inject(AuthService);
    const router = inject(Router);

    return authService.estConnecte$.pipe(
      take(1),
      map((estConnecte) => {
        if (!estConnecte) {
          return router.createUrlTree(['/connexion']);
        }

        const roleUtilisateur = authService.getUtilisateur()?.role;

        if (roleUtilisateur && rolesRequis.includes(roleUtilisateur)) {
          return true;
        }

        // Accès refusé — redirection vers le tableau de bord avec un message
        return router.createUrlTree(['/tableau-bord'], {
          queryParams: { accesRefuse: 'true' },
        });
      })
    );
  };
};

/**
 * Guard basé sur les permissions d'agence.
 *
 * Vérifie que l'utilisateur a accès à l'agence spécifiée dans la route.
 * Les ADMIN ont accès à toutes les agences.
 * Les OPERATEUR et SUPERVISEUR n'ont accès qu'à leur propre agence.
 *
 * @param route La route activée (peut contenir le paramètre :codeAgence)
 */
export const agenceGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): Observable<boolean | UrlTree> => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.estConnecte$.pipe(
    take(1),
    map((estConnecte) => {
      if (!estConnecte) {
        return router.createUrlTree(['/connexion']);
      }

      const utilisateur = authService.getUtilisateur();
      if (!utilisateur) {
        return router.createUrlTree(['/connexion']);
      }

      // ADMIN a accès à toutes les agences
      if (utilisateur.role === 'ADMIN') {
        return true;
      }

      // Vérifier l'agence demandée
      const codeAgenceDemandee = route.params['codeAgence'] || route.queryParams['agence'];

      if (!codeAgenceDemandee) {
        // Pas d'agence spécifique demandée — autoriser
        return true;
      }

      if (utilisateur.agence === codeAgenceDemandee) {
        return true;
      }

      // Accès inter-agence refusé
      return router.createUrlTree(['/tableau-bord'], {
        queryParams: {
          accesRefuse: 'true',
          raison: 'agence',
        },
      });
    })
  );
};

/**
 * Guard de redirection post-connexion.
 *
 * Si l'utilisateur est déjà connecté et tente d'accéder à /connexion,
 * il est redirigé vers le tableau de bord.
 */
export const connexionGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): Observable<boolean | UrlTree> => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.estConnecte$.pipe(
    take(1),
    map((estConnecte) => {
      if (estConnecte) {
        // Déjà connecté — rediriger vers le tableau de bord
        const redirectUrl = route.queryParams['redirectUrl'];
        return redirectUrl
          ? router.parseUrl(redirectUrl)
          : router.createUrlTree(['/tableau-bord']);
      }

      // Pas connecté — autoriser l'accès à la page de connexion
      return true;
    })
  );
};

/**
 * Guard classique (classe) pour compatibilité avec les anciennes
 * configurations Angular qui utilisent @Injectable() et implements CanActivate.
 *
 * @deprecated Utiliser les guards fonctionnels dans les nouvelles configurations.
 */
@Injectable({ providedIn: 'root' })
export class AuthGuard {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean | UrlTree> {
    return authGuard(route, state);
  }
}