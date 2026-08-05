import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { Router, NavigationEnd, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatBadgeModule } from '@angular/material/badge';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subscription, filter, interval } from 'rxjs';
import { AuthService } from './core/services/auth.service';
import { TableauBordService } from './core/services/tableau-bord.service';
import { RouterModule } from '@angular/router';

/**
 * Composant racine de l'application BNA-FLUX.
 *
 * Structure :
 * - Sidebar de navigation avec icônes et libellés
 * - Header avec titre, breadcrumb, et menu utilisateur
 * - Contenu principal avec router-outlet
 * - Footer avec statut et copyright
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
@Component({
  selector: 'bna-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterModule,
    MatSidenavModule,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    MatListModule,
    MatMenuModule,
    MatBadgeModule,
    MatDividerModule,
    MatTooltipModule,
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  readonly authService = inject(AuthService);
  private readonly tableauBordService = inject(TableauBordService);

  /** Titre affiché dans le header */
  titrePage = 'Tableau de Bord';

  /** L'utilisateur est-il authentifié ? */
  estConnecte = false;

  /** Nombre d'alertes non acquittées (badge) */
  nombreAlertes = 0;

  /** Nombre de disjoncteurs ouverts (badge) */
  nombreDisjoncteursOuverts = 0;

  /** Année courante pour le footer */
  anneeCourante = new Date().getFullYear();

  private subscriptions = new Subscription();

  /** Menu de navigation principal */
  readonly menuItems: MenuItem[] = [
    {
      route: '/tableau-bord',
      icone: 'fa-solid fa-chart-pie',
      libelle: 'Tableau de Bord',
    },
    {
      route: '/transactions',
      icone: 'fa-solid fa-money-bill-transfer',
      libelle: 'Transactions',
    },
    {
      route: '/regles',
      icone: 'fa-solid fa-shield-halved',
      libelle: 'Règles',
    },
    {
      route: '/disjoncteurs',
      icone: 'fa-solid fa-bolt',
      libelle: 'Disjoncteurs',
      badge: () => this.nombreDisjoncteursOuverts,
      badgeCouleur: 'danger',
    },
    {
      route: '/devises',
      icone: 'fa-solid fa-coins',
      libelle: 'Devises',
    },
  ];

  ngOnInit(): void {
    // Suivre l'état de connexion
    this.subscriptions.add(
      this.authService.estConnecte$.subscribe((connecte) => {
        this.estConnecte = connecte;
      })
    );

    // Mettre à jour le titre selon la route active
    this.subscriptions.add(
      this.router.events
        .pipe(filter((event) => event instanceof NavigationEnd))
        .subscribe((event) => {
          const navEnd = event as NavigationEnd;
          this.mettreAJourTitre(navEnd.urlAfterRedirects);
        })
    );

    // Initialiser le titre
    this.mettreAJourTitre(this.router.url);

    // Polling des statistiques toutes les 60 secondes
    if (this.estConnecte) {
      this.subscriptions.add(
        interval(60000).subscribe(() => this.chargerStatistiques())
      );
      this.chargerStatistiques();
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  /** Déconnecte l'utilisateur et redirige vers la page de connexion */
  deconnexion(): void {
    this.authService.deconnexion();
    this.router.navigate(['/connexion']);
  }

  /** Retourne les initiales de l'utilisateur connecté */
  getInitiales(): string {
    const nom = this.authService.getUtilisateur()?.nom || 'U';
    return nom
      .split(' ')
      .map((partie) => partie.charAt(0))
      .join('')
      .toUpperCase()
      .substring(0, 2);
  }

  /** Retourne le nom complet de l'utilisateur connecté */
  getNomUtilisateur(): string {
    return this.authService.getUtilisateur()?.nom || 'Utilisateur';
  }

  /** Retourne le rôle de l'utilisateur connecté */
  getRoleUtilisateur(): string {
    const role = this.authService.getUtilisateur()?.role;
    if (!role) return '';
    const labels: Record<string, string> = {
      ADMIN: 'Administrateur',
      SUPERVISEUR: 'Superviseur',
      OPERATEUR: 'Opérateur',
    };
    return labels[role] || role;
  }

  private mettreAJourTitre(url: string): void {
    const titres: Record<string, string> = {
      '/tableau-bord': 'Tableau de Bord',
      '/transactions': 'Transactions',
      '/regles': 'Règles de Surveillance',
      '/disjoncteurs': 'Disjoncteurs',
      '/devises': 'Devises',
    };

    // Chercher une correspondance exacte ou partielle
    const match = Object.entries(titres).find(([path]) => url.startsWith(path));
    this.titrePage = match ? match[1] : 'BNA-FLUX';
  }

  private chargerStatistiques(): void {
    this.tableauBordService.getStatistiques().subscribe({
      next: (stats) => {
        this.nombreAlertes = stats.alertesNonAcquittees || 0;
        this.nombreDisjoncteursOuverts = stats.disjoncteursOuverts || 0;
      },
      error: () => {
        // Silencieux — les badges ne se mettent pas à jour
      },
    });
  }
}

/** Élément du menu de navigation */
interface MenuItem {
  route: string;
  icone: string;
  libelle: string;
  badge?: () => number;
  badgeCouleur?: 'danger' | 'warning' | 'info';
}