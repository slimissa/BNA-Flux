// Fichier: pages/tableau-bord/tableau-bord.composant.ts

import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DecimalPipe, PercentPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterModule } from '@angular/router';
import { interval, Subscription, switchMap, startWith } from 'rxjs';
import { TableauBordService } from '../../core/services/tableau-bord.service';
import {
  ResumeTableauBord,
  TendanceJournaliere,
  StatistiquesRapides,
} from '@modeles/tableau-bord.modele';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData, ChartType } from 'chart.js';

@Component({
  selector: 'bna-tableau-bord',
  standalone: true,
  imports: [
    CommonModule,
    DecimalPipe,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatChipsModule,
    MatTabsModule,
    RouterModule,
    BaseChartDirective,
  ],
  templateUrl: './tableau-bord.composant.html',
  styleUrls: ['./tableau-bord.composant.scss'],
})
export class TableauBordComposant implements OnInit, OnDestroy {
  private readonly tableauBordService = inject(TableauBordService);

  readonly resume = signal<ResumeTableauBord | null>(null);
  readonly statistiques = signal<StatistiquesRapides | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly derniereMaj = signal<Date>(new Date());
  readonly tempsEcoule = signal('à l\'instant');
  readonly periodeActive = signal<'7j' | '4s' | '12m'>('7j');
  readonly ongletActif = signal(0);

  private subscription = new Subscription();
  private timerEcoule: any;

  // Données des graphiques

  /** Graphique circulaire — Répartition par statut */
  readonly donneesCamenbert: ChartData<'doughnut'> = {
    labels: ['Acceptées', 'Surveillées', 'Bloquées'],
    datasets: [
      {
        data: [0, 0, 0],
        backgroundColor: ['#2ecc71', '#f39c12', '#e74c3c'],
        borderColor: ['#1a3150', '#1a3150', '#1a3150'],
        borderWidth: 3,
        hoverBorderColor: ['#2ecc71', '#f39c12', '#e74c3c'],
        hoverBackgroundColor: ['#3ddb84', '#f5a623', '#f05a4a'],
      },
    ],
  };

  readonly optionsCamenbert: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '70%',
    plugins: {
      legend: { display: true, position: 'bottom', labels: { color: '#8899aa', padding: 16, font: { size: 12 } } },
      tooltip: { backgroundColor: '#1a2d45', titleColor: '#e0e6ed', bodyColor: '#e0e6ed', borderColor: 'rgba(74,158,255,0.25)', borderWidth: 1, cornerRadius: 8, padding: 12 },
    },
  };

  /** Graphique linéaire — Tendance */
  readonly donneesTendance: ChartData<'line'> = {
    labels: [],
    datasets: [
      {
        label: 'Acceptées',
        data: [],
        borderColor: '#2ecc71',
        backgroundColor: 'rgba(46, 204, 113, 0.1)',
        fill: true,
        tension: 0.4,
        pointRadius: 0,
        borderWidth: 2,
      },
      {
        label: 'Surveillées',
        data: [],
        borderColor: '#f39c12',
        backgroundColor: 'rgba(243, 156, 18, 0.1)',
        fill: true,
        tension: 0.4,
        pointRadius: 0,
        borderWidth: 2,
      },
      {
        label: 'Bloquées',
        data: [],
        borderColor: '#e74c3c',
        backgroundColor: 'rgba(231, 76, 60, 0.1)',
        fill: true,
        tension: 0.4,
        pointRadius: 0,
        borderWidth: 2,
      },
    ],
  };

  readonly optionsTendance: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: true, position: 'bottom', labels: { color: '#8899aa', padding: 20, usePointStyle: true, pointStyleWidth: 8, font: { size: 12 } } },
      tooltip: { backgroundColor: '#1a2d45', titleColor: '#e0e6ed', bodyColor: '#e0e6ed', borderColor: 'rgba(74,158,255,0.25)', borderWidth: 1, cornerRadius: 8, padding: 12 },
    },
    scales: {
      x: { grid: { color: 'rgba(74,158,255,0.06)' }, ticks: { color: '#5a6d80', font: { size: 11 } } },
      y: { grid: { color: 'rgba(74,158,255,0.06)' }, ticks: { color: '#5a6d80', font: { size: 11 } }, beginAtZero: true },
    },
  };

  /** Graphique barres — Alertes par niveau */
  readonly donneesBarresAlertes: ChartData<'bar'> = {
    labels: ['Faible', 'Moyen', 'Élevé', 'Critique'],
    datasets: [
      {
        data: [0, 0, 0, 0],
        backgroundColor: ['#3498db', '#f1c40f', '#e67e22', '#e74c3c'],
        borderRadius: 8,
        borderSkipped: false,
      },
    ],
  };

  readonly optionsBarresAlertes: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: 'x',
    plugins: { legend: { display: false }, tooltip: { backgroundColor: '#1a2d45', cornerRadius: 8 } },
    scales: {
      x: { grid: { display: false }, ticks: { color: '#5a6d80', font: { size: 11 } } },
      y: { grid: { color: 'rgba(74,158,255,0.06)' }, ticks: { color: '#5a6d80', font: { size: 11 } }, beginAtZero: true },
    },
  };

  // Cycles de vie
  ngOnInit(): void {
    this.chargerDonnees();
    this.subscription.add(
      interval(30000)
        .pipe(switchMap(() => this.tableauBordService.getStatistiques()))
        .subscribe({
          next: (stats) => {
            this.statistiques.set(stats);
            this.mettreAJourTemps();
          },
        })
    );
    this.timerEcoule = setInterval(() => this.mettreAJourTemps(), 30000);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    if (this.timerEcoule) clearInterval(this.timerEcoule);
  }

  // Chargement des données
  private chargerDonnees(): void {
    this.chargement.set(true);
    this.erreur.set(null);

    this.tableauBordService.getResumeAujourdhui().subscribe({
      next: (resume) => {
        this.resume.set(resume);
        this.chargement.set(false);
        this.mettreAJourGraphiques(resume);
        this.mettreAJourTemps();
      },
      error: () => {
        this.erreur.set('Erreur lors du chargement du tableau de bord');
        this.chargement.set(false);
      },
    });

    this.tableauBordService.getStatistiques().subscribe({
      next: (stats) => this.statistiques.set(stats),
    });
  }

  /** Change la période de tendance */
  changerPeriode(periode: '7j' | '4s' | '12m'): void {
    this.periodeActive.set(periode);
    this.chargement.set(true);

    const appel = periode === '7j'
      ? this.tableauBordService.getTendance7Jours()
      : periode === '4s'
        ? this.tableauBordService.getTendance4Semaines()
        : this.tableauBordService.getTendance12Mois();

    appel.subscribe({
      next: (tendance) => {
        this.mettreAJourTendance(tendance);
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false),
    });
  }

  private mettreAJourGraphiques(resume: ResumeTableauBord): void {
    // Camembert
    this.donneesCamenbert.datasets[0].data = [
      resume.transactions.acceptees,
      resume.transactions.surveillees,
      resume.transactions.bloquees,
    ];
    this.donneesCamenbert = { ...this.donneesCamenbert };

    // Barres alertes
    const parNiveau = resume.alertes.parNiveau || {};
    this.donneesBarresAlertes.datasets[0].data = [
      parNiveau['FAIBLE'] || 0,
      parNiveau['MOYEN'] || 0,
      parNiveau['ELEVE'] || 0,
      parNiveau['CRITIQUE'] || 0,
    ];
    this.donneesBarresAlertes = { ...this.donneesBarresAlertes };

    // Tendance
    if (resume.tendance?.length) {
      this.mettreAJourTendance(resume.tendance);
    } else {
      this.tableauBordService.getTendance7Jours().subscribe({
        next: (tendance) => this.mettreAJourTendance(tendance),
      });
    }
  }

  private mettreAJourTendance(tendance: TendanceJournaliere[]): void {
    this.donneesTendance.labels = tendance.map((t) => {
      const d = new Date(t.date);
      return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
    });
    this.donneesTendance.datasets[0].data = tendance.map((t) => t.acceptees);
    this.donneesTendance.datasets[1].data = tendance.map((t) => t.surveillees);
    this.donneesTendance.datasets[2].data = tendance.map((t) => t.bloquees);
    this.donneesTendance = { ...this.donneesTendance };
  }

  private mettreAJourTemps(): void {
    this.derniereMaj.set(new Date());
    const secondes = Math.floor((Date.now() - this.derniereMaj().getTime()) / 1000);
    if (secondes < 60) this.tempsEcoule.set('à l\'instant');
    else if (secondes < 3600) this.tempsEcoule.set(`il y a ${Math.floor(secondes / 60)} min`);
    else this.tempsEcoule.set(`il y a ${Math.floor(secondes / 3600)}h`);
  }

  rafraichir(): void { this.chargerDonnees(); }

  // Accesseurs calculés
  get tauxAcceptation(): number { const r = this.resume(); return r?.transactions.total ? Math.round((r.transactions.acceptees / r.transactions.total) * 100) : 0; }
  get tauxSurveillance(): number { const r = this.resume(); return r?.transactions.total ? Math.round((r.transactions.surveillees / r.transactions.total) * 100) : 0; }
  get tauxBlocage(): number { const r = this.resume(); return r?.transactions.total ? Math.round((r.transactions.bloquees / r.transactions.total) * 100) : 0; }
  get aDesAlertes(): boolean { return (this.statistiques()?.alertesActionsRequises ?? 0) > 0; }
  get aDesDisjoncteurs(): boolean { return (this.statistiques()?.disjoncteursOuverts ?? 0) > 0; }
}