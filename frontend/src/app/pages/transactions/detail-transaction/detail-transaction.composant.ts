import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterModule } from '@angular/router';

@Component({
  selector: 'bna-detail-transaction',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div style="padding:40px;background:#0d1b2a;min-height:100vh;color:#e0e6ed;font-family:'Inter',sans-serif">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:30px">
        <h1 style="color:#4a9eff;margin:0"> Détail Transaction</h1>
        <a href="/transactions" style="color:#4a9eff;text-decoration:none;padding:8px 16px;border:1px solid rgba(74,158,255,0.3);border-radius:8px">← Retour</a>
      </div>

      <div *ngIf="chargement()" style="text-align:center;padding:40px;color:#5a6d80">Chargement...</div>
      <div *ngIf="erreur()" style="color:#e74c3c">{{ erreur() }}</div>

      <div *ngIf="t() as tx" style="display:flex;flex-direction:column;gap:16px">
        <!-- Score -->
        <div style="background:#162840;border:1px solid rgba(74,158,255,0.15);border-radius:12px;padding:20px">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
            <span style="color:#5a6d80">Score de risque</span>
            <span [style.color]="getCouleurScore(tx.scoreRisque)" style="font-size:28px;font-weight:700">{{ tx.scoreRisque }}/100</span>
          </div>
          <div style="height:8px;background:#0d1b2a;border-radius:4px;overflow:hidden">
            <div [style.width.%]="tx.scoreRisque" [style.background]="getCouleurScore(tx.scoreRisque)" style="height:100%;border-radius:4px"></div>
          </div>
        </div>

        <!-- Infos -->
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
          <div style="background:#162840;border:1px solid rgba(74,158,255,0.15);border-radius:12px;padding:20px">
            <h3 style="color:#e0e6ed;margin-bottom:12px">Informations</h3>
            <div *ngFor="let info of infos" style="display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid rgba(74,158,255,0.06)">
              <span style="color:#5a6d80;font-size:12px">{{ info.label }}</span>
              <span style="color:#e0e6ed;font-size:13px">{{ info.valeur }}</span>
            </div>
          </div>
          <div style="background:#162840;border:1px solid rgba(74,158,255,0.15);border-radius:12px;padding:20px">
            <h3 style="color:#e0e6ed;margin-bottom:12px">Comptes</h3>
            <p style="color:#5a6d80;font-size:12px">Source: <span style="color:#4a9eff;font-family:monospace">{{ tx.ribSource }}</span></p>
            <p style="color:#5a6d80;font-size:12px">Dest: <span style="color:#4a9eff;font-family:monospace">{{ tx.ribDestination }}</span></p>
            <p style="color:#5a6d80;font-size:12px">Montant: <strong style="color:#e0e6ed">{{ tx.montant | number:'1.2-2' }} {{ tx.codeDevise }}</strong></p>
            <p style="color:#5a6d80;font-size:12px">Motif: {{ tx.motif || 'Aucun' }}</p>
          </div>
        </div>

        <!-- Alertes -->
        <div style="background:#162840;border:1px solid rgba(74,158,255,0.15);border-radius:12px;padding:20px">
          <h3 style="color:#e0e6ed;margin-bottom:12px"> Alertes ({{ tx.nombreAlertes }})</h3>
          <div *ngFor="let a of tx.alertes" style="background:#0d1b2a;border-left:3px solid #f39c12;border-radius:0 8px 8px 0;padding:12px;margin-bottom:8px">
            <div style="display:flex;justify-content:space-between">
              <span style="font-weight:600;color:#f39c12">{{ a.nomRegle }}</span>
              <span style="color:#5a6d80;font-size:11px">{{ a.niveau }}</span>
            </div>
            <p style="color:#8899aa;font-size:12px;margin:6px 0 0">{{ a.message }}</p>
          </div>
        </div>

        <!-- Piste d'audit -->
        <div style="background:#162840;border:1px solid rgba(74,158,255,0.15);border-radius:12px;padding:20px">
          <h3 style="color:#e0e6ed;margin-bottom:8px"> Piste d'audit</h3>
          <button (click)="chargerAudit()" style="padding:8px 16px;background:#4a9eff;border:none;border-radius:6px;color:white;cursor:pointer;font-family:inherit">Vérifier l'intégrité</button>
          <div *ngIf="auditResult()" style="margin-top:12px;padding:12px;border-radius:8px" [style.background]="auditResult()?.chaineIntacte ? 'rgba(46,204,113,0.1)' : 'rgba(231,76,60,0.1)'">
            <span [style.color]="auditResult()?.chaineIntacte ? '#2ecc71' : '#e74c3c'">
              {{ auditResult()?.chaineIntacte ? ' Chaîne intacte' : '❌ Chaîne corrompue' }}
            </span>
            <span style="color:#5a6d80;margin-left:8px">({{ auditResult()?.nombreEntrees }} entrées, {{ auditResult()?.dureeVerificationMs }}ms)</span>
          </div>
        </div>
      </div>
    </div>
  `
})
export class DetailTransactionComposant implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);

  readonly t = signal<any>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly auditResult = signal<any>(null);

  infos: {label: string, valeur: string}[] = [];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.http.get('/api/transactions/' + id).subscribe({
        next: (data: any) => {
          const tx = data.donnees || data;
          this.t.set(tx);
          this.infos = [
            { label: 'Référence', valeur: tx.referenceTransaction },
            { label: 'Statut', valeur: tx.statutTransaction },
            { label: 'Date', valeur: new Date(tx.dateTransaction).toLocaleString('fr-FR') },
            { label: 'Type', valeur: tx.typeTransaction },
            { label: 'Canal', valeur: tx.canal },
            { label: 'Devise', valeur: tx.nomDevise || tx.codeDevise },
            { label: 'Pays origine', valeur: tx.paysOrigine || 'N/A' },
          ];
          this.chargement.set(false);
        },
        error: (err) => {
          this.erreur.set('Erreur: ' + (err.error?.message || err.message));
          this.chargement.set(false);
        }
      });
    }
  }

  chargerAudit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.http.get('/api/transactions/' + id + '/piste-audit/verifier').subscribe({
        next: (data: any) => this.auditResult.set(data.donnees || data),
        error: (err) => console.error(err)
      });
    }
  }

  getCouleurScore(score: number): string {
    if (score >= 70) return '#e74c3c';
    if (score >= 30) return '#f39c12';
    return '#2ecc71';
  }
}
