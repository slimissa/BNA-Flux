import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'bna-devises',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="padding:40px;background:#0a1a10;min-height:100vh;color:#d4edda;font-family:'Inter',sans-serif">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:30px">
        <h1 style="color:#1a8c4e;margin:0"> Devises</h1>
        <div style="display:flex;gap:12px">
          <a href="/tableau-bord" style="color:#1a8c4e;text-decoration:none;padding:8px 16px;border:1px solid rgba(26,140,78,0.3);border-radius:8px">← Tableau de bord</a>
          <button (click)="deconnexion()" style="padding:8px 16px;background:transparent;border:1px solid rgba(192,57,43,0.3);color:#e74c3c;border-radius:8px;cursor:pointer">Déconnexion</button>
        </div>
      </div>

      <div *ngIf="chargement()" style="text-align:center;padding:40px;color:#6b9e74">Chargement...</div>

      <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:12px">
        <div *ngFor="let d of devises()" style="background:#0f2d1a;border:1px solid rgba(26,140,78,0.15);border-radius:10px;padding:16px;text-align:center">
          <div style="font-size:28px;margin-bottom:4px">{{ d.symbole }}</div>
          <div style="font-size:18px;font-weight:700;color:#1a8c4e">{{ d.code }}</div>
          <div style="font-size:12px;color:#6b9e74;margin-top:4px">{{ d.nom }}</div>
          <div style="font-size:11px;color:{{ d.unitesMineures === 3 ? '#f39c12' : d.unitesMineures === 0 ? '#3498db' : '#2ecc71' }};margin-top:6px">{{ d.unitesMineures }} décimales</div>
        </div>
      </div>
    </div>
  `
})
export class DevisesComposant implements OnInit {
  private readonly http = inject(HttpClient);
  readonly devises = signal<any[]>([]);
  readonly chargement = signal(true);

  ngOnInit(): void {
    this.http.get('/api/devises').subscribe({
      next: (data: any) => { this.devises.set(data.devises || []); this.chargement.set(false); },
      error: () => this.chargement.set(false)
    });
  }

  deconnexion(): void { localStorage.clear(); window.location.href = '/connexion'; }
}
