import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, shareReplay } from 'rxjs';
import { Devise } from '@modeles/devise.modele';

/**
 * Service de consultation des devises ISO 4217.
 *
 * Responsabilités :
 * - Récupérer la liste des devises actives (endpoint public)
 * - Mettre en cache la liste pour éviter les appels répétés
 * - Rechercher une devise par son code
 *
 * Note : Cet endpoint est public (pas d'authentification requise).
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@Injectable({
  providedIn: 'root',
})
export class DeviseService {
  private readonly http = inject(HttpClient);

  /** URL de base de l'API des devises (endpoint public) */
  private readonly apiUrl = '/api/devises';

  /** Cache de la liste des devises (partagé entre tous les abonnés) */
  private cacheDevises$: Observable<Devise[]> | null = null;

  // Consultation

  /**
   * Récupère la liste de toutes les devises actives.
   *
   * La réponse est mise en cache via shareReplay() pour éviter
   * des appels réseau répétés (les devises changent rarement).
   *
   * @returns Observable de la liste des devises
   */
  lister(): Observable<Devise[]> {
    if (!this.cacheDevises$) {
      this.cacheDevises$ = this.http
        .get<{ statut: string; nombre: number; devises: Devise[]; horodatage: string }>(
          this.apiUrl
        )
        .pipe(
          map((reponse) => reponse.devises),
          shareReplay(1) // Cache la dernière valeur pour tous les abonnés
        );
    }
    return this.cacheDevises$;
  }

  /**
   * Récupère le détail d'une devise par son code ISO 4217.
   *
   * @param code Le code devise (ex: TND, EUR, USD)
   * @returns Observable du détail de la devise
   */
  getParCode(code: string): Observable<Devise> {
    return this.http
      .get<{ statut: string; devise: Devise; horodatage: string }>(
        `${this.apiUrl}/${code.toUpperCase()}`
      )
      .pipe(map((reponse) => reponse.devise));
  }

  // Méthodes utilitaires

  /**
   * Vide le cache des devises.
   * Utile après une modification des devises (admin).
   */
  viderCache(): void {
    this.cacheDevises$ = null;
  }

  /**
   * Recherche une devise par son code dans une liste donnée.
   *
   * @param devises La liste des devises
   * @param code Le code recherché
   * @returns La devise trouvée ou undefined
   */
  trouverParCode(devises: Devise[], code: string): Devise | undefined {
    return devises.find(
      (d) => d.code.toUpperCase() === code.toUpperCase()
    );
  }

  /**
   * Récupère le symbole d'une devise par son code.
   *
   * @param code Le code devise
   * @returns Observable du symbole
   */
  getSymbole(code: string): Observable<string> {
    return this.getParCode(code).pipe(
      map((devise) => devise.symbole),
      map((symbole) => symbole || code)
    );
  }

  /**
   * Récupère les unités mineures d'une devise.
   *
   * @param code Le code devise
   * @returns Observable du nombre d'unités mineures
   */
  getUnitesMineures(code: string): Observable<number> {
    return this.getParCode(code).pipe(
      map((devise) => devise.unitesMineures)
    );
  }

  /**
   * Vérifie si un code devise est valide.
   *
   * @param code Le code à vérifier
   * @returns Observable de true si la devise existe et est active
   */
  estValide(code: string): Observable<boolean> {
    return this.lister().pipe(
      map((devises) =>
        devises.some(
          (d) => d.code.toUpperCase() === code.toUpperCase() && d.actif
        )
      )
    );
  }
}