// Fichier: pages/regles/gestion-regles.composant.ts

import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { RegleService } from '../../core/services/regle.service';
import { AuthService } from '../../core/services/auth.service';
import {
  ReponseRegle,
  RequeteRegle,
  Severite,
  TypeRegle,
  LABELS_SEVERITES,
  COULEURS_SEVERITES,
  COULEURS_BG_SEVERITES,
  ICONES_SEVERITES,
  LABELS_TYPES_REGLE,
  COULEURS_TYPES_REGLE,
  VARIABLES_SPEL,
  OPERATEURS_SPEL,
} from '@modeles/regle.modele';

@Component({
  selector: 'bna-gestion-regles',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatChipsModule,
    MatTooltipModule,
    MatDialogModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatExpansionModule,
    MatDividerModule,
  ],
  templateUrl: './gestion-regles.composant.html',
  styleUrls: ['./gestion-regles.composant.scss'],
})
export class GestionReglesComposant implements OnInit {
  private readonly regleService = inject(RegleService);
  readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly regles = signal<ReponseRegle[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly formulaireVisible = signal(false);
  readonly modeEdition = signal(false);
  readonly regleEnEdition = signal<number | null>(null);
  readonly sauvegardeEnCours = signal(false);

  // Test SpEL
  readonly testExpression = signal('');
  readonly testResultat = signal<{ syntaxeValide: boolean; message?: string; erreur?: string } | null>(null);
  readonly testEnCours = signal(false);

  // Aide SpEL
  readonly aideSpelVisible = signal(false);

  readonly LABELS_SEVERITES = LABELS_SEVERITES;
  readonly COULEURS_SEVERITES = COULEURS_SEVERITES;
  readonly COULEURS_BG_SEVERITES = COULEURS_BG_SEVERITES;
  readonly ICONES_SEVERITES = ICONES_SEVERITES;
  readonly LABELS_TYPES_REGLE = LABELS_TYPES_REGLE;
  readonly COULEURS_TYPES_REGLE = COULEURS_TYPES_REGLE;
  readonly SEVERITES: Severite[] = ['CRITIQUE', 'ELEVE', 'MOYEN', 'FAIBLE'];
  readonly TYPES_REGLE: TypeRegle[] = ['AUTO_REJET', 'ALERTE', 'PREVENTION'];
  readonly VARIABLES_SPEL = VARIABLES_SPEL;
  readonly OPERATEURS_SPEL = OPERATEURS_SPEL;

  readonly formulaire: FormGroup = this.fb.group({
    nom: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(200)]],
    description: [''],
    expressionCondition: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
    severite: ['MOYEN', Validators.required],
    contributionScore: [15, [Validators.required, Validators.min(1), Validators.max(100)]],
    typeRegle: ['ALERTE', Validators.required],
    categorie: [''],
    priorite: [50, [Validators.required, Validators.min(0), Validators.max(100)]],
    actif: [true],
  });

  ngOnInit(): void { this.chargerRegles(); }

  chargerRegles(): void {
    this.chargement.set(true);
    this.regleService.lister().subscribe({
      next: (regles) => { this.regles.set(regles); this.chargement.set(false); },
      error: () => { this.erreur.set('Erreur lors du chargement des règles'); this.chargement.set(false); },
    });
  }

  // CRUD
  ouvrirCreation(): void {
    this.modeEdition.set(false);
    this.regleEnEdition.set(null);
    this.formulaire.reset({ severite: 'MOYEN', contributionScore: 15, typeRegle: 'ALERTE', priorite: 50, actif: true });
    this.formulaireVisible.set(true);
  }

  ouvrirEdition(regle: ReponseRegle): void {
    this.modeEdition.set(true);
    this.regleEnEdition.set(regle.id);
    this.formulaire.patchValue({
      nom: regle.nom,
      description: regle.description || '',
      expressionCondition: regle.expressionCondition,
      severite: regle.severite,
      contributionScore: regle.contributionScore,
      typeRegle: regle.typeRegle,
      categorie: regle.categorie || '',
      priorite: regle.priorite,
      actif: regle.actif,
    });
    this.formulaireVisible.set(true);
  }

  annuler(): void { this.formulaireVisible.set(false); }

  sauvegarder(): void {
    if (this.formulaire.invalid) { this.formulaire.markAllAsTouched(); return; }
    this.sauvegardeEnCours.set(true);
    const requete: RequeteRegle = this.formulaire.value;

    const appel = this.modeEdition()
      ? this.regleService.modifier(this.regleEnEdition()!, requete)
      : this.regleService.creer(requete);

    appel.subscribe({
      next: () => {
        this.sauvegardeEnCours.set(false);
        this.formulaireVisible.set(false);
        this.snackBar.open(this.modeEdition() ? 'Règle modifiée avec succès' : 'Règle créée avec succès', 'Fermer', { duration: 3000, panelClass: ['snackbar-success'] });
        this.chargerRegles();
      },
      error: (err) => {
        this.sauvegardeEnCours.set(false);
        this.snackBar.open(err.error?.message || 'Erreur lors de la sauvegarde', 'Fermer', { duration: 5000, panelClass: ['snackbar-error'] });
      },
    });
  }

  basculerActif(regle: ReponseRegle): void {
    this.regleService.basculer(regle.id).subscribe({
      next: (r) => {
        this.snackBar.open(r.actif ? 'Règle activée' : 'Règle désactivée', 'Fermer', { duration: 3000 });
        this.chargerRegles();
      },
    });
  }

  supprimer(regle: ReponseRegle): void {
    if (!confirm(`Supprimer définitivement la règle "${regle.nom}" ?`)) return;
    this.regleService.supprimer(regle.id).subscribe({
      next: () => { this.snackBar.open('Règle supprimée', 'Fermer', { duration: 3000 }); this.chargerRegles(); },
    });
  }

  // Test SpEL
  testerSyntaxe(): void {
    const expr = this.testExpression().trim();
    if (!expr) return;
    this.testEnCours.set(true);
    this.testResultat.set(null);
    this.regleService.validerSyntaxe(expr).subscribe({
      next: (r) => { this.testResultat.set(r); this.testEnCours.set(false); },
      error: (err) => { this.testResultat.set({ syntaxeValide: false, erreur: err.error?.message || 'Erreur' }); this.testEnCours.set(false); },
    });
  }

  insererVariable(varNom: string): void {
    const controle = this.formulaire.get('expressionCondition');
    if (!controle) return;
    const valeurActuelle = controle.value || '';
    controle.setValue(valeurActuelle + ' ' + varNom + ' ');
    controle.markAsDirty();
  }

  getErreurChamp(champ: string): string {
    const c = this.formulaire.get(champ);
    if (!c?.touched || !c?.errors) return '';
    if (c.errors['required']) return 'Requis';
    if (c.errors['minlength']) return 'Trop court';
    if (c.errors['maxlength']) return 'Trop long';
    if (c.errors['min']) return 'Minimum ' + c.errors['min'].min;
    if (c.errors['max']) return 'Maximum ' + c.errors['max'].max;
    return 'Invalide';
  }

  // Filtres locaux
  readonly filtreCategorie = signal<string | null>(null);

  get categories(): string[] {
    const cats = new Set(this.regles().map(r => r.categorie).filter(Boolean) as string[]);
    return [...cats].sort();
  }

  get reglesFiltrees(): ReponseRegle[] {
    const cat = this.filtreCategorie();
    if (!cat) return this.regles();
    return this.regles().filter(r => r.categorie === cat);
  }

  filtrerParCategorie(cat: string | null): void {
    this.filtreCategorie.set(cat === this.filtreCategorie() ? null : cat);
  }
}