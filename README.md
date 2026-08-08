# BNA-FLUX — Surveillance des Transactions Bancaires en Temps Réel

![Version](https://img.shields.io/badge/version-1.1.0-green)
![Java](https://img.shields.io/badge/java-21-orange)
![Spring Boot](https://img.shields.io/badge/spring--boot-3.3.0-green)
![Angular](https://img.shields.io/badge/angular-18-red)
![Docker](https://img.shields.io/badge/docker-ready-brightgreen)
![License](https://img.shields.io/badge/license-Apache%202.0-lightgrey)

**Système de surveillance des transactions bancaires avec pipeline de validation, enrichissement, évaluation de règles SpEL, notation du risque, circuit breaker et piste d'audit hash-chaînée SHA-256.**

Développé dans le cadre du stage d'été 2026 à la **Banque Nationale Agricole (BNA)** — Tunisie.

---

## Table des Matières

- [Architecture](#-architecture)
- [Fonctionnalités](#-fonctionnalités)
- [Stack Technique](#-stack-technique)
- [Démarrage Rapide](#-démarrage-rapide)
- [Utilisateurs de Test](#-utilisateurs-de-test)
- [API Endpoints](#-api-endpoints)
- [Pipeline de Traitement](#-pipeline-de-traitement)
- [Moteur de Règles](#-moteur-de-règles-spel)
- [Piste d'Audit](#-piste-daudit-sha-256)
- [Circuit Breaker](#-circuit-breaker-disjoncteur)
- [Structure du Projet](#-structure-du-projet)
- [Déploiement Docker](#-déploiement-docker)
- [Tests](#-tests)
- [Auteur](#-auteur)

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     BNA-FLUX                                │
├─────────────────────────────────────────────────────────────┤
│  Frontend (Angular 18)  │  Backend (Spring Boot 3.3)       │
│  Port 80 (Nginx)        │  Port 8080 (Tomcat)              │
│                         │                                  │
│  Pages:                 │  Pipeline 5 étapes:              │
│  • Connexion            │  1. Validation (RIB mod 97)      │
│  • Tableau de Bord      │  2. Enrichissement               │
│  • Transactions         │  3. Évaluation règles (SpEL)     │
│  • Détail Transaction   │  4. Notation (score 0-100)       │
│  • Devises              │  5. Persistance (audit SHA-256)  │
│  • Disjoncteurs         │                                  │
│  • Testeur SpEL         │  Notifications:                  │
│  • Export PDF           │  • WebSocket STOMP               │
│                         │  • Emails SMTP (HTML)            │
│  Authentification JWT   │  • Documentation OpenAPI         │
│                         │  Base de données: PostgreSQL 16   │
│  Authentification JWT   │  (H2 en développement)           │
└─────────────────────────────────────────────────────────────┘
```

---

## Fonctionnalités

### Authentification
- Login/Logout avec JWT (HMAC-SHA512)
- Token d'accès (60 min) + Token de rafraîchissement (24h)
- Rafraîchissement automatique via intercepteur HTTP
- Rôles : ADMIN, SUPERVISEUR, OPERATEUR
- Protection des routes par rôle et agence

### Tableau de Bord
- Statistiques en temps réel (transactions, alertes, score moyen)
- Graphiques : tendance, répartition par statut, alertes par niveau
- Top 5 des règles les plus déclenchées
- Disjoncteurs ouverts avec actions requises

### Transactions
- Soumission via API REST
- Liste avec filtrage (statut, devise, canal, type, montant, dates)
- Pagination et tri
- Détail avec score de risque, alertes, piste d'audit
- Vérification d'intégrité SHA-256

### Règles de Surveillance
- 10 règles par défaut (configurables)
- Expressions SpEL évaluées dynamiquement
- Sévérités : FAIBLE, MOYEN, ELEVE, CRITIQUE
- Types : PREVENTION, ALERTE, AUTO_REJET
- Test d'expression via API
- Activation/désactivation sans redéploiement

### Disjoncteurs (Circuit Breakers)
- Protection automatique contre les anomalies
- États : FERMÉ → OUVERT → MI_OUVERT → FERMÉ
- Seuil d'échecs configurable
- Transition automatique après délai
- Réinitialisation manuelle (SUPERVISEUR/ADMIN)

### Piste d'Audit
- Chaîne de hachage SHA-256 immuable
- Chaque étape du pipeline génère une entrée
- Les entrées sont chaînées (hashPrecedent → hashCourant)
- Vérification d'intégrité par recalcul

### Notifications WebSocket (Temps Réel)
- Alertes instantanées via WebSocket STOMP
- Toast notifications dans le navigateur
- Abonnement aux canaux : /topic/alertes, /topic/disjoncteurs
- Reconnexion automatique en cas de déconnexion

### Export PDF
- Rapport professionnel avec logo BNA intégré
- Détails de transaction, score, statut
- Piste d'audit SHA-256 complète
- Bouton de téléchargement sur la page de détail

### Testeur d'Expressions SpEL
- Interface interactive de validation d'expressions
- Feedback instantané (✅ syntaxe valide / ❌ erreur)
- Localisation précise des erreurs (position du caractère)
- Variables disponibles affichées pour référence

### Service Email (SMTP)
- Emails HTML professionnels avec branding BNA
- Alertes CRITIQUE : envoi immédiat asynchrone
- Alertes ELEVE : envoi groupé toutes les 15 minutes
- Console en développement, SMTP réel en production
- Configuration par variables d'environnement

### Documentation API (Swagger/OpenAPI)
- Interface Swagger UI interactive (/swagger-ui.html)
- Spécification OpenAPI 3.0 complète (/api-docs)
- Tous les endpoints documentés avec exemples
- Schémas de requêtes/réponses inclus

### Devises
- 17 devises ISO 4217 supportées
- TND (3 décimales), EUR/USD (2 décimales), JPY (0 décimale)
- Endpoint public (sans authentification)

---

## Stack Technique

| Couche | Technologie | Version |
|--------|------------|---------|
| **Backend** | Java | 21 |
| **Framework** | Spring Boot | 3.3.0 |
| **Sécurité** | Spring Security + JWT (JJWT) | 6.3 / 0.12 |
| **Base de données** | PostgreSQL (prod) / H2 (dev) | 16 / 2.2 |
| **ORM** | Hibernate / JPA | 6.5 |
| **Règles** | Spring Expression Language (SpEL) | — |
| **Frontend** | Angular | 18 |
| **UI** | Angular Material | 18 |
| **Graphiques** | Chart.js + ng2-charts | 4.4 / 6 |
| **Authentification** | JWT (HMAC-SHA512) | — |
| **Build** | Maven | 3.9 |
| **Déploiement** | Docker + Docker Compose | — |
| **Tests** | JUnit 5 + Mockito + TestContainers | — |

---

## 🚀 Démarrage Rapide

### Prérequis
- Java 21+
- Node.js 22+
- Docker & Docker Compose
- Maven 3.9+

### Développement Local (H2 en mémoire)

```bash
# 1. Cloner le projet
git clone https://github.com/slimissa/BNA-Flux.git
cd BNA-Flux

# 2. Terminal 1 — Backend
cd backend
mvn spring-boot:run

# 3. Terminal 2 — Frontend
cd frontend
npm install
ng serve --proxy-config proxy.conf.json --open

# 4. Ouvrir http://localhost:4200
```

### Docker (PostgreSQL)

```bash
# Démarrer tous les services
docker compose up -d

# Ouvrir http://localhost
```

### Arrêt

```bash
docker compose down
```

---

## Utilisateurs de Test

| Rôle | Email | Mot de passe | Permissions |
|------|-------|-------------|------------|
| **Admin** | admin@bna.com.tn | BnaFlux2026! | Tous les droits, toutes les agences |
| **Superviseur** | superviseur@bna.com.tn | BnaFlux2026! | CRUD règles, réinitialisation disjoncteurs |
| **Opérateur** | operateur@bna.com.tn | BnaFlux2026! | Consultation, acquittement alertes |

---

## API Endpoints

### Authentification
| Méthode | Endpoint | Description | Auth |
|---------|----------|-------------|------|
| POST | `/api/auth/connexion` | Login | Non |
| POST | `/api/auth/rafraichir` | Rafraîchir token | Non |
| POST | `/api/auth/deconnexion` | Déconnexion | Non |

### Transactions
| Méthode | Endpoint | Description | Rôle |
|---------|----------|-------------|------|
| GET | `/api/transactions` | Liste avec filtres | Tous |
| POST | `/api/transactions` | Soumettre au pipeline | Tous |
| GET | `/api/transactions/{id}` | Détail | Tous |
| GET | `/api/transactions/{id}/piste-audit` | Piste d'audit | Tous |
| GET | `/api/transactions/{id}/piste-audit/verifier` | Vérifier intégrité | Tous |
| GET | `/api/transactions/{id}/alertes` | Alertes liées | Tous |

### Règles
| Méthode | Endpoint | Description | Rôle |
|---------|----------|-------------|------|
| GET | `/api/regles` | Liste | Tous |
| POST | `/api/regles` | Créer | SUPERVISEUR+ |
| PUT | `/api/regles/{id}` | Modifier | SUPERVISEUR+ |
| DELETE | `/api/regles/{id}` | Supprimer | SUPERVISEUR+ |
| PUT | `/api/regles/{id}/basculer` | Activer/Désactiver | SUPERVISEUR+ |
| POST | `/api/regles/tester` | Tester expression SpEL | SUPERVISEUR+ |

### Alertes
| Méthode | Endpoint | Description | Rôle |
|---------|----------|-------------|------|
| GET | `/api/alertes` | Liste avec filtres | Tous |
| PUT | `/api/alertes/{id}/acquitter` | Acquitter | Tous |

### Disjoncteurs
| Méthode | Endpoint | Description | Rôle |
|---------|----------|-------------|------|
| GET | `/api/disjoncteurs` | Liste | Tous |
| GET | `/api/disjoncteurs/{id}` | Détail | Tous |
| PUT | `/api/disjoncteurs/{id}/reinitialiser` | Réinitialiser | SUPERVISEUR+ |

### Devises (Public)
| Méthode | Endpoint | Description | Auth |
|---------|----------|-------------|------|
| GET | `/api/devises` | Liste des 17 devises | Non |
| GET | `/api/devises/{code}` | Détail devise | Non |

### Tableau de Bord
| Méthode | Endpoint | Description | Rôle |
|---------|----------|-------------|------|
| GET | `/api/tableau-bord/resume` | Résumé complet | Tous |
| GET | `/api/tableau-bord/tendance` | Tendance | Tous |
| GET | `/api/tableau-bord/statistiques` | Stats rapides | Tous |

### Export PDF
| Méthode | Endpoint | Description | Rôle |
|---------|----------|-------------|------|
| GET | `/api/transactions/{id}/export-pdf` | Rapport PDF complet | Tous |

### Documentation
| Endpoint | Description |
|----------|-------------|
| `/swagger-ui.html` | Interface Swagger interactive |
| `/api-docs` | Spécification OpenAPI 3.0 |
| `/actuator/health` | Health check |
| `/h2-console` | Console H2 (dev uniquement) |

### Notifications WebSocket
| Endpoint | Description |
|----------|-------------|
| `/ws` | Endpoint STOMP (SockJS fallback) |
| `/topic/alertes` | Canal d'alertes en temps réel |
| `/topic/disjoncteurs` | Canal de changement d'état disjoncteurs |

---

## ⚙ Pipeline de Traitement

Chaque transaction soumise traverse 5 étapes séquentielles :

```
Transaction → Stage 1 → Stage 2 → Stage 3 → Stage 4 → Stage 5 → Résultat
                │          │          │          │          │
           Validation  Enrichis-  Évaluation  Notation  Persistance
           RIB mod 97  sement     Règles SpEL Score      Audit SHA-256
           Devise      Pays       (10 règles) Statut     Alertes
           Disjoncteur Contre-                 Disjonct.  Emails
                       partie
```

### Stage 1 — Validation
- RIB tunisien : format 20 chiffres, clé modulo 97
- Devise : code ISO 4217 valide et actif
- Montant : > 0, décimales cohérentes avec la devise
- Disjoncteurs : vérification pour source, destination, agence, canal

### Stage 2 — Enrichissement
- Pays d'origine : déterminé à partir de la devise et des RIBs
- Catégorie contrepartie : PARTICULIER, ENTREPRISE, GOUVERNEMENT

### Stage 3 — Évaluation des Règles
- 10 règles SpEL évaluées séquentiellement
- Expressions compilées avec cache pour performance
- Accumulation du score de risque

### Stage 4 — Notation
- Score 0-29 → ACCEPTE
- Score 30-70 → SURVEILLE
- Score 71-100 → BLOQUE
- Enregistrement des échecs dans les disjoncteurs

### Stage 5 — Persistance
- Sauvegarde en base de données
- Génération des alertes
- Création des entrées d'audit SHA-256 chaînées
- Envoi d'emails (CRITIQUE : immédiat, ELEVE : groupé)

---

## Moteur de Règles SpEL

Les règles utilisent Spring Expression Language pour évaluer dynamiquement les transactions.

### Variables disponibles
| Variable | Type | Description |
|----------|------|-------------|
| `montant` | BigDecimal | Montant de la transaction |
| `codeDevise` | String | Code ISO 4217 (TND, EUR, USD...) |
| `typeTransaction` | String | VIREMENT, CHEQUE, ESPECES, CARTE, PRELEVEMENT |
| `canal` | String | AGENCE, DAB, EN_LIGNE, MOBILE |
| `paysOrigine` | String | Pays d'origine (après enrichissement) |
| `categorieContrepartie` | String | PARTICULIER, ENTREPRISE, GOUVERNEMENT |
| `ribSource` | String | RIB émetteur (20 chiffres) |
| `ribDestination` | String | RIB bénéficiaire (20 chiffres) |

### Opérateurs supportés
| Opérateur | Exemple |
|-----------|---------|
| `==`, `!=`, `<`, `>`, `<=`, `>=` | `montant >= 50000` |
| `AND`, `OR`, `NOT` | `montant >= 50000 AND codeDevise != 'TND'` |
| `IN` | `canal IN {'EN_LIGNE', 'MOBILE'}` |
| `!= null`, `== null` | `paysOrigine != null` |
| `matches` | `ribSource matches '^[0-9]{20}$'` |

### Règles par défaut
| # | Nom | Sévérité | Score | Type |
|---|-----|----------|-------|------|
| 1 | Virement international ≥ 50 000 TND | ELEVE | 30 | ALERTE |
| 2 | Dépôt en espèces ≥ 10 000 TND hors agence | ELEVE | 30 | ALERTE |
| 3 | Virement en ligne ≥ 5 000 TND | MOYEN | 15 | ALERTE |
| 4 | Réception étranger ≥ 20 000 TND | MOYEN | 20 | ALERTE |
| 5 | Transaction mobile ≥ 3 000 TND | MOYEN | 15 | PREVENTION |
| 6 | Blocage virement ≥ 100 000 TND étranger | CRITIQUE | 50 | AUTO_REJET |
| 7 | Chèque ≥ 50 000 TND | MOYEN | 15 | ALERTE |
| 8 | Carte en ligne ≥ 8 000 TND | ELEVE | 25 | ALERTE |
| 9 | Prélèvement ≥ 10 000 TND | MOYEN | 15 | PREVENTION |
| 10 | Retrait DAB ≥ 2 000 TND espèces | FAIBLE | 10 | PREVENTION |

---

## Piste d'Audit SHA-256

Chaque étape du pipeline génère une entrée d'audit immuable. Les entrées sont chaînées par hachage cryptographique.

### Formule de hachage
```
hashCourant = SHA-256(
    hashPrecedent + "|" +
    transactionId + "|" +
    etape + "|" +
    action + "|" +
    detail + "|" +
    horodatage + "|" +
    operateur
)
```

### Vérification d'intégrité
- Endpoint : `GET /api/transactions/{id}/piste-audit/verifier`
- Recalcule chaque hash et compare avec la valeur stockée
- Détecte toute modification ou suppression d'entrée
- Retourne le détail de chaque entrée avec statut de vérification

---

## ⚡ Circuit Breaker (Disjoncteur)

Protection automatique contre les anomalies. Quand un compte, une agence ou un canal génère un nombre anormal de transactions bloquées, le disjoncteur s'ouvre.

### Cycle de vie
```
FERMÉ ──(nb échecs ≥ seuil)──▶ OUVERT ──(délai écoulé)──▶ MI_OUVERT
  ▲                                                          │
  └────────────(test réussi)─────────────────────────────────┘
  └────────────(test échoué)──▶ OUVERT (retour)
```

### Types de cibles
- COMPTE_SOURCE : RIB émetteur
- COMPTE_DESTINATION : RIB bénéficiaire
- AGENCE : Code agence (3 chiffres)
- CANAL : AGENCE, DAB, EN_LIGNE, MOBILE

---

## Structure du Projet

```
BNA-Flux/
├── backend/                          # Application Spring Boot
│   ├── src/main/java/com/bna/flux/
│   │   ├── config/                   # Security, JWT, SpEL, Async, CORS
│   │   ├── controller/               # Contrôleurs REST (7)
│   │   ├── dto/                      # Data Transfer Objects (12)
│   │   ├── entity/                   # Entités JPA (7)
│   │   ├── exception/                # Exceptions personnalisées (6)
│   │   ├── repository/               # Repositories Spring Data (7)
│   │   └── service/                  # Services métier (14)
│   │       └── pipeline/             # Pipeline 5 étapes
│   │           └── etape/            # Validation, Enrichissement, etc.
│   ├── src/main/resources/
│   │   ├── application.yml           # Configuration principale
│   │   ├── application-dev.yml       # Profil développement (H2)
│   │   ├── application-docker.yml    # Profil Docker (PostgreSQL)
│   │   ├── devises.json              # 17 devises ISO 4217
│   │   └── regles-par-defaut.json    # 10 règles par défaut
│   ├── src/test/                     # Tests unitaires et intégration
│   ├── Dockerfile                    # Build multi-stage Maven + JRE
│   └── pom.xml
├── frontend/                         # Application Angular 18
│   ├── src/app/
│   │   ├── core/                     # Intercepteurs, gardes, services
│   │   ├── modeles/                  # Interfaces TypeScript (7)
│   │   └── pages/                    # Pages (7 × 3 fichiers)
│   │       ├── connexion/
│   │       ├── tableau-bord/
│   │       ├── transactions/
│   │       │   ├── liste-transactions/
│   │       │   └── detail-transaction/
│   │       ├── devises/
│   │       ├── disjoncteurs/
│   │       └── regles/
│   ├── Dockerfile                    # Build multi-stage Node + Nginx
│   ├── nginx.conf                    # Configuration Nginx (proxy API)
│   ├── proxy.conf.json               # Proxy développement Angular
│   └── angular.json
├── database/
│   └── init.sql                      # Schéma PostgreSQL + seeds
├── docker-compose.yml                # Orchestration 3 services
├── README.md                         # Documentation
└── .gitignore
```

---

## Déploiement Docker

### Services
| Service | Image | Port | Description |
|---------|-------|------|-------------|
| postgres | postgres:16-alpine | 5433 | Base de données |
| backend | bna-flux-backend:1.0.0 | 8080 | API Spring Boot |
| frontend | bna-flux-frontend:1.0.0 | 80 | Interface Angular + Nginx |

### Commandes Docker
```bash
# Démarrer
docker compose up -d

# Reconstruire après modifications
docker compose up -d --build

# Voir les logs
docker compose logs -f

# Arrêter
docker compose down

# Arrêter et supprimer les volumes
docker compose down -v
```

### Variables d'environnement
| Variable | Défaut | Description |
|----------|--------|-------------|
| POSTGRES_DB | bnaflux | Nom de la base |
| POSTGRES_USER | bnaflux | Utilisateur |
| POSTGRES_PASSWORD | bnaflux2026 | Mot de passe |
| POSTGRES_PORT | 5433 | Port exposé |
| BNA_JWT_SECRET | (auto) | Clé secrète JWT |
| FRONTEND_URL | http://localhost | URL pour CORS |
| SMTP_HOST | sandbox.smtp.mailtrap.io | Serveur SMTP |
| SMTP_PORT | 2525 | Port SMTP |
| SMTP_USERNAME | (vide) | Utilisateur SMTP |
| SMTP_PASSWORD | (vide) | Mot de passe SMTP |

---

## Tests

```bash
# Backend
cd backend
mvn test

# Frontend
cd frontend
ng test
```

### Couverture
- ✅ ValidateurRib — 41 tests (algorithme modulo 97)
- ✅ ServiceDisjoncteur — 29 tests (transitions d'état)
- ✅ ServiceAudit — 12 tests (chaîne de hachage SHA-256)
- ✅ MoteurPipeline — 19 tests (orchestration 5 étapes)
- ✅ PipelineIntegrationTest — 3 tests (flux complet API)
- ✅ WebSocketNotificationTest — 4 tests (notifications temps réel)
- ✅ **Total : 108 tests, 0 échecs**

---

## Soumettre une Transaction (cURL)

```bash
# 1. Obtenir un token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/connexion \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@bna.com.tn","motDePasse":"BnaFlux2026!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['tokenAcces'])")

# 2. Soumettre une transaction
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "ribSource": "08601000191000748054",
    "ribDestination": "01601123456789012359",
    "montant": 75000.00,
    "codeDevise": "EUR",
    "typeTransaction": "VIREMENT",
    "canal": "EN_LIGNE",
    "dateTransaction": "2026-08-06T10:00:00",
    "description": "Paiement fournisseur"
  }'

# 3. Vérifier la piste d'audit
curl -s "http://localhost:8080/api/transactions/1/piste-audit/verifier" \
  -H "Authorization: Bearer $TOKEN"
```

---

## Auteur

**Slim Issa** — Stagiaire BNA 2026

- **GitHub** : [github.com/slimissa](https://github.com/slimissa)
- **Projets liés** :
  - [Las_shell](https://github.com/slimissa/Las_shell) — Shell de trading quantitatif en C
  - [Market Data Handling](https://github.com/slimissa/market-data-handling) — Pipeline de données financières en Python
  - [ISO 4217 Registry](https://github.com/slimissa/iso4217) — Registre des devises ISO 4217

---

## Licence

Apache License 2.0 — voir le fichier [LICENSE](LICENSE) pour plus de détails.

---

**BNA-FLUX v1.0.0** — Août 2026
