import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  ReponseDisjoncteur,
  EtatDisjoncteur,
} from '@modeles/disjoncteur.modele';

/**
 * Service de gestion des disjoncteurs (Circuit Breakers).
 *
 * Responsabilités :
 * - Consulter l'état de tous les disjoncteurs
 * - Filtrer par état (OUVERT, FERME, MI_OUVERT)
 * - Consulter le détail d'un disjoncteur spécifique
 * - Réinitialiser manuellement un disjoncteur (SUPERVISEUR/ADMIN)
 * - Récupérer uniquement les disjoncteurs ouverts (raccourci)
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@Injectable({
  providedIn: 'root',
})
export class DisjoncteurService {
  private readonly http = inject(HttpClient);

  /** URL de base de l'API des disjoncteurs */
  private readonly apiUrl = '/api/disjoncteurs';

  // Consultation

  /**
   * Récupère tous les disjoncteurs avec filtrage optionnel par état.
   *
   * @param etat Filtre par état (optionnel)
   * @returns Observable de la liste des disjoncteurs avec statistiques
   */
  lister(etat?: EtatDisjoncteur): Observable<{
    statut: string;
    nombre: number;
    disjoncteurs: ReponseDisjoncteur[];
    statistiques: StatistiquesDisjoncteurs;
    horodatage: string;
  }> {
    let params = new HttpParams();

    if (etat) {
      params = params.set('etat', etat);
    }

    return this.http.get<any>(this.apiUrl, { params });
  }

  /**
   * Récupère uniquement les disjoncteurs actuellement ouverts.
   *
   * @returns Observable de la liste des disjoncteurs ouverts
   */
  getDisjoncteursOuverts(): Observable<{
    statut: string;
    nombre: number;
    disjoncteurs: ReponseDisjoncteur[];
    horodatage: string;
  }> {
    return this.http.get<any>(`${this.apiUrl}/ouverts`);
  }

  /**
   * Récupère le détail d'un disjoncteur spécifique.
   *
   * @param id L'identifiant du disjoncteur
   * @returns Observable du détail du disjoncteur
   */
  getParId(id: number): Observable<ReponseDisjoncteur> {
    return this.http
      .get<{ statut: string; disjoncteur: ReponseDisjoncteur; horodatage: string }>(
        `${this.apiUrl}/${id}`
      )
      .pipe(map((reponse) => reponse.disjoncteur));
  }

  // Réinitialisation

  /**
   * Réinitialise manuellement un disjoncteur (retour à l'état FERMÉ).
   * Réservé aux rôles SUPERVISEUR et ADMIN.
   *
   * @param id L'identifiant du disjoncteur
   * @returns Observable du disjoncteur réinitialisé
   */
  reinitialiser(id: number): Observable<{
    statut: string;
    message: string;
    disjoncteur: ReponseDisjoncteur;
    horodatage: string;
  }> {
    return this.http.put<any>(`${this.apiUrl}/${id}/reinitialiser`, {});
  }

  // Méthodes utilitaires

  /**
   * Compte le nombre de disjoncteurs ouverts.
   * Utile pour les badges dans le menu de navigation.
   *
   * @returns Observable du nombre de disjoncteurs ouverts
   */
  compterOuverts(): Observable<number> {
    return this.getDisjoncteursOuverts().pipe(map((reponse) => reponse.nombre));
  }

  /**
   * Vérifie si un disjoncteur peut être réinitialisé.
   *
   * @param disjoncteur Le disjoncteur à vérifier
   * @returns true si le disjoncteur est OUVERT ou MI_OUVERT
   */
  peutEtreReinitialise(disjoncteur: ReponseDisjoncteur): boolean {
    return disjoncteur.peutEtreReinitialise;
  }

  /**
   * Calcule le pourcentage d'échecs par rapport au seuil.
   * Utile pour les barres de progression dans l'interface.
   *
   * @param disjoncteur Le disjoncteur
   * @returns Le pourcentage (0-100)
   */
  getPourcentageEchecs(disjoncteur: ReponseDisjoncteur): number {
    if (disjoncteur.seuilEchecs === 0) return 0;
    return Math.min(
      Math.round((disjoncteur.nombreEchecs / disjoncteur.seuilEchecs) * 100),
      100
    );
  }

  /**
   * Formate le temps restant avant passage en MI_OUVERT.
   *
   * @param minutes Le nombre de minutes
   * @returns Une chaîne formatée (ex: "45 min", "1h 15min")
   */
  formaterTempsRestant(minutes: number | undefined): string {
    if (minutes === undefined || minutes === null) return '—';
    if (minutes <= 0) return 'Imminent';
    if (minutes < 60) return `${minutes} min`;
    const heures = Math.floor(minutes / 60);
    const minsRestantes = minutes % 60;
    return minsRestantes > 0 ? `${heures}h ${minsRestantes}min` : `${heures}h`;
  }
}

// Interface locale (incluse dans la réponse de lister())

/**
 * Statistiques des disjoncteurs (retournées par l'API).
 */
export interface StatistiquesDisjoncteurs {
  ouverts: number;
  miOuverts: number;
  fermes: number;
  totalEchecs: number;
}