import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'bna-testeur-regle',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: '<div style="padding:40px;background:#0a1a10;min-height:100vh;color:#d4edda;font-family:Inter,sans-serif"><h1 style="color:#1a8c4e;margin-bottom:8px">Testeur d Expression SpEL</h1><p style="color:#6b9e74;margin-bottom:24px">Validez vos expressions avant de les utiliser dans les regles</p><div style="display:flex;gap:12px;margin-bottom:24px"><a href="/tableau-bord" style="padding:8px 16px;background:#0f2d1a;color:#1a8c4e;text-decoration:none;border-radius:8px">Retour</a></div><div style="background:#0f2d1a;border-radius:12px;padding:24px;margin-bottom:20px;border:1px solid rgba(26,140,78,0.15)"><h3 style="margin-bottom:16px;color:#d4edda">Expression a tester</h3><textarea [(ngModel)]="expression" rows="3" style="width:100%;background:#0a1a10;color:#d4edda;border:1px solid rgba(26,140,78,0.3);border-radius:8px;padding:12px;font-family:monospace;font-size:14px" placeholder="Ex: montant >= 50000 AND codeDevise != TND"></textarea><div style="margin-top:16px"><button (click)="tester()" [disabled]="chargement() || !expression().trim()" style="padding:12px 24px;background:#1a8c4e;color:#fff;border:none;border-radius:8px;cursor:pointer;font-weight:600">{{ chargement() ? "Test..." : "Tester" }}</button></div></div><div *ngIf="resultat()" [style.background]="resultat().syntaxeValide ? \'rgba(46,204,113,0.1)\' : \'rgba(231,76,60,0.1)\'" style="border-radius:12px;padding:20px;border:1px solid;margin-top:16px" [style.borderColor]="resultat().syntaxeValide ? \'#2ecc71\' : \'#e74c3c\'"><span style="font-size:24px">{{ resultat().syntaxeValide ? "OK" : "ERREUR" }}</span><strong [style.color]="resultat().syntaxeValide ? \'#2ecc71\' : \'#e74c3c\'" style="margin-left:12px">{{ resultat().syntaxeValide ? "Syntaxe valide!" : "Erreur" }}</strong><p style="color:#8db894;margin-top:4px">{{ resultat().message || resultat().erreur }}</p></div></div>'
})
export class TesteurRegleComposant {
  private http = inject(HttpClient);
  expression = signal('');
  resultat = signal<any>(null);
  chargement = signal(false);

  tester() {
    this.chargement.set(true);
    this.resultat.set(null);
    this.http.post('/api/regles/tester', { expression: this.expression() }).subscribe({
      next: (r: any) => { this.resultat.set(r); this.chargement.set(false); },
      error: (e: any) => { this.resultat.set({ syntaxeValide: false, erreur: e.error?.message || 'Erreur' }); this.chargement.set(false); }
    });
  }
}
