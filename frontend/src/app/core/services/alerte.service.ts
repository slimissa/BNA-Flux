import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  ReponseAlerte,
  FiltresAlerte,
  ReponseAcquittement,
  EmailEnvoye,
} from '@modeles/alerte.modele';
import { PageReponse } from '@modeles/transaction.modele';

/**
 * Service de gestion des alertes de surveillance.
 *
 * Responsabilités :
 * - Consulter les alertes avec filtrage et pagination
 * - Acquitter une alerte après revue manuelle
 * - Consulter l'historique des emails envoyés
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@Injectable({
  providedIn: 'root',
})
export class AlerteService {
  private readonly http = inject(HttpClient);

  /** URL de base de l'API des alertes */
  private readonly apiUrl = '/api/alertes';

  // Consultation

  /**
   * Recherche les alertes avec filtrage et pagination.
   *
   * @param filtres Les critères de filtrage
   * @returns Observable de la page d'alertes
   */
  rechercher(filtres: FiltresAlerte): Observable<PageReponse<ReponseAlerte>> {
    let params = new HttpParams()
      .set('page', filtres.page.toString())
      .set('taille', filtres.taille.toString());

    if (filtres.niveau) {
      params = params.set('niveau', filtres.niveau);
    }
    if (filtres.acquittee !== undefined && filtres.acquittee !== null) {
      params = params.set('acquittee', filtres.acquittee.toString());
    }
    if (filtres.dateDebut) {
      params = params.set('dateDebut', filtres.dateDebut);
    }
    if (filtres.dateFin) {
      params = params.set('dateFin', filtres.dateFin);
    }

    return this.http.get<PageReponse<ReponseAlerte>>(this.apiUrl, { params });
  }

  /**
   * Récupère toutes les alertes avec pagination simple.
   *
   * @param page Numéro de page
   * @param taille Taille de la page
   * @returns Observable de la page d'alertes
   */
  lister(page: number = 0, taille: number = 20): Observable<PageReponse<ReponseAlerte>> {
    return this.rechercher({ page, taille });
  }

  /**
   * Récupère les alertes non acquittées.
   *
   * @param page Numéro de page
   * @param taille Taille de la page
   * @returns Observable de la page d'alertes non acquittées
   */
  getNonAcquittees(page: number = 0, taille: number = 20): Observable<PageReponse<ReponseAlerte>> {
    return this.rechercher({ page, taille, acquittee: false });
  }

  /**
   * Récupère les alertes par niveau de sévérité.
   *
   * @param niveau Le niveau de sévérité
   * @param page Numéro de page
   * @param taille Taille de la page
   * @returns Observable de la page d'alertes
   */
  getParNiveau(
    niveau: ReponseAlerte['niveau'],
    page: number = 0,
    taille: number = 20
  ): Observable<PageReponse<ReponseAlerte>> {
    return this.rechercher({ page, taille, niveau });
  }

  /**
   * Récupère le détail d'une alerte.
   *
   * @param id L'identifiant de l'alerte
   * @returns Observable du détail de l'alerte
   */
  getParId(id: number): Observable<ReponseAlerte> {
    return this.http
      .get<{ statut: string; donnees: ReponseAlerte; horodatage: string }>(
        `${this.apiUrl}/${id}`
      )
      .pipe(map((reponse) => reponse.donnees));
  }

  // Acquittement

  /**
   * Acquitte une alerte après revue manuelle.
   *
   * @param id L'identifiant de l'alerte
   * @returns Observable de la réponse d'acquittement
   */
  acquitter(id: number): Observable<ReponseAcquittement> {
    return this.http.put<ReponseAcquittement>(`${this.apiUrl}/${id}/acquitter`, {});
  }

  // Emails

  /**
   * Récupère l'historique des emails envoyés.
   *
   * @param page Numéro de page
   * @param taille Taille de la page
   * @returns Observable de la liste des emails
   */
  getEmailsEnvoyes(page: number = 0, taille: number = 20): Observable<EmailEnvoye[]> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('taille', taille.toString());

    return this.http
      .get<{ statut: string; emails: EmailEnvoye[]; horodatage: string }>(
        `${this.apiUrl}/emails-envoyes`,
        { params }
      )
      .pipe(map((reponse) => reponse.emails));
  }
}