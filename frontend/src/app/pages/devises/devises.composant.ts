// Fichier: pages/devises/devises.composant.ts

import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule } from '@angular/forms';
import { DeviseService } from '../../core/services/devise.service';
import { Devise, formaterMontant, formaterMontantAvecNom } from '@modeles/devise.modele';

@Component({
  selector: 'bna-devises',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatInputModule,
    MatFormFieldModule,
    FormsModule,
  ],
  templateUrl: './devises.composant.html',
  styleUrls: ['./devises.composant.scss'],
})
export class DevisesComposant implements OnInit {
  private readonly deviseService = inject(DeviseService);

  readonly devises = signal<Devise[]>([]);
  readonly devisesFiltrees = signal<Devise[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly recherche = signal('');

  ngOnInit(): void { this.chargerDevises(); }

  chargerDevises(): void {
    this.chargement.set(true);
    this.deviseService.lister().subscribe({
      next: (devises) => {
        this.devises.set(devises);
        this.devisesFiltrees.set(devises);
        this.chargement.set(false);
      },
      error: () => {
        this.erreur.set('Erreur lors du chargement des devises');
        this.chargement.set(false);
      },
    });
  }

  filtrer(): void {
    const terme = this.recherche().toLowerCase().trim();
    if (!terme) {
      this.devisesFiltrees.set(this.devises());
      return;
    }
    this.devisesFiltrees.set(
      this.devises().filter(d =>
        d.code.toLowerCase().includes(terme) ||
        d.nom.toLowerCase().includes(terme)
      )
    );
  }

  getExempleMontant(unitesMineures: number): string {
    const montant = 100.500;
    return formaterMontant(montant, undefined, unitesMineures);
  }

  getDescriptionUnites(unitesMineures: number): string {
    switch (unitesMineures) {
      case 0: return 'Pas de sous-unité (entier)';
      case 2: return 'Centimes (2 décimales)';
      case 3: return 'Millimes (3 décimales)';
      default: return `${unitesMineures} décimales`;
    }
  }

  getCouleurUnites(unitesMineures: number): string {
    switch (unitesMineures) {
      case 0: return '#3498db';
      case 2: return '#2ecc71';
      case 3: return '#f39c12';
      default: return '#9b59b6';
    }
  }

  get activesCount(): number {
    return this.devises().filter(d => d.actif).length;
  }
}