import { Routes } from '@angular/router';
import { AuthGuard } from './core/gardes/auth.garde';

/**
 * Routes de l'application BNA-FLUX.
 *
 * Structure :
 * - /connexion — Page publique d'authentification
 * - /tableau-bord — Dashboard avec statistiques en temps réel
 * - /transactions — Liste et détail des transactions
 * - /regles — Gestion des règles de surveillance
 * - /disjoncteurs — Surveillance des circuit breakers
 * - /devises — Consultation des devises (lecture seule)
 *
 * Toutes les routes sauf /connexion sont protégées par AuthGuard.
 * Lazy loading pour les modules de pages (performance).
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
export const routes: Routes = [
  // Route publique — Connexion
  {
    path: 'connexion',
    title: 'Connexion — BNA-FLUX',
    loadComponent: () =>
      import('./pages/connexion/connexion.composant').then((m) => m.ConnexionComposant),
  },

  // Routes protégées
  {
    path: '',
    canActivate: [AuthGuard],
    children: [
      // Dashboard
      {
        path: '',
        redirectTo: 'tableau-bord',
        pathMatch: 'full',
      },
      {
        path: 'tableau-bord',
        title: 'Tableau de Bord — BNA-FLUX',
        loadComponent: () =>
          import('./pages/tableau-bord/tableau-bord.composant').then(
            (m) => m.TableauBordComposant
          ),
        data: { animation: 'tableauBord' },
      },

      // Transactions
      {
        path: 'transactions',
        title: 'Transactions — BNA-FLUX',
        loadComponent: () =>
          import(
            './pages/transactions/liste-transactions/liste-transactions.composant'
          ).then((m) => m.ListeTransactionsComposant),
        data: { animation: 'transactions' },
      },
      {
        path: 'transactions/:id',
        title: 'Détail Transaction — BNA-FLUX',
        loadComponent: () =>
          import(
            './pages/transactions/detail-transaction/detail-transaction.composant'
          ).then((m) => m.DetailTransactionComposant),
        data: { animation: 'detailTransaction' },
      },

      // Règles
      {
        path: 'regles',
        title: 'Règles de Surveillance — BNA-FLUX',
        loadComponent: () =>
          import('./pages/regles/gestion-regles.composant').then(
            (m) => m.GestionReglesComposant
          ),
        data: { animation: 'regles' },
      },

      // Disjoncteurs
      {
        path: 'disjoncteurs',
        title: 'Disjoncteurs — BNA-FLUX',
        loadComponent: () =>
          import('./pages/disjoncteurs/disjoncteurs.composant').then(
            (m) => m.DisjoncteursComposant
          ),
        data: { animation: 'disjoncteurs' },
      },

      // Devises (lecture seule)
      {
        path: 'devises',
        title: 'Devises — BNA-FLUX',
        loadComponent: () =>
          import('./pages/devises/devises.composant').then((m) => m.DevisesComposant),
        data: { animation: 'devises' },
      },
    ],
  },

  // Redirection par défaut
  {
    path: '**',
    redirectTo: 'tableau-bord',
  },
];