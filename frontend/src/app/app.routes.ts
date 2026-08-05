import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'connexion',
    loadComponent: () =>
      import('./pages/connexion/connexion.composant').then((m) => m.ConnexionComposant),
  },
  { path: '', redirectTo: 'connexion', pathMatch: 'full' },
  { path: '**', redirectTo: 'connexion' },
];
