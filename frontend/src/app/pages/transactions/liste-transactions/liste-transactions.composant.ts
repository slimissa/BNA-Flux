import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Router } from "@angular/router";
import { RouterModule } from '@angular/router';

@Component({
  selector: 'bna-liste-transactions',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div style="padding:40px;background:#0d1b2a;min-height:100vh;color:#e0e6ed;font-family:'Inter',sans-serif">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:30px">
        <h1 style="color:#4a9eff;margin:0">📊 Transactions</h1>
        <div style="display:flex;gap:12px">
          <a href="/tableau-bord" style="color:#4a9eff;text-decoration:none;padding:8px 16px;border:1px solid rgba(74,158,255,0.3);border-radius:8px">← Tableau de bord</a>
          <button (click)="deconnexion()" style="padding:8px 16px;background:transparent;border:1px solid rgba(231,76,60,0.3);color:#e74c3c;border-radius:8px;cursor:pointer">Déconnexion</button>
        </div>
      </div>

      <div *ngIf="chargement()" style="text-align:center;padding:40px;color:#5a6d80">Chargement...</div>
      <div *ngIf="erreur()" style="color:#e74c3c;padding:16px;background:rgba(231,76,60,0.1);border-radius:8px;margin-bottom:16px">{{ erreur() }}</div>

      <div *ngIf="!chargement() && transactions().length" style="display:flex;flex-direction:column;gap:8px">
        <div *ngFor="let t of transactions()" [routerLink]="['/transactions', t.id]" style="cursor:pointer" 
             style="background:#162840;border:1px solid rgba(74,158,255,0.15);border-radius:10px;padding:16px 20px;display:flex;justify-content:space-between;align-items:center">
          <div>
            <div style="font-family:monospace;color:#4a9eff;font-size:13px">{{ t.referenceTransaction }}</div>
            <div style="font-size:12px;color:#5a6d80;margin-top:4px">{{ t.dateTransaction | date:'dd/MM/yyyy HH:mm' }} · {{ t.typeTransaction }} · {{ t.canal }}</div>
          </div>
          <div style="text-align:right">
            <div style="font-family:monospace;font-weight:600">{{ t.montant | number:'1.3-3' }} {{ t.codeDevise }}</div>
            <span [style.color]="getCouleurStatut(t.statutTransaction)" style="font-size:11px;font-weight:600">{{ t.statutTransaction }}</span>
          </div>
        </div>
      </div>
      <div *ngIf="!chargement() && !transactions().length" style="text-align:center;padding:40px;color:#5a6d80">Aucune transaction trouvée</div>
    </div>
  `
})
export class ListeTransactionsComposant implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  readonly transactions = signal<any[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);


  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.http.get('/api/transactions?page=0&taille=20&tri=dateTransaction,desc', ).subscribe({
      next: (data: any) => {
        this.transactions.set(data.donnees || []);
        this.chargement.set(false);
      },
      error: (err) => {
        this.erreur.set('Erreur: ' + (err.status || '') + ' ' + (err.error?.message || err.message));
        this.chargement.set(false);
      }
    });
  }

  getCouleurStatut(statut: string): string {
    if (statut === 'ACCEPTE') return '#2ecc71';
    if (statut === 'SURVEILLE') return '#f39c12';
    if (statut === 'BLOQUE') return '#e74c3c';
    return '#5a6d80';
  }

  deconnexion(): void {
    localStorage.clear();
    window.location.href = '/connexion';
  }
}
