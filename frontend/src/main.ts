import { bootstrapApplication } from '@angular/platform-browser';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideRouter, withHashLocation } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule } from '@angular/material/dialog';
import { ToastrModule } from 'ngx-toastr';

import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';
import { AuthInterceptor } from './app/core/intercepteurs/auth.intercepteur';

/**
 * Point d'entrée principal de l'application Angular BNA-FLUX.
 *
 * Bootstrappe le composant racine avec :
 * - Router (stratégie hash pour compatibilité Docker/Nginx)
 * - HttpClient avec intercepteur JWT
 * - Animations Material
 * - Toastr pour les notifications
 * - Material SnackBar et Dialog
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
bootstrapApplication(AppComponent, {
  providers: [
    // Router avec stratégie hash (#/) pour le déploiement
    provideRouter(routes, withHashLocation()),

    // HttpClient avec intercepteurs
    provideHttpClient(withInterceptorsFromDi()),

    // Intercepteur JWT
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true,
    },

    // Animations Angular
    provideAnimations(),

    // Modules Material
    importProvidersFrom(
      MatSnackBarModule,
      MatDialogModule,
      ToastrModule.forRoot({
        timeOut: 5000,
        positionClass: 'toast-bottom-right',
        preventDuplicates: true,
        progressBar: true,
        progressAnimation: 'decreasing',
        tapToDismiss: true,
        newestOnTop: true,
        maxOpened: 5,
      })
    ),
  ],
}).catch((err) => console.error('Erreur de bootstrap Angular :', err));