// Fichier: pages/disjoncteurs/disjoncteurs.composant.ts

import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { interval, Subscription, switchMap, startWith } from 'rxjs';
import { DisjoncteurService } from '../../core/services/disjoncteur.service';
import { AuthService } from '../../core/services/auth.service';
import {
  ReponseDisjoncteur,
  EtatDisjoncteur,
  TypeCible,
  LABELS_TYPES_CIBLE,
  ICONES_TYPES_CIBLE,
  LABELS_ETATS_DISJONCTEUR,
  COULEURS_ETATS_DISJONCTEUR,
  COULEURS_BG_ETATS_DISJONCTEUR,
  ICONES_ETATS_DISJONCTEUR,
} from '@modeles/disjoncteur.modele';

@Component({
  selector: 'bna-disjoncteurs',
  standalone: true,
  imports: [
    CommonModule,
    DecimalPipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDividerModule,
  ],
  templateUrl: './disjoncteurs.composant.html',
  styleUrls: ['./disjoncteurs.composant.scss'],
})
export class DisjoncteursComposant implements OnInit, OnDestroy {
  private readonly disjoncteurService = inject(DisjoncteurService);
  readonly authService = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);

  readonly disjoncteurs = signal<ReponseDisjoncteur[]>([]);
  readonly statistiques = signal<{ ouverts: number; miOuverts: number; fermes: number; totalEchecs: number } | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly reinitialisationEnCours = signal<number | null>(null);

  // Constantes
  readonly LABELS_TYPES_CIBLE = LABELS_TYPES_CIBLE;
  readonly ICONES_TYPES_CIBLE = ICONES_TYPES_CIBLE;
  readonly LABELS_ETATS_DISJONCTEUR = LABELS_ETATS_DISJONCTEUR;
  readonly COULEURS_ETATS_DISJONCTEUR = COULEURS_ETATS_DISJONCTEUR;
  readonly COULEURS_BG_ETATS_DISJONCTEUR = COULEURS_BG_ETATS_DISJONCTEUR;
  readonly ICONES_ETATS_DISJONCTEUR = ICONES_ETATS_DISJONCTEUR;

  private subscription = new Subscription();

  ngOnInit(): void { this.chargerDisjoncteurs(); }

  ngOnDestroy(): void { this.subscription.unsubscribe(); }

  chargerDisjoncteurs(): void {
    this.chargement.set(true);
    this.subscription.add(
      this.disjoncteurService.lister().subscribe({
        next: (r) => {
          this.disjoncteurs.set(r.disjoncteurs || []);
          this.statistiques.set(r.statistiques);
          this.chargement.set(false);
        },
        error: () => { this.erreur.set('Erreur lors du chargement'); this.chargement.set(false); },
      })
    );
  }

  reinitialiser(disjoncteur: ReponseDisjoncteur): void {
    this.reinitialisationEnCours.set(disjoncteur.id);
    this.disjoncteurService.reinitialiser(disjoncteur.id).subscribe({
      next: () => {
        this.reinitialisationEnCours.set(null);
        this.snackBar.open(`Disjoncteur "${disjoncteur.nom}" réinitialisé`, 'Fermer', { duration: 3000, panelClass: ['snackbar-success'] });
        this.chargerDisjoncteurs();
      },
      error: () => {
        this.reinitialisationEnCours.set(null);
        this.snackBar.open('Erreur lors de la réinitialisation', 'Fermer', { duration: 4000, panelClass: ['snackbar-error'] });
      },
    });
  }

  getPourcentageEchecs(d: ReponseDisjoncteur): number {
    return this.disjoncteurService.getPourcentageEchecs(d);
  }

  formaterTempsRestant(minutes: number | undefined): string {
    return this.disjoncteurService.formaterTempsRestant(minutes);
  }

  get disjoncteursOuverts(): ReponseDisjoncteur[] {
    return this.disjoncteurs().filter(d => d.etat === 'OUVERT');
  }

  get disjoncteursMiOuverts(): ReponseDisjoncteur[] {
    return this.disjoncteurs().filter(d => d.etat === 'MI_OUVERT');
  }

  get disjoncteursFermes(): ReponseDisjoncteur[] {
    return this.disjoncteurs().filter(d => d.etat === 'FERME');
  }
}