import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  RequeteTransaction,
  ReponseTransaction,
  ResumeTransaction,
  FiltresTransaction,
  PageReponse,
} from '@modeles/transaction.modele';
import { ReponseVerificationAudit } from '../modeles/audit.modele';

/**
 * Service de gestion des transactions.
 *
 * Responsabilités :
 * - Soumettre une transaction au pipeline
 * - Consulter les transactions avec filtrage et pagination
 * - Consulter le détail, la piste d'audit et les alertes d'une transaction
 * - Vérifier l'intégrité de la piste d'audit
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@Injectable({
  providedIn: 'root',
})
export class TransactionService {
  private readonly http = inject(HttpClient);

  /** URL de base de l'API des transactions */
  private readonly apiUrl = '/api/transactions';

  // Soumission

  /**
   * Soumet une nouvelle transaction au pipeline de surveillance.
   *
   * @param requete Les données de la transaction
   * @returns Observable de la réponse avec le résultat du pipeline
   */
  soumettre(requete: RequeteTransaction): Observable<ReponseTransaction> {
    return this.http
      .post<{ statut: string; donnees: ReponseTransaction; horodatage: string }>(
        this.apiUrl,
        requete
      )
      .pipe(map((reponse) => reponse.donnees));
  }

  // Consultation — Liste

  /**
   * Recherche les transactions avec filtrage et pagination.
   *
   * @param filtres Les critères de filtrage
   * @returns Observable de la page de transactions
   */
  rechercher(filtres: FiltresTransaction): Observable<PageReponse<ResumeTransaction>> {
    let params = new HttpParams()
      .set('page', filtres.page.toString())
      .set('taille', filtres.taille.toString())
      .set('tri', filtres.tri);

    if (filtres.statut) {
      params = params.set('statut', filtres.statut);
    }
    if (filtres.codeDevise) {
      params = params.set('codeDevise', filtres.codeDevise);
    }
    if (filtres.canal) {
      params = params.set('canal', filtres.canal);
    }
    if (filtres.typeTransaction) {
      params = params.set('typeTransaction', filtres.typeTransaction);
    }
    if (filtres.minMontant !== undefined && filtres.minMontant !== null) {
      params = params.set('minMontant', filtres.minMontant.toString());
    }
    if (filtres.maxMontant !== undefined && filtres.maxMontant !== null) {
      params = params.set('maxMontant', filtres.maxMontant.toString());
    }
    if (filtres.dateDebut) {
      params = params.set('dateDebut', filtres.dateDebut);
    }
    if (filtres.dateFin) {
      params = params.set('dateFin', filtres.dateFin);
    }

    return this.http.get<PageReponse<ResumeTransaction>>(this.apiUrl, { params });
  }

  /**
   * Récupère toutes les transactions avec pagination simple.
   *
   * @param page Numéro de page
   * @param taille Taille de la page
   * @param tri Champ de tri
   * @returns Observable de la page de transactions
   */
  lister(
    page: number = 0,
    taille: number = 20,
    tri: string = 'dateTransaction,desc'
  ): Observable<PageReponse<ResumeTransaction>> {
    return this.rechercher({ page, taille, tri });
  }

  // Consultation — Détail

  /**
   * Récupère le détail complet d'une transaction.
   *
   * @param id L'identifiant de la transaction
   * @returns Observable du détail de la transaction
   */
  getParId(id: number): Observable<ReponseTransaction> {
    return this.http
      .get<{ statut: string; donnees: ReponseTransaction; horodatage: string }>(
        `${this.apiUrl}/${id}`
      )
      .pipe(map((reponse) => reponse.donnees));
  }

  // Piste d'audit

  /**
   * Récupère la piste d'audit d'une transaction.
   *
   * @param id L'identifiant de la transaction
   * @returns Observable de la piste d'audit
   */
  getPisteAudit(id: number): Observable<{
    statut: string;
    transactionId: number;
    nombreEntrees: number;
    entrees: EntreeAudit[];
    horodatage: string;
  }> {
    return this.http.get<any>(`${this.apiUrl}/${id}/piste-audit`);
  }

  /**
   * Vérifie l'intégrité de la piste d'audit.
   *
   * @param id L'identifiant de la transaction
   * @returns Observable du résultat de vérification
   */
  verifierPisteAudit(id: number): Observable<ReponseVerificationAudit> {
    return this.http
      .get<{ statut: string; donnees: ReponseVerificationAudit; horodatage: string }>(
        `${this.apiUrl}/${id}/piste-audit/verifier`
      )
      .pipe(map((reponse) => reponse.donnees));
  }

  // Alertes liées

  /**
   * Récupère les alertes liées à une transaction.
   *
   * @param id L'identifiant de la transaction
   * @returns Observable des alertes
   */
  getAlertes(id: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/${id}/alertes`);
  }
}

// Modèles locaux

/**
 * Entrée de la piste d'audit.
 */
export interface EntreeAudit {
  id: number;
  transactionId?: number;
  etape: string;
  action: string;
  detail?: string;
  hashPrecedent?: string;
  hashCourant: string;
  horodatage: string;
  operateur: string;
}

// Note: ReponseVerificationAudit est importé depuis le fichier audit.modele.ts
// Si ce fichier n'existe pas encore, le type est défini ici en fallback.
export interface ReponseVerificationAudit {
  transactionId: number;
  referenceTransaction?: string;
  chaineIntacte: boolean;
  nombreEntrees: number;
  premiereEntree?: string;
  derniereEntree?: string;
  entreeCorrompue?: number;
  messageErreur?: string;
  dureeVerificationMs: number;
  entrees?: EntreeAuditVerifiee[];
}

export interface EntreeAuditVerifiee {
  id: number;
  etape: string;
  action: string;
  operateur: string;
  horodatage: string;
  hashStocke: string;
  hashCalcule: string;
  hashVerifie: boolean;
  hashPrecedent?: string;
}