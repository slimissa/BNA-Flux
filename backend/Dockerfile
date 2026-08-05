# ============================================================
# BNA-FLUX — Dockerfile multi-stage
# ============================================================
# Stage 1 : Build avec Maven 3.9 + JDK 21 Temurin
# Stage 2 : Runtime avec JRE 21 Temurin minimal
#
# Construction :
#   docker build -t bna-flux:1.0.0 .
#
# Exécution :
#   docker run -p 8080:8080 \
#     -e POSTGRES_HOST=postgres \
#     -e POSTGRES_DB=bnaflux \
#     -e POSTGRES_USER=bnaflux \
#     -e POSTGRES_PASSWORD=bnaflux2026 \
#     -e BNA_JWT_SECRET=ma-cle-secrete-d-au-moins-256-bits \
#     bna-flux:1.0.0
# ============================================================

# Stage 1 — Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copier le POM en premier pour profiter du cache Docker
# Les dépendances ne sont re-téléchargées que si le POM change
COPY pom.xml .

# Télécharger toutes les dépendances (phase de résolution)
# -B : mode batch (non interactif)
# -q : quiet (réduit le bruit dans les logs Docker)
RUN mvn dependency:go-offline -B -q

# Copier tout le code source
# On copie après le POM pour ne pas invalider le cache des dépendances
COPY src ./src

# Compiler et packager l'application
# -DskipTests : on saute les tests dans le build Docker
#   (les tests sont exécutés séparément en CI/CD)
RUN mvn package -DskipTests -B -q

# Vérifier que le JAR a bien été généré
RUN ls -la target/bna-flux.jar

# Stage 2 — Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Métadonnées de l'image
LABEL maintainer="Slim Issa — Stage BNA 2026" \
      application="BNA-FLUX" \
      version="1.0.0" \
      description="Système de surveillance des transactions bancaires en temps réel"

# Sécurité — Utilisateur non-root
RUN addgroup -g 1000 bnaflux && \
    adduser -u 1000 -G bnaflux -s /bin/sh -D bnaflux

# Répertoires
# Logs persistants (montables en volume)
RUN mkdir -p /var/log/bna-flux && \
    chown -R bnaflux:bnaflux /var/log/bna-flux

# Répertoire des ressources JSON
RUN mkdir -p /app/resources && \
    chown -R bnaflux:bnaflux /app

# Copie du JAR et des ressources
# JAR exécutable depuis le stage de build
COPY --from=build /app/target/bna-flux.jar app.jar

# Fichiers JSON de configuration (devises, règles par défaut)
# Ces fichiers sont inclus dans le JAR via src/main/resources,
# mais on les copie aussi à l'extérieur pour permettre
# de les surcharger facilement sans rebuild
COPY --from=build /app/target/classes/devises.json /app/resources/devises.json
COPY --from=build /app/target/classes/regles-par-defaut.json /app/resources/regles-par-defaut.json

# Port exposé
EXPOSE 8080

# Health Check
# Vérifie que l'application répond sur le endpoint Actuator health
# Intervalle : 30 secondes
# Timeout : 5 secondes
# Retries : 3 avant de marquer unhealthy
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Utilisateur non-root
USER bnaflux

# Point d'entrée
# Options JVM :
#   -XX:+UseContainerSupport : Détection des limites du conteneur
#   -XX:MaxRAMPercentage=75.0 : Utilise max 75% de la RAM du conteneur
#   -Djava.security.egd=file:/dev/./urandom : Entropie plus rapide
#   -Dspring.profiles.active=docker : Active le profil Docker
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=docker", \
    "-jar", "app.jar"]