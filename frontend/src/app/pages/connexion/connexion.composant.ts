// Fichier: pages/connexion/connexion.composant.ts

import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'bna-connexion',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './connexion.composant.html',
  styleUrls: ['./connexion.composant.scss'],
})
export class ConnexionComposant {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);

  /** Formulaire de connexion */
  readonly formulaire: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    motDePasse: ['', [Validators.required, Validators.minLength(8)]],
  });

  /** État de chargement */
  readonly chargement = signal(false);

  /** Visibilité du mot de passe */
  readonly motDePasseVisible = signal(false);

  /** Message d'erreur */
  readonly erreur = signal<string | null>(null);

  /** Année courante pour le footer */
  readonly anneeCourante = new Date().getFullYear();

  /** URL de redirection après connexion */
  private readonly redirectUrl: string | null = null;

  constructor() {
    this.redirectUrl = this.route.snapshot.queryParams['redirectUrl'] || null;
  }

  /** Soumet le formulaire de connexion */
  async soumettre(): Promise<void> {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }

    this.chargement.set(true);
    this.erreur.set(null);

    const { email, motDePasse } = this.formulaire.value;

    this.authService.connexion(email, motDePasse).subscribe({
      next: () => {
        this.chargement.set(false);
        this.snackBar.open('Connexion réussie — Bienvenue sur BNA-FLUX', 'Fermer', {
          duration: 4000,
          panelClass: ['snackbar-success'],
          horizontalPosition: 'center',
          verticalPosition: 'bottom',
        });

        // Redirection après connexion
        const destination = this.redirectUrl || '/tableau-bord';
        this.router.navigateByUrl(destination);
      },
      error: (err) => {
        this.chargement.set(false);
        this.erreur.set(err.message || 'Erreur de connexion');
        this.formulaire.get('motDePasse')?.reset();
      },
    });
  }

  /** Bascule la visibilité du mot de passe */
  basculerVisibiliteMotDePasse(): void {
    this.motDePasseVisible.update((v) => !v);
  }

  /** Retourne le message d'erreur pour un champ */
  getErreurChamp(champ: string): string {
    const controle = this.formulaire.get(champ);
    if (!controle || !controle.touched || !controle.errors) return '';

    if (controle.errors['required']) return 'Ce champ est requis';
    if (controle.errors['email']) return 'Format d\'email invalide';
    if (controle.errors['minlength']) return 'Minimum 8 caractères requis';

    return 'Champ invalide';
  }

  /** Remplit les champs avec les identifiants de test (développement) */
  remplirTest(role: 'admin' | 'superviseur' | 'operateur'): void {
    const comptes: Record<string, { email: string; mdp: string }> = {
      admin: { email: 'admin@bna.com.tn', mdp: 'BnaFlux2026!' },
      superviseur: { email: 'superviseur@bna.com.tn', mdp: 'BnaFlux2026!' },
      operateur: { email: 'operateur@bna.com.tn', mdp: 'BnaFlux2026!' },
    };

    const compte = comptes[role];
    this.formulaire.patchValue({
      email: compte.email,
      motDePasse: compte.mdp,
    });
  }
}