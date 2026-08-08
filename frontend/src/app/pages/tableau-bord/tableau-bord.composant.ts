import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { RouterModule } from '@angular/router';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'bna-tableau-bord',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div style="padding:40px;background:#0a1a10;min-height:100vh;color:#d4edda;font-family:'Inter',sans-serif">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:30px">
        <div style="display:flex;align-items:center;gap:16px"><img src="assets/bna-logo.png" alt="BNA" style="width:120px;height:auto"><h1 style="color:#1a8c4e;margin:0">Tableau de Bord</h1></div>
        <button (click)="deconnexion()" style="padding:8px 20px;background:transparent;border:1px solid rgba(192,57,43,0.3);color:#e74c3c;border-radius:8px;cursor:pointer">Déconnexion</button>
      </div>

      <div style="display:flex;gap:12px;margin-bottom:30px;flex-wrap:wrap">
        <a href="/transactions" style="padding:10px 20px;background:#0f2d1a;border:1px solid rgba(26,140,78,0.2);border-radius:8px;color:#1a8c4e;text-decoration:none;font-weight:500"> Transactions</a>
        <a href="/devises" style="padding:10px 20px;background:#0f2d1a;border:1px solid rgba(26,140,78,0.2);border-radius:8px;color:#f39c12;text-decoration:none;font-weight:500">Devises</a>
        <a href="/testeur-regle" style="padding:10px 20px;background:#0f2d1a;border:1px solid rgba(26,140,78,0.2);border-radius:8px;color:#2ecc71;text-decoration:none;font-weight:500">Testeur SpEL</a>
        <a href="/disjoncteurs" style="padding:10px 20px;background:#0f2d1a;border:1px solid rgba(26,140,78,0.2);border-radius:8px;color:#e74c3c;text-decoration:none;font-weight:500"> Disjoncteurs</a>
      </div>

      <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:30px">
        <div *ngFor="let stat of stats" style="background:#0f2d1a;border:1px solid rgba(26,140,78,0.15);border-radius:12px;padding:20px">
          <div style="font-size:32px;font-weight:700;color:#1a8c4e">{{ stat.valeur }}</div>
          <div style="font-size:13px;color:#6b9e74;margin-top:4px">{{ stat.label }}</div>
        </div>
      </div>

      <div style="display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-bottom:30px">
        <div style="background:#0f2d1a;border:1px solid rgba(26,140,78,0.15);border-radius:12px;padding:24px">
          <h3 style="color:#d4edda;margin-bottom:16px">📊 Répartition des transactions</h3>
          <canvas id="chart-donut" style="max-height:250px"></canvas>
        </div>
        <div style="background:#0f2d1a;border:1px solid rgba(26,140,78,0.15);border-radius:12px;padding:24px">
          <h3 style="color:#d4edda;margin-bottom:16px">📈 Alertes par sévérité</h3>
          <canvas id="chart-bar" style="max-height:250px"></canvas>
        </div>
      </div>

      <div style="background:#0f2d1a;border:1px solid rgba(26,140,78,0.15);border-radius:12px;padding:24px">
        <h3 style="color:#d4edda;margin-bottom:8px"> Connexion réussie</h3>
        <p style="color:#6b9e74">Vous êtes connecté en tant que <strong style="color:#1a8c4e">{{ utilisateur?.nom }}</strong> ({{ utilisateur?.role }})</p>
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
        this.renderCharts(data);
      }
    });
  }

  private renderCharts(data: any): void {
    setTimeout(() => {
      // Donut chart — Transaction Status
      const donutCtx = document.getElementById('chart-donut') as HTMLCanvasElement;
      if (donutCtx && data?.statistiques) {
        new Chart(donutCtx, {
          type: 'doughnut',
          data: {
            labels: ['Acceptées', 'Surveillées', 'Bloquées'],
            datasets: [{
              data: [
                data.statistiques.transactionsTotal - (data.statistiques.transactionsSurveillees || 0) - (data.statistiques.transactionsBloquees || 0),
                data.statistiques.transactionsSurveillees || 0,
                data.statistiques.transactionsBloquees || 0
              ],
              backgroundColor: ['#2ecc71', '#f39c12', '#e74c3c'],
              borderColor: '#0f2d1a',
              borderWidth: 2
            }]
          },
          options: {
            responsive: true,
            plugins: {
              legend: { position: 'bottom', labels: { color: '#8db894', padding: 16 } }
            }
          }
        });
      }

      // Bar chart — Alerts by severity
      const barCtx = document.getElementById('chart-bar') as HTMLCanvasElement;
      if (barCtx && data?.statistiques) {
        new Chart(barCtx, {
          type: 'bar',
          data: {
            labels: ['Faible', 'Moyen', 'Élevé', 'Critique'],
            datasets: [{
              label: 'Alertes',
              data: [
                data.statistiques.alertesFaibles || 0,
                data.statistiques.alertesMoyennes || 0,
                data.statistiques.alertesElevees || 0,
                data.statistiques.alertesCritiques || 0
              ],
              backgroundColor: ['#3498db', '#f1c40f', '#e67e22', '#e74c3c'],
              borderRadius: 4
            }]
          },
          options: {
            responsive: true,
            plugins: {
              legend: { display: false }
            },
            scales: {
              y: { beginAtZero: true, ticks: { color: '#6b9e74' }, grid: { color: 'rgba(26,140,78,0.08)' } },
              x: { ticks: { color: '#6b9e74' }, grid: { display: false } }
            }
          }
        });
      }
    }, 300);
  }

deconnexion(): void {
    localStorage.clear();
    window.location.href = '/connexion';
  }
}
