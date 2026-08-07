import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpClientModule } from '@angular/common/http';

@Component({
  selector: 'bna-connexion',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
<div class="login-page">
  <div class="login-bg">
    <div class="orb orb-1"></div>
    <div class="orb orb-2"></div>
    <div class="orb orb-3"></div>
  </div>
  <div class="login-card">
    <div class="login-logo">
      <img src="assets/bna-logo.png" alt="BNA" style="width:180px;height:auto" />
    </div>
    <h1>BNA-FLUX</h1>
    <p class="subtitle">Surveillance des Transactions Bancaires</p>

    <div class="error-msg" *ngIf="erreur()">{{ erreur() }}</div>
    <div class="success-msg" *ngIf="succes()">{{ succes() }}</div>

    <div [formGroup]="formulaire">
      <div class="field">
        <label>Email</label>
        <input type="email" formControlName="email" placeholder="votre.email@bna.com.tn"/>
      </div>
      <div class="field">
        <label>Mot de passe</label>
        <input type="password" formControlName="motDePasse" placeholder="••••••••"/>
      </div>
      <button type="button" class="btn-login" (click)="soumettre()" [disabled]="chargement()">
        {{ chargement() ? 'Connexion...' : 'Se connecter' }}
      </button>
    </div>

    <div class="divider"><span>comptes de test</span></div>
    <div class="test-btns">
      <button (click)="remplirTest('admin')">Admin</button>
      <button (click)="remplirTest('superviseur')">Superviseur</button>
      <button (click)="remplirTest('operateur')">Opérateur</button>
    </div>

    <p class="footer">Banque Nationale Agricole &copy; 2026</p>
  </div>
</div>
`,
  styles: [`
.login-page { min-height:100vh; display:flex; align-items:center; justify-content:center; background:#08140c; font-family:'Inter',sans-serif; position:relative; overflow:hidden; }
.login-bg { position:absolute; inset:0; }
.orb { position:absolute; border-radius:50%; filter:blur(60px); opacity:0.1; }
.orb-1 { width:400px; height:400px; background:#1a8c4e; top:-10%; right:-5%; }
.orb-2 { width:300px; height:300px; background:#2ecc71; bottom:-15%; left:-5%; }
.orb-3 { width:250px; height:250px; background:#15703d; top:50%; left:40%; }
.login-card { position:relative; z-index:1; width:380px; max-width:92vw; padding:36px 28px 24px; background:#0d2418; border:1px solid rgba(26,140,78,0.2); border-radius:16px; box-shadow:0 8px 32px rgba(0,0,0,0.5); }
.login-logo { display:flex; justify-content:center; margin-bottom:12px; }
.login-logo svg { filter:drop-shadow(0 0 8px rgba(26,140,78,0.3)); }
h1 { text-align:center; font-size:24px; font-weight:700; color:#1a8c4e; letter-spacing:2px; margin-bottom:2px; }
.subtitle { text-align:center; font-size:12px; color:#6b9e74; margin-bottom:20px; }
.error-msg { background:rgba(192,57,43,0.15); border:1px solid rgba(192,57,43,0.3); color:#e74c3c; padding:10px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; }
.success-msg { background:rgba(26,140,78,0.15); border:1px solid rgba(26,140,78,0.3); color:#2ecc71; padding:10px 14px; border-radius:8px; font-size:13px; margin-bottom:16px; }
.field { margin-bottom:14px; }
.field label { display:block; font-size:11px; font-weight:600; color:#6b9e74; text-transform:uppercase; letter-spacing:0.5px; margin-bottom:6px; }
.field input { width:100%; padding:12px 14px; background:#08140c; border:1px solid rgba(26,140,78,0.2); border-radius:8px; color:#d4edda; font-size:14px; font-family:inherit; outline:none; }
.field input:focus { border-color:#1a8c4e; }
.btn-login { width:100%; padding:13px; margin-top:6px; background:linear-gradient(135deg,#1a8c4e,#15703d); border:none; border-radius:8px; color:white; font-size:15px; font-weight:600; cursor:pointer; font-family:inherit; }
.btn-login:disabled { opacity:0.5; }
.divider { display:flex; align-items:center; gap:10px; margin:20px 0 14px; color:#4a6e52; font-size:10px; text-transform:uppercase; }
.divider::before,.divider::after { content:''; flex:1; height:1px; background:rgba(26,140,78,0.1); }
.test-btns { display:flex; gap:8px; justify-content:center; }
.test-btns button { padding:6px 14px; background:transparent; border:1px solid rgba(26,140,78,0.2); border-radius:20px; color:#6b9e74; font-size:11px; cursor:pointer; font-family:inherit; }
.test-btns button:hover { border-color:#1a8c4e; color:#1a8c4e; }
.footer { text-align:center; margin-top:18px; padding-top:14px; border-top:1px solid rgba(26,140,78,0.08); font-size:10px; color:#4a6e52; }
`]
})
export class ConnexionComposant {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly formulaire = inject(FormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    motDePasse: ['', [Validators.required, Validators.minLength(8)]],
  });

  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  soumettre(): void {
    console.log('=== SOUMETTRE APPELE ===');
    const { email, motDePasse } = this.formulaire.value;
    console.log('Email:', email, 'MDP length:', motDePasse?.length);

    if (!email || !motDePasse) {
      this.erreur.set('Veuillez remplir tous les champs');
      return;
    }

    this.chargement.set(true);
    this.erreur.set(null);

    const body = { email: email.trim().toLowerCase(), motDePasse };
    console.log('Envoi requete:', body);

    this.http.post('/api/auth/connexion', body).subscribe({
      next: (reponse: any) => {
        console.log('SUCCES:', reponse);
        this.chargement.set(false);
        this.succes.set('Connecté! Token: ' + reponse.tokenAcces?.substring(0, 20) + '...');
        localStorage.setItem('bna_token_acces', reponse.tokenAcces);
        localStorage.setItem('bna_utilisateur', JSON.stringify(reponse.utilisateur));
        setTimeout(() => this.router.navigateByUrl('/tableau-bord'), 500);
      },
      error: (err) => {
        console.error('ERREUR:', err);
        this.chargement.set(false);
        this.erreur.set('Erreur: ' + (err.error?.message || err.message || 'Inconnue'));
      }
    });
  }

  remplirTest(role: string): void {
    const comptes: Record<string, { email: string; mdp: string }> = {
      admin: { email: 'admin@bna.com.tn', mdp: 'BnaFlux2026!' },
      superviseur: { email: 'superviseur@bna.com.tn', mdp: 'BnaFlux2026!' },
      operateur: { email: 'operateur@bna.com.tn', mdp: 'BnaFlux2026!' },
    };
    const c = comptes[role];
    if (c) {
      this.formulaire.patchValue({ email: c.email, motDePasse: c.mdp });
      console.log('Rempli avec:', role, c.email);
    }
  }
}
