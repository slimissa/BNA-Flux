# ============================================================
# BNA-FLUX — Frontend Dockerfile
# ============================================================
# Stage 1 : Build Angular avec Node.js 22
# Stage 2 : Serve avec Nginx Alpine
# ============================================================

# Stage 1 — Build Angular
FROM node:22-alpine AS build

WORKDIR /app

# Copier les fichiers de dépendances en premier (cache Docker)
COPY package.json package-lock.json ./

# Installer les dépendances avec npm ci (reproductible, plus rapide)
# --legacy-peer-deps pour éviter les conflits avec Angular 18
RUN npm ci --legacy-peer-deps --quiet

# Copier tout le code source
COPY . .

# Build Angular en mode production
# La configuration production est définie dans angular.json
RUN ./node_modules/.bin/ng build --configuration=production

# Vérifier que le build a bien généré les fichiers
RUN ls -la dist/bna-flux-frontend/browser/index.html

# Stage 2 — Serveur Nginx
FROM nginx:alpine

# Métadonnées de l'image
LABEL maintainer="Slim Issa — Stage BNA 2026" \
      application="BNA-FLUX Frontend" \
      version="1.0.0" \
      description="Interface Angular de surveillance des transactions bancaires"

# Créer un utilisateur non-root pour Nginx
RUN addgroup -g 1000 bnaflux && \
    adduser -u 1000 -G bnaflux -s /bin/sh -D bnaflux

# Créer les répertoires nécessaires
RUN mkdir -p /var/log/nginx /var/cache/nginx /usr/share/nginx/html && \
    chown -R bnaflux:bnaflux /var/log/nginx /var/cache/nginx /usr/share/nginx/html

# Copier les fichiers buildés depuis le stage de build
COPY --from=build /app/dist/bna-flux-frontend/browser /usr/share/nginx/html

# Copier la configuration Nginx personnalisée
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Vérifier la configuration Nginx

# Exposer le port HTTP
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=10s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

# Utilisateur non-root

# Démarrer Nginx en foreground
CMD ["nginx", "-g", "daemon off;"]