import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: 'connexion', loadComponent: () => import('./pages/connexion/connexion.composant').then(m => m.ConnexionComposant) },
  { path: 'tableau-bord', loadComponent: () => import('./pages/tableau-bord/tableau-bord.composant').then(m => m.TableauBordComposant) },
  { path: 'transactions', loadComponent: () => import('./pages/transactions/liste-transactions/liste-transactions.composant').then(m => m.ListeTransactionsComposant) },
  { path: 'transactions/:id', loadComponent: () => import('./pages/transactions/detail-transaction/detail-transaction.composant').then(m => m.DetailTransactionComposant) },
  { path: 'devises', loadComponent: () => import('./pages/devises/devises.composant').then(m => m.DevisesComposant) },
  { path: 'disjoncteurs', loadComponent: () => import('./pages/disjoncteurs/disjoncteurs.composant').then(m => m.DisjoncteursComposant) },
  { path: '', redirectTo: 'connexion', pathMatch: 'full' },
  { path: 'testeur-regle', loadComponent: () => import('./pages/testeur-regle/testeur-regle.composant').then(m => m.TesteurRegleComposant) },
  { path: '**', redirectTo: 'connexion' },
];
