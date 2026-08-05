// Fichier: pages/transactions/liste-transactions/liste-transactions.composant.ts

import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { TransactionService } from '../../../core/services/transaction.service';
import { DeviseService } from '../../../core/services/devise.service';
import {
  ResumeTransaction,
  FiltresTransaction,
  StatutTransaction,
  TypeTransaction,
  Canal,
  PageReponse,
  LABELS_STATUTS,
  COULEURS_STATUTS,
  ICONES_STATUTS,
  LABELS_TYPES_TRANSACTION,
  ICONES_TYPES_TRANSACTION,
  LABELS_CANAUX,
  ICONES_CANAUX,
} from '@modeles/transaction.modele';
import { Devise } from '@modeles/devise.modele';

@Component({
  selector: 'bna-liste-transactions',
  standalone: true,
  imports: [
    CommonModule,
    DecimalPipe,
    DatePipe,
    RouterModule,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatExpansionModule,
  ],
  templateUrl: './liste-transactions.composant.html',
  styleUrls: ['./liste-transactions.composant.scss'],
})
export class ListeTransactionsComposant implements OnInit, OnDestroy {
  private readonly transactionService = inject(TransactionService);
  private readonly deviseService = inject(DeviseService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  // Données
  readonly transactions = signal<ResumeTransaction[]>([]);
  readonly devises = signal<Devise[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);

  // Pagination
  readonly pageActuelle = signal(0);
  readonly taillePage = signal(20);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);

  // Tri
  readonly triActif = signal('dateTransaction');
  readonly directionTri = signal<'asc' | 'desc'>('desc');

  // Filtres
  readonly formulaireFiltres: FormGroup = this.fb.group({
    statut: [''],
    codeDevise: [''],
    canal: [''],
    typeTransaction: [''],
    minMontant: [''],
    maxMontant: [''],
    dateDebut: [''],
    dateFin: [''],
  });

  // Filtres actifs (pour les chips)
  readonly filtresActifs = signal<string[]>([]);
  readonly colonnesAffichees = [
    'referenceTransaction',
    'dateTransaction',
    'typeTransaction',
    'canal',
    'montant',
    'statutTransaction',
    'scoreRisque',
    'actions',
  ];

  // Constantes exportées pour le template
  readonly LABELS_STATUTS = LABELS_STATUTS;
  readonly COULEURS_STATUTS = COULEURS_STATUTS;
  readonly ICONES_STATUTS = ICONES_STATUTS;
  readonly LABELS_TYPES_TRANSACTION = LABELS_TYPES_TRANSACTION;
  readonly ICONES_TYPES_TRANSACTION = ICONES_TYPES_TRANSACTION;
  readonly LABELS_CANAUX = LABELS_CANAUX;
  readonly ICONES_CANAUX = ICONES_CANAUX;
  readonly STATUTS: StatutTransaction[] = ['ACCEPTE', 'SURVEILLE', 'BLOQUE'];
  readonly TYPES_TRANSACTION: TypeTransaction[] = ['VIREMENT', 'CHEQUE', 'ESPECES', 'CARTE', 'PRELEVEMENT'];
  readonly CANAUX: Canal[] = ['AGENCE', 'DAB', 'EN_LIGNE', 'MOBILE'];

  private subscription = new Subscription();

  ngOnInit(): void {
    this.chargerDevises();
    this.chargerTransactions();

    // Réagir aux changements de filtres avec debounce
    this.subscription.add(
      this.formulaireFiltres.valueChanges
        .pipe(debounceTime(400), distinctUntilChanged())
        .subscribe(() => {
          this.pageActuelle.set(0);
          this.chargerTransactions();
          this.mettreAJourFiltresActifs();
        })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  chargerTransactions(): void {
    this.chargement.set(true);
    this.erreur.set(null);

    const filtres = this.construireFiltres();

    this.transactionService.rechercher(filtres).subscribe({
      next: (reponse) => {
        this.transactions.set(reponse.donnees || []);
        this.totalElements.set(reponse.pagination.totalElements);
        this.totalPages.set(reponse.pagination.totalPages);
        this.chargement.set(false);
      },
      error: () => {
        this.erreur.set('Erreur lors du chargement des transactions');
        this.chargement.set(false);
      },
    });
  }

  chargerDevises(): void {
    this.deviseService.lister().subscribe({
      next: (devises) => this.devises.set(devises),
    });
  }

  construireFiltres(): FiltresTransaction {
    const f = this.formulaireFiltres.value;
    return {
      page: this.pageActuelle(),
      taille: this.taillePage(),
      tri: `${this.triActif()},${this.directionTri()}`,
      statut: f.statut || undefined,
      codeDevise: f.codeDevise || undefined,
      canal: f.canal || undefined,
      typeTransaction: f.typeTransaction || undefined,
      minMontant: f.minMontant ? Number(f.minMontant) : undefined,
      maxMontant: f.maxMontant ? Number(f.maxMontant) : undefined,
      dateDebut: f.dateDebut || undefined,
      dateFin: f.dateFin || undefined,
    };
  }

  mettreAJourFiltresActifs(): void {
    const f = this.formulaireFiltres.value;
    const actifs: string[] = [];
    if (f.statut) actifs.push(`Statut: ${LABELS_STATUTS[f.statut]}`);
    if (f.codeDevise) actifs.push(`Devise: ${f.codeDevise}`);
    if (f.canal) actifs.push(`Canal: ${LABELS_CANAUX[f.canal]}`);
    if (f.typeTransaction) actifs.push(`Type: ${LABELS_TYPES_TRANSACTION[f.typeTransaction]}`);
    if (f.minMontant) actifs.push(`Min: ${f.minMontant} TND`);
    if (f.maxMontant) actifs.push(`Max: ${f.maxMontant} TND`);
    this.filtresActifs.set(actifs);
  }

  // Pagination
  onChangementPage(event: PageEvent): void {
    this.pageActuelle.set(event.pageIndex);
    this.taillePage.set(event.pageSize);
    this.chargerTransactions();
  }

  // Tri
  onTri(sort: Sort): void {
    this.triActif.set(sort.active);
    this.directionTri.set(sort.direction || 'desc');
    this.chargerTransactions();
  }

  // Filtres
  effacerFiltres(): void {
    this.formulaireFiltres.reset({
      statut: '',
      codeDevise: '',
      canal: '',
      typeTransaction: '',
      minMontant: '',
      maxMontant: '',
      dateDebut: '',
      dateFin: '',
    });
    this.filtresActifs.set([]);
    this.pageActuelle.set(0);
    this.chargerTransactions();
  }

  supprimerFiltre(cle: string): void {
    this.formulaireFiltres.get(cle)?.reset();
  }

  aDesFiltres(): boolean {
    return this.filtresActifs().length > 0;
  }

  // Navigation
  voirDetail(id: number): void {
    this.router.navigate(['/transactions', id]);
  }

  // Affichage
  getCouleurScore(score: number): string {
    if (score >= 70) return '#e74c3c';
    if (score >= 30) return '#f39c12';
    return '#2ecc71';
  }

  getLargeurScore(score: number): string {
    return `${Math.min(score, 100)}%`;
  }

  formaterMontant(montant: number, codeDevise: string): string {
    const symbole = this.devises().find(d => d.code === codeDevise)?.symbole || codeDevise;
    return `${symbole} ${montant.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }
}