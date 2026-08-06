import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Component({
  selector: 'bna-disjoncteurs',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="padding:40px;background:#0d1b2a;min-height:100vh;color:#e0e6ed;font-family:'Inter',sans-serif">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:30px">
        <h1 style="color:#4a9eff;margin:0"> Disjoncteurs</h1>
        <div style="display:flex;gap:12px">
          <a href="/tableau-bord" style="color:#4a9eff;text-decoration:none;padding:8px 16px;border:1px solid rgba(74,158,255,0.3);border-radius:8px">← Tableau de bord</a>
          <button (click)="deconnexion()" style="padding:8px 16px;background:transparent;border:1px solid rgba(231,76,60,0.3);color:#e74c3c;border-radius:8px;cursor:pointer">Déconnexion</button>
        </div>
      </div>

      <div *ngIf="chargement()" style="text-align:center;padding:40px;color:#5a6d80">Chargement...</div>

      <div *ngIf="stats" style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:20px">
        <div style="background:#162840;border-radius:10px;padding:16px;text-align:center;border-left:3px solid #e74c3c">
          <div style="font-size:24px;font-weight:700;color:#e74c3c">{{ stats.ouverts }}</div>
          <div style="font-size:11px;color:#5a6d80">Ouverts</div>
        </div>
        <div style="background:#162840;border-radius:10px;padding:16px;text-align:center;border-left:3px solid #f39c12">
          <div style="font-size:24px;font-weight:700;color:#f39c12">{{ stats.miOuverts }}</div>
          <div style="font-size:11px;color:#5a6d80">Mi-ouverts</div>
        </div>
        <div style="background:#162840;border-radius:10px;padding:16px;text-align:center;border-left:3px solid #2ecc71">
          <div style="font-size:24px;font-weight:700;color:#2ecc71">{{ stats.fermes }}</div>
          <div style="font-size:11px;color:#5a6d80">Fermés</div>
        </div>
        <div style="background:#162840;border-radius:10px;padding:16px;text-align:center;border-left:3px solid #9b59b6">
          <div style="font-size:24px;font-weight:700;color:#9b59b6">{{ stats.totalEchecs }}</div>
          <div style="font-size:11px;color:#5a6d80">Total échecs</div>
        </div>
      </div>

      <div *ngFor="let d of disjoncteurs()" style="background:#162840;border:1px solid rgba(74,158,255,0.15);border-radius:10px;padding:16px;margin-bottom:8px;display:flex;justify-content:space-between;align-items:center">
        <div>
          <div style="font-weight:600">{{ d.nom || d.typeCible + ' ' + d.identifiantCible }}</div>
          <div style="font-size:12px;color:#5a6d80">{{ d.typeCible }} · {{ d.identifiantCible }}</div>
          <div style="font-size:12px;color:#5a6d80">Échecs: {{ d.nombreEchecs }}/{{ d.seuilEchecs }}</div>
        </div>
        <div>
          <span [style.background]="d.etat === 'OUVERT' ? 'rgba(231,76,60,0.15)' : d.etat === 'MI_OUVERT' ? 'rgba(243,156,18,0.15)' : 'rgba(46,204,113,0.15)'" 
                [style.color]="d.etat === 'OUVERT' ? '#e74c3c' : d.etat === 'MI_OUVERT' ? '#f39c12' : '#2ecc71'"
                style="padding:4px 12px;border-radius:20px;font-size:11px;font-weight:600">
            {{ d.etat }}
          </span>
        </div>
      </div>
      <div *ngIf="!chargement() && !disjoncteurs().length" style="text-align:center;padding:40px;color:#5a6d80">Aucun disjoncteur</div>
    </div>
  `
})
export class DisjoncteursComposant implements OnInit {
  private readonly http = inject(HttpClient);
  readonly disjoncteurs = signal<any[]>([]);
  readonly chargement = signal(true);
  stats: any = null;

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('bna_token_acces');
    return new HttpHeaders({ 'Authorization': `Bearer ${token}` });
  }

  ngOnInit(): void {
    this.http.get('/api/disjoncteurs', { headers: this.getHeaders() }).subscribe({
      next: (data: any) => {
        this.disjoncteurs.set(data.disjoncteurs || []);
        this.stats = data.statistiques;
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false)
    });
  }

  deconnexion(): void { localStorage.clear(); window.location.href = '/connexion'; }
}
