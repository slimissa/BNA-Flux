import { ApplicationConfig, provideZoneChangeDetection, LOCALE_ID } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeFrExtra from '@angular/common/locales/extra/fr';

import { routes } from './app.routes';
import { authInterceptor } from './core/intercepteurs/auth.intercepteur';

// Enregistrer la locale française
registerLocaleData(localeFr, 'fr-FR', localeFrExtra);

/**
 * Configuration de l'application Angular BNA-FLUX.
 *
 * Centralise tous les providers nécessaires au fonctionnement
 * de l'application : router, HTTP, animations, localisation.
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-05
 */
export const appConfig: ApplicationConfig = {
  providers: [
    // Zone.js — détection de changements optimisée
    provideZoneChangeDetection({ eventCoalescing: true }),

    // Router
    provideRouter(routes),

    // HttpClient avec intercepteur fonctionnel JWT
    provideHttpClient(withInterceptors([authInterceptor])),

    // Animations Material
    provideAnimations(),

    // Locale française par défaut
    { provide: LOCALE_ID, useValue: 'fr-FR' },
  ],
};