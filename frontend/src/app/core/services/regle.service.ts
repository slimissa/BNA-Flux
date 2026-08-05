import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  RequeteRegle,
  ReponseRegle,
  RequeteTestRegle,
  ReponseTestRegle,
  FiltresRegle,
} from '@modeles/regle.modele';

/**
 * Service de gestion des règles de surveillance.
 *
 * Responsabilités :
 * - CRUD complet des règles (créer, lire, modifier, supprimer)
 * - Activer / Désactiver / Basculer une règle
 * - Tester une expression SpEL avant sauvegarde
 * - Récupérer les catégories distinctes
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@Injectable({
  providedIn: 'root',
})
export class RegleService {
  private readonly http = inject(HttpClient);

  /** URL de base de l'API des règles */
  private readonly apiUrl = '/api/regles';

  // CRUD

  /**
   * Récupère toutes les règles.
   *
   * @param filtres Filtres optionnels (catégorie)
   * @returns Observable de la liste des règles
   */
  lister(filtres?: FiltresRegle): Observable<ReponseRegle[]> {
    let params = new HttpParams();

    if (filtres?.categorie) {
      params = params.set('categorie', filtres.categorie);
    }

    return this.http
      .get<{ statut: string; nombre: number; regles: ReponseRegle[]; horodatage: string }>(
        this.apiUrl,
        { params }
      )
      .pipe(map((reponse) => reponse.regles));
  }

  /**
   * Récupère une règle par son identifiant.
   *
   * @param id L'identifiant de la règle
   * @returns Observable du détail de la règle
   */
  getParId(id: number): Observable<ReponseRegle> {
    return this.http
      .get<{ statut: string; regle: ReponseRegle; horodatage: string }>(`${this.apiUrl}/${id}`)
      .pipe(map((reponse) => reponse.regle));
  }

  /**
   * Crée une nouvelle règle de surveillance.
   *
   * @param requete Les données de la règle
   * @returns Observable de la règle créée
   */
  creer(requete: RequeteRegle): Observable<ReponseRegle> {
    return this.http
      .post<{ statut: string; message: string; regle: ReponseRegle; horodatage: string }>(
        this.apiUrl,
        requete
      )
      .pipe(map((reponse) => reponse.regle));
  }

  /**
   * Modifie une règle existante.
   *
   * @param id L'identifiant de la règle
   * @param requete Les nouvelles données
   * @returns Observable de la règle modifiée
   */
  modifier(id: number, requete: RequeteRegle): Observable<ReponseRegle> {
    return this.http
      .put<{ statut: string; message: string; regle: ReponseRegle; horodatage: string }>(
        `${this.apiUrl}/${id}`,
        requete
      )
      .pipe(map((reponse) => reponse.regle));
  }

  /**
   * Supprime définitivement une règle.
   *
   * @param id L'identifiant de la règle
   * @returns Observable vide
   */
  supprimer(id: number): Observable<void> {
    return this.http
      .delete<{ statut: string; message: string; horodatage: string }>(`${this.apiUrl}/${id}`)
      .pipe(map(() => undefined));
  }

  // Activation / Désactivation

  /**
   * Bascule l'état actif/inactif d'une règle.
   *
   * @param id L'identifiant de la règle
   * @returns Observable avec le nouvel état
   */
  basculer(id: number): Observable<{ statut: string; message: string; actif: boolean }> {
    return this.http.put<{ statut: string; message: string; actif: boolean; horodatage: string }>(
      `${this.apiUrl}/${id}/basculer`,
      {}
    );
  }

  /**
   * Active une règle.
   *
   * @param id L'identifiant de la règle
   */
  activer(id: number): Observable<void> {
    // Le basculement gère l'activation si la règle est inactive
    return this.basculer(id).pipe(map(() => undefined));
  }

  /**
   * Désactive une règle (suppression logique).
   *
   * @param id L'identifiant de la règle
   */
  desactiver(id: number): Observable<void> {
    return this.basculer(id).pipe(map(() => undefined));
  }

  // Test d'expression SpEL

  /**
   * Teste une expression SpEL avant de créer ou modifier une règle.
   *
   * @param requete L'expression à tester et optionnellement l'ID d'une transaction test
   * @returns Observable du résultat du test
   */
  testerExpression(requete: RequeteTestRegle): Observable<ReponseTestRegle> {
    return this.http.post<ReponseTestRegle>(`${this.apiUrl}/tester`, requete);
  }

  /**
   * Valide rapidement la syntaxe d'une expression SpEL.
   *
   * @param expression L'expression à valider
   * @returns Observable du résultat
   */
  validerSyntaxe(expression: string): Observable<ReponseTestRegle> {
    return this.testerExpression({ expression });
  }

  // Catégories

  /**
   * Récupère la liste des catégories distinctes de règles.
   *
   * @returns Observable de la liste des catégories
   */
  getCategories(): Observable<string[]> {
    return this.http
      .get<{ statut: string; categories: string[]; horodatage: string }>(
        `${this.apiUrl}/categories`
      )
      .pipe(map((reponse) => reponse.categories));
  }
}