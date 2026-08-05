import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  ResumeTableauBord,
  StatistiquesRapides,
  TendanceJournaliere,
  PeriodeTendance,
} from '@modeles/tableau-bord.modele';

/**
 * Service de consultation du tableau de bord.
 *
 * Responsabilités :
 * - Récupérer le résumé complet du dashboard
 * - Récupérer la tendance journalière/hebdomadaire/mensuelle
 * - Récupérer les statistiques rapides pour les widgets
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@Injectable({
  providedIn: 'root',
})
export class TableauBordService {
  private readonly http = inject(HttpClient);

  /** URL de base de l'API du tableau de bord */
  private readonly apiUrl = '/api/tableau-bord';

  // Résumé complet

  /**
   * Récupère le résumé complet du tableau de bord.
   *
   * @param dateDebut Date de début (optionnel, défaut : aujourd'hui)
   * @param dateFin Date de fin (optionnel, défaut : aujourd'hui)
   * @returns Observable du résumé complet
   */
  getResume(dateDebut?: string, dateFin?: string): Observable<ResumeTableauBord> {
    let params = new HttpParams();

    if (dateDebut) {
      params = params.set('dateDebut', dateDebut);
    }
    if (dateFin) {
      params = params.set('dateFin', dateFin);
    }

    return this.http
      .get<{ statut: string; donnees: ResumeTableauBord; horodatage: string }>(
        `${this.apiUrl}/resume`,
        { params }
      )
      .pipe(map((reponse) => reponse.donnees));
  }

  /**
   * Récupère le résumé pour une plage de dates spécifique.
   *
   * @param dateDebut Date de début
   * @param dateFin Date de fin
   * @returns Observable du résumé
   */
  getResumeParPeriode(dateDebut: string, dateFin: string): Observable<ResumeTableauBord> {
    return this.getResume(dateDebut, dateFin);
  }

  /**
   * Récupère le résumé pour la journée en cours.
   *
   * @returns Observable du résumé du jour
   */
  getResumeAujourdhui(): Observable<ResumeTableauBord> {
    const aujourdhui = new Date().toISOString().split('T')[0];
    return this.getResume(aujourdhui, aujourdhui);
  }

  // Tendance

  /**
   * Récupère la tendance des transactions sur une période.
   *
   * @param periode Type de période (JOURNALIER, HEBDOMADAIRE, MENSUEL)
   * @param dateDebut Date de début (optionnel)
   * @param dateFin Date de fin (optionnel)
   * @returns Observable des données de tendance
   */
  getTendance(
    periode: PeriodeTendance = 'JOURNALIER',
    dateDebut?: string,
    dateFin?: string
  ): Observable<{
    statut: string;
    periode: { debut: string; fin: string };
    typePeriode: string;
    tendance: TendanceJournaliere[];
    horodatage: string;
  }> {
    let params = new HttpParams().set('periode', periode);

    if (dateDebut) {
      params = params.set('debut', dateDebut);
    }
    if (dateFin) {
      params = params.set('fin', dateFin);
    }

    return this.http.get<any>(`${this.apiUrl}/tendance`, { params });
  }

  /**
   * Récupère la tendance des 7 derniers jours.
   *
   * @returns Observable de la tendance journalière
   */
  getTendance7Jours(): Observable<TendanceJournaliere[]> {
    const fin = new Date().toISOString().split('T')[0];
    const debut = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)
      .toISOString()
      .split('T')[0];

    return this.getTendance('JOURNALIER', debut, fin).pipe(
      map((reponse) => reponse.tendance)
    );
  }

  /**
   * Récupère la tendance des 4 dernières semaines.
   *
   * @returns Observable de la tendance hebdomadaire
   */
  getTendance4Semaines(): Observable<TendanceJournaliere[]> {
    return this.getTendance('HEBDOMADAIRE').pipe(
      map((reponse) => reponse.tendance)
    );
  }

  /**
   * Récupère la tendance des 12 derniers mois.
   *
   * @returns Observable de la tendance mensuelle
   */
  getTendance12Mois(): Observable<TendanceJournaliere[]> {
    return this.getTendance('MENSUEL').pipe(
      map((reponse) => reponse.tendance)
    );
  }

  // Statistiques rapides (widgets)

  /**
   * Récupère les statistiques rapides pour les widgets du dashboard.
   *
   * Plus léger que le résumé complet — uniquement les compteurs principaux.
   *
   * @returns Observable des statistiques rapides
   */
  getStatistiques(): Observable<StatistiquesRapides> {
    return this.http
      .get<{ statut: string; statistiques: StatistiquesRapides; horodatage: string }>(
        `${this.apiUrl}/statistiques`
      )
      .pipe(map((reponse) => reponse.statistiques));
  }

  // Méthodes utilitaires

  /**
   * Calcule le taux d'acceptation des transactions.
   *
   * @param resume Le résumé du tableau de bord
   * @returns Le pourcentage de transactions acceptées (0-100)
   */
  getTauxAcceptation(resume: ResumeTableauBord): number {
    if (!resume.transactions || resume.transactions.total === 0) {
      return 0;
    }
    return Math.round(
      (resume.transactions.acceptees / resume.transactions.total) * 100
    );
  }

  /**
   * Calcule le taux de blocage des transactions.
   *
   * @param resume Le résumé du tableau de bord
   * @returns Le pourcentage de transactions bloquées (0-100)
   */
  getTauxBlocage(resume: ResumeTableauBord): number {
    if (!resume.transactions || resume.transactions.total === 0) {
      return 0;
    }
    return Math.round(
      (resume.transactions.bloquees / resume.transactions.total) * 100
    );
  }

  /**
   * Vérifie si le tableau de bord contient des alertes critiques.
   *
   * @param resume Le résumé du tableau de bord
   * @returns true si des actions sont requises
   */
  aDesActionsRequises(resume: ResumeTableauBord): boolean {
    return (
      (resume.alertes?.actionsRequises ?? 0) > 0 ||
      (resume.disjoncteurs?.ouverts ?? 0) > 0
    );
  }
}