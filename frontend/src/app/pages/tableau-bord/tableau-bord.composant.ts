import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { RouterModule } from '@angular/router';

@Component({
  selector: 'bna-tableau-bord',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div style="padding:40px;background:#0d1b2a;min-height:100vh;color:#e0e6ed;font-family:'Inter',sans-serif">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:30px">
        <h1 style="color:#4a9eff;margin:0"> BNA-FLUX — Tableau de Bord</h1>
        <button (click)="deconnexion()" style="padding:8px 20px;background:transparent;border:1px solid rgba(231,76,60,0.3);color:#e74c3c;border-radius:8px;cursor:pointer">Déconnexion</button>
      </div>

      <div style="display:flex;gap:12px;margin-bottom:30px;flex-wrap:wrap">
        <a href="/transactions" style="padding:10px 20px;background:#162840;border:1px solid rgba(74,158,255,0.2);border-radius:8px;color:#4a9eff;text-decoration:none;font-weight:500"> Transactions</a>
        <a href="/devises" style="padding:10px 20px;background:#162840;border:1px solid rgba(74,158,255,0.2);border-radius:8px;color:#f39c12;text-decoration:none;font-weight:500">Devises</a>
        <a href="/testeur-regle" style="padding:10px 20px;background:#162840;border:1px solid rgba(74,158,255,0.2);border-radius:8px;color:#2ecc71;text-decoration:none;font-weight:500">Testeur SpEL</a>
        <a href="/disjoncteurs" style="padding:10px 20px;background:#162840;border:1px solid rgba(74,158,255,0.2);border-radius:8px;color:#e74c3c;text-decoration:none;font-weight:500"> Disjoncteurs</a>
      </div>

      <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:30px">
        <div *ngFor="let stat of stats" style="background:#162840;border:1px solid rgba(74,158,255,0.15);border-radius:12px;padding:20px">
          <div style="font-size:32px;font-weight:700;color:#4a9eff">{{ stat.valeur }}</div>
          <div style="font-size:13px;color:#5a6d80;margin-top:4px">{{ stat.label }}</div>
        </div>
      </div>

      <div style="background:#162840;border:1px solid rgba(74,158,255,0.15);border-radius:12px;padding:24px">
        <h3 style="color:#e0e6ed;margin-bottom:8px"> Connexion réussie</h3>
        <p style="color:#5a6d80">Vous êtes connecté en tant que <strong style="color:#4a9eff">{{ utilisateur?.nom }}</strong> ({{ utilisateur?.role }})</p>
      </div>
    </div>
  `
})
export class TableauBordComposant implements OnInit {
  private readonly http = inject(HttpClient);
  
  utilisateur: any = null;
  
  stats = [
    { label: 'Transactions aujourd\'hui', valeur: 0 },
    { label: 'En surveillance', valeur: 0 },
    { label: 'Bloquées', valeur: 0 },
    { label: 'Score moyen', valeur: '0.0' },
  ];


  ngOnInit(): void {
    const user = localStorage.getItem('bna_utilisateur');
    if (user) this.utilisateur = JSON.parse(user);
    
    this.http.get('/api/tableau-bord/statistiques', ).subscribe({
      next: (data: any) => {
        if (data?.statistiques) {
          this.stats[0].valeur = data.statistiques.transactionsTotal || 0;
          this.stats[1].valeur = data.statistiques.transactionsSurveillees || 0;
          this.stats[2].valeur = data.statistiques.transactionsBloquees || 0;
          this.stats[3].valeur = data.statistiques.scoreRisqueMoyen || '0.0';
        }
      }
    });
  }

  deconnexion(): void {
    localStorage.clear();
    window.location.href = '/connexion';
  }
}
