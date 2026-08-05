// Fichier: pages/transactions/detail-transaction/detail-transaction.composant.ts

import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTabsModule } from '@angular/material/tabs';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subscription, switchMap } from 'rxjs';
import { TransactionService, EntreeAudit, ReponseVerificationAudit, EntreeAuditVerifiee } from '../../../core/services/transaction.service';
import { ReponseTransaction, LABELS_STATUTS, COULEURS_STATUTS, ICONES_STATUTS, LABELS_TYPES_TRANSACTION, ICONES_TYPES_TRANSACTION, LABELS_CANAUX, ICONES_CANAUX } from '@modeles/transaction.modele';
import { ReponseAlerte, LABELS_NIVEAUX_ALERTE, COULEURS_NIVEAUX_ALERTE, ICONES_NIVEAUX_ALERTE } from '@modeles/alerte.modele';

@Component({
  selector: 'bna-detail-transaction',
  standalone: true,
  imports: [CommonModule, DecimalPipe, DatePipe, RouterModule, MatCardModule, MatButtonModule, MatIconModule, MatChipsModule, MatTabsModule, MatDividerModule, MatTooltipModule, MatProgressSpinnerModule, MatSnackBarModule],
  templateUrl: './detail-transaction.composant.html',
  styleUrls: ['./detail-transaction.composant.scss'],
})
export class DetailTransactionComposant implements OnInit, OnDestroy {
  private readonly transactionService = inject(TransactionService);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);

  readonly transaction = signal<ReponseTransaction | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly ongletActif = signal(0);

  // Piste d'audit
  readonly pisteAudit = signal<EntreeAudit[]>([]);
  readonly chargementAudit = signal(false);
  readonly verification = signal<ReponseVerificationAudit | null>(null);
  readonly verificationEnCours = signal(false);

  // Alertes
  readonly alertes = signal<ReponseAlerte[]>([]);
  readonly chargementAlertes = signal(false);

  readonly LABELS_STATUTS = LABELS_STATUTS;
  readonly COULEURS_STATUTS = COULEURS_STATUTS;
  readonly ICONES_STATUTS = ICONES_STATUTS;
  readonly LABELS_TYPES_TRANSACTION = LABELS_TYPES_TRANSACTION;
  readonly ICONES_TYPES_TRANSACTION = ICONES_TYPES_TRANSACTION;
  readonly LABELS_CANAUX = LABELS_CANAUX;
  readonly ICONES_CANAUX = ICONES_CANAUX;
  readonly LABELS_NIVEAUX_ALERTE = LABELS_NIVEAUX_ALERTE;
  readonly COULEURS_NIVEAUX_ALERTE = COULEURS_NIVEAUX_ALERTE;
  readonly ICONES_NIVEAUX_ALERTE = ICONES_NIVEAUX_ALERTE;

  private subscription = new Subscription();

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) this.chargerTransaction(id);
  }

  ngOnDestroy(): void { this.subscription.unsubscribe(); }

  private chargerTransaction(id: number): void {
    this.chargement.set(true);
    this.subscription.add(
      this.transactionService.getParId(id).subscribe({
        next: (t) => { this.transaction.set(t); this.chargement.set(false); },
        error: () => { this.erreur.set('Transaction introuvable'); this.chargement.set(false); },
      })
    );
  }

  chargerPisteAudit(): void {
    if (!this.transaction()) return;
    this.chargementAudit.set(true);
    this.subscription.add(
      this.transactionService.getPisteAudit(this.transaction()!.id).subscribe({
        next: (r) => { this.pisteAudit.set(r.entrees || []); this.chargementAudit.set(false); },
        error: () => this.chargementAudit.set(false),
      })
    );
  }

  verifierIntegrite(): void {
    if (!this.transaction()) return;
    this.verificationEnCours.set(true);
    this.subscription.add(
      this.transactionService.verifierPisteAudit(this.transaction()!.id).subscribe({
        next: (r) => {
          this.verification.set(r);
          this.verificationEnCours.set(false);
          const msg = r.chaineIntacte ? 'Chaîne d\'audit intacte ✅' : 'Chaîne corrompue détectée !';
          this.snackBar.open(msg, 'Fermer', { duration: 4000, panelClass: r.chaineIntacte ? ['snackbar-success'] : ['snackbar-error'] });
        },
        error: () => this.verificationEnCours.set(false),
      })
    );
  }

  chargerAlertes(): void {
    if (!this.transaction()) return;
    this.chargementAlertes.set(true);
    this.subscription.add(
      this.transactionService.getAlertes(this.transaction()!.id).subscribe({
        next: (r: any) => { this.alertes.set(r.alertes || r.donnees || []); this.chargementAlertes.set(false); },
        error: () => this.chargementAlertes.set(false),
      })
    );
  }

  onChangementOnglet(index: number): void {
    this.ongletActif.set(index);
    if (index === 1 && !this.pisteAudit().length && !this.chargementAudit()) this.chargerPisteAudit();
    if (index === 2 && !this.alertes().length && !this.chargementAlertes()) this.chargerAlertes();
  }

  getCouleurScore(score: number): string { if (score >= 70) return '#e74c3c'; if (score >= 30) return '#f39c12'; return '#2ecc71'; }
  getScorePourcentage(score: number): number { return Math.min(score, 100); }
  getDelaiFormatte(date?: string): string { if (!date) return '—'; const diff = Date.now() - new Date(date).getTime(); const min = Math.floor(diff / 60000); if (min < 1) return 'À l\'instant'; if (min < 60) return `il y a ${min} min`; const h = Math.floor(min / 60); return `il y a ${h}h${min % 60}min`; }
  getEtapeIcone(etape: string): string { const icones: Record<string, string> = { VALIDATION: 'fa-check-circle', ENRICHISSEMENT: 'fa-search-plus', EVALUATION_REGLES: 'fa-shield-halved', NOTATION: 'fa-gauge-high', PERSISTANCE: 'fa-database' }; return icones[etape] || 'fa-circle'; }
  getEtapeLibelle(etape: string): string { const libelles: Record<string, string> = { VALIDATION: 'Validation', ENRICHISSEMENT: 'Enrichissement', EVALUATION_REGLES: 'Évaluation règles', NOTATION: 'Notation', PERSISTANCE: 'Persistance' }; return libelles[etape] || etape; }

  // RIB formatté
  formaterRib(rib: string): string {
    if (!rib || rib.length !== 20) return rib || '—';
    return rib.replace(/(.{4})/g, '$1 ').trim();
  }

  // Extraction code banque
  getCodeBanque(rib: string): string { return rib?.substring(0, 2) || '—'; }

  // Extraction code agence
  getCodeAgence(rib: string): string { return rib?.substring(2, 5) || '—'; }
}