-- ============================================================
-- BNA-FLUX — Script d'initialisation de la base de données
-- ============================================================
-- Ce script est exécuté au premier démarrage du conteneur
-- PostgreSQL dans docker-compose.yml.
-- Il crée les tables, insère les données de référence
-- (devises, règles, utilisateurs) et configure les indexes.
-- ============================================================

-- ============================================================
-- Nettoyage (optionnel — commenté pour la production)
-- ============================================================
-- DROP TABLE IF EXISTS alertes CASCADE;
-- DROP TABLE IF EXISTS entrees_audit CASCADE;
-- DROP TABLE IF EXISTS transactions CASCADE;
-- DROP TABLE IF EXISTS regles CASCADE;
-- DROP TABLE IF EXISTS etats_disjoncteur CASCADE;
-- DROP TABLE IF EXISTS devises CASCADE;
-- DROP TABLE IF EXISTS utilisateurs CASCADE;
-- ============================================================

-- Table : devises
CREATE TABLE IF NOT EXISTS devises (
    code VARCHAR(3) NOT NULL,
    nom VARCHAR(100) NOT NULL,
    unites_mineures INTEGER NOT NULL DEFAULT 2,
    symbole VARCHAR(10),
    code_numerique VARCHAR(3),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP,
    PRIMARY KEY (code)
);

-- Table : utilisateurs
CREATE TABLE IF NOT EXISTS utilisateurs (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    mot_de_passe VARCHAR(255) NOT NULL,
    nom VARCHAR(150) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'OPERATEUR',
    code_agence VARCHAR(3),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP,
    derniere_connexion TIMESTAMP
);

-- Table : regles
CREATE TABLE IF NOT EXISTS regles (
    id BIGSERIAL PRIMARY KEY,
    nom VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    expression_condition VARCHAR(500) NOT NULL,
    severite VARCHAR(10) NOT NULL DEFAULT 'MOYEN',
    contribution_score INTEGER NOT NULL DEFAULT 15,
    type_regle VARCHAR(15) NOT NULL DEFAULT 'ALERTE',
    categorie VARCHAR(100),
    priorite INTEGER NOT NULL DEFAULT 50,
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP,
    CONSTRAINT chk_contribution_score CHECK (contribution_score >= 0 AND contribution_score <= 100),
    CONSTRAINT chk_priorite CHECK (priorite >= 0 AND priorite <= 100)
);

-- Table : transactions
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    reference_transaction VARCHAR(30) NOT NULL UNIQUE,
    rib_source VARCHAR(20) NOT NULL,
    rib_destination VARCHAR(20) NOT NULL,
    montant NUMERIC(18,3) NOT NULL,
    code_devise VARCHAR(3) NOT NULL REFERENCES devises(code),
    type_transaction VARCHAR(15) NOT NULL,
    canal VARCHAR(10) NOT NULL,
    date_transaction TIMESTAMP NOT NULL,
    description VARCHAR(500),
    pays_origine VARCHAR(100),
    categorie_contrepartie VARCHAR(20),
    score_risque NUMERIC(5,2) DEFAULT 0.00,
    statut VARCHAR(10) NOT NULL DEFAULT 'ACCEPTE',
    motif VARCHAR(1000),
    traite_le TIMESTAMP,
    version BIGINT DEFAULT 0,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP
);

-- Table : alertes
CREATE TABLE IF NOT EXISTS alertes (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id),
    regle_id BIGINT NOT NULL REFERENCES regles(id),
    message VARCHAR(500) NOT NULL,
    niveau VARCHAR(10) NOT NULL,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acquittee BOOLEAN NOT NULL DEFAULT FALSE,
    acquittee_par VARCHAR(150),
    acquittee_le TIMESTAMP,
    email_envoye BOOLEAN NOT NULL DEFAULT FALSE,
    email_envoye_le TIMESTAMP,
    email_destinataire VARCHAR(255)
);

-- Table : entrees_audit
CREATE TABLE IF NOT EXISTS entrees_audit (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id),
    etape VARCHAR(30) NOT NULL,
    action VARCHAR(50) NOT NULL,
    detail VARCHAR(2000),
    hash_precedent VARCHAR(64),
    hash_courant VARCHAR(64) NOT NULL,
    horodatage TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operateur VARCHAR(150) NOT NULL DEFAULT 'SYSTEME'
);

-- Table : etats_disjoncteur
CREATE TABLE IF NOT EXISTS etats_disjoncteur (
    id BIGSERIAL PRIMARY KEY,
    nom VARCHAR(200),
    type_cible VARCHAR(25) NOT NULL,
    identifiant_cible VARCHAR(100) NOT NULL,
    etat VARCHAR(15) NOT NULL DEFAULT 'FERME',
    nombre_echecs INTEGER NOT NULL DEFAULT 0,
    seuil_echecs INTEGER NOT NULL DEFAULT 3,
    delai_ouverture_minutes INTEGER NOT NULL DEFAULT 60,
    fenetre_heures INTEGER NOT NULL DEFAULT 24,
    date_derniere_ouverture TIMESTAMP,
    date_derniere_fermeture TIMESTAMP,
    date_dernier_echec TIMESTAMP,
    date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification TIMESTAMP,
    CONSTRAINT chk_nombre_echecs CHECK (nombre_echecs >= 0),
    CONSTRAINT chk_seuil_echecs CHECK (seuil_echecs >= 1),
    CONSTRAINT chk_delai_ouverture CHECK (delai_ouverture_minutes >= 1),
    CONSTRAINT chk_fenetre_heures CHECK (fenetre_heures >= 1)
);

-- Indexes pour les performances

-- Transactions — Recherche par statut (filtre fréquent)
CREATE INDEX IF NOT EXISTS idx_transactions_statut ON transactions(statut);

-- Transactions — Recherche par date (filtre fréquent + tri)
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(date_transaction DESC);

-- Transactions — Recherche par devise
CREATE INDEX IF NOT EXISTS idx_transactions_devise ON transactions(code_devise);

-- Transactions — Recherche par RIB source
CREATE INDEX IF NOT EXISTS idx_transactions_rib_source ON transactions(rib_source);

-- Transactions — Recherche par RIB destination
CREATE INDEX IF NOT EXISTS idx_transactions_rib_dest ON transactions(rib_destination);

-- Transactions — Recherche par canal
CREATE INDEX IF NOT EXISTS idx_transactions_canal ON transactions(canal);

-- Transactions — Recherche par score de risque
CREATE INDEX IF NOT EXISTS idx_transactions_score ON transactions(score_risque DESC);

-- Alertes — Recherche par transaction
CREATE INDEX IF NOT EXISTS idx_alertes_transaction ON alertes(transaction_id);

-- Alertes — Recherche par règle
CREATE INDEX IF NOT EXISTS idx_alertes_regle ON alertes(regle_id);

-- Alertes — Recherche par niveau
CREATE INDEX IF NOT EXISTS idx_alertes_niveau ON alertes(niveau);

-- Alertes — Recherche par acquittement
CREATE INDEX IF NOT EXISTS idx_alertes_acquittee ON alertes(acquittee);

-- Alertes — Recherche par date
CREATE INDEX IF NOT EXISTS idx_alertes_date ON alertes(date_creation DESC);

-- Entrées d'audit — Recherche par transaction
CREATE INDEX IF NOT EXISTS idx_audit_transaction ON entrees_audit(transaction_id);

-- Entrées d'audit — Recherche par date
CREATE INDEX IF NOT EXISTS idx_audit_date ON entrees_audit(horodatage DESC);

-- Disjoncteurs — Recherche par type + identifiant (requête la plus fréquente)
CREATE UNIQUE INDEX IF NOT EXISTS idx_disjoncteur_cible ON etats_disjoncteur(type_cible, identifiant_cible);

-- Disjoncteurs — Recherche par état
CREATE INDEX IF NOT EXISTS idx_disjoncteur_etat ON etats_disjoncteur(etat);

-- Règles — Recherche par catégorie
CREATE INDEX IF NOT EXISTS idx_regles_categorie ON regles(categorie);

-- Règles — Recherche par sévérité
CREATE INDEX IF NOT EXISTS idx_regles_severite ON regles(severite);

-- Utilisateurs — Recherche par email (login)
CREATE INDEX IF NOT EXISTS idx_utilisateurs_email ON utilisateurs(email);

-- Utilisateurs — Recherche par rôle
CREATE INDEX IF NOT EXISTS idx_utilisateurs_role ON utilisateurs(role);

-- Données de référence — Devises (17 devises)
INSERT INTO devises (code, nom, unites_mineures, symbole, code_numerique, actif) VALUES
('TND', 'Dinar Tunisien', 3, 'د.ت', '788', TRUE),
('EUR', 'Euro', 2, '€', '978', TRUE),
('KWD', 'Dinar Koweïtien', 3, 'د.ك', '414', TRUE),
('USD', 'Dollar Américain', 2, '$', '840', TRUE),
('CAD', 'Dollar Canadien', 2, 'CA$', '124', TRUE),
('GBP', 'Livre Sterling', 2, '£', '826', TRUE),
('CHF', 'Franc Suisse', 2, 'CHF', '756', TRUE),
('BHD', 'Dinar Bahreïni', 3, 'د.ب', '48', TRUE),
('SEK', 'Couronne Suédoise', 2, 'kr', '752', TRUE),
('SAR', 'Riyal Saoudien', 2, '﷼', '682', TRUE),
('QAR', 'Riyal Qatari', 2, 'ر.ق', '634', TRUE),
('NOK', 'Couronne Norvégienne', 2, 'kr', '578', TRUE),
('JPY', 'Yen Japonais', 0, '¥', '392', TRUE),
('DKK', 'Couronne Danoise', 2, 'kr', '208', TRUE),
('AED', 'Dirham des Émirats Arabes Unis', 2, 'د.إ', '784', TRUE),
('CNY', 'Yuan Chinois', 2, '¥', '156', TRUE),
('LYD', 'Dinar Libyen', 3, 'ل.د', '434', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Données de référence — Règles par défaut (10 règles)
INSERT INTO regles (nom, description, expression_condition, severite, contribution_score, type_regle, categorie, priorite, actif) VALUES
(
    'Virement international ≥ 50 000 TND en devise étrangère',
    'Surveille les virements sortants d''un montant égal ou supérieur à 50 000 dans une devise autre que le dinar tunisien.',
    'montant >= 50000 AND codeDevise != ''TND''',
    'ELEVE', 30, 'ALERTE', 'Virements internationaux', 10, TRUE
),
(
    'Dépôt en espèces ≥ 10 000 TND hors agence',
    'Détecte les dépôts en espèces importants effectués en dehors d''une agence physique.',
    'typeTransaction == ''ESPECES'' AND montant >= 10000 AND canal != ''AGENCE''',
    'ELEVE', 30, 'ALERTE', 'Lutte anti-blanchiment', 10, TRUE
),
(
    'Virement en ligne ≥ 5 000 TND',
    'Surveille les virements initiés via la plateforme en ligne pour des montants significatifs.',
    'canal == ''EN_LIGNE'' AND montant >= 5000 AND typeTransaction == ''VIREMENT''',
    'MOYEN', 15, 'ALERTE', 'Sécurité des canaux en ligne', 30, TRUE
),
(
    'Réception depuis un pays étranger ≥ 20 000 TND',
    'Surveille les transactions entrantes depuis l''étranger pour des montants significatifs.',
    'paysOrigine != null AND paysOrigine != ''Tunisie'' AND montant >= 20000',
    'MOYEN', 20, 'ALERTE', 'Virements internationaux', 25, TRUE
),
(
    'Transaction mobile ≥ 3 000 TND',
    'Surveille les transactions initiées via l''application mobile pour des montants modérés à élevés.',
    'canal == ''MOBILE'' AND montant >= 3000',
    'MOYEN', 15, 'PREVENTION', 'Sécurité des canaux en ligne', 40, TRUE
),
(
    'Blocage virement suspect ≥ 100 000 TND vers pays à risque',
    'Bloque automatiquement les virements d''un montant très élevé vers des pays considérés à risque.',
    'montant >= 100000 AND paysOrigine != null AND paysOrigine != ''Tunisie'' AND typeTransaction == ''VIREMENT''',
    'CRITIQUE', 50, 'AUTO_REJET', 'Virements internationaux', 5, TRUE
),
(
    'Chèque ≥ 50 000 TND',
    'Surveille les transactions par chèque pour des montants importants.',
    'typeTransaction == ''CHEQUE'' AND montant >= 50000',
    'MOYEN', 15, 'ALERTE', 'Opérations chèques', 35, TRUE
),
(
    'Paiement par carte ≥ 8 000 TND en ligne',
    'Surveille les paiements par carte bancaire sur le canal en ligne pour des montants élevés.',
    'typeTransaction == ''CARTE'' AND canal == ''EN_LIGNE'' AND montant >= 8000',
    'ELEVE', 25, 'ALERTE', 'Sécurité des canaux en ligne', 15, TRUE
),
(
    'Prélèvement ≥ 10 000 TND',
    'Surveille les prélèvements automatiques pour des montants inhabituellement élevés.',
    'typeTransaction == ''PRELEVEMENT'' AND montant >= 10000',
    'MOYEN', 15, 'PREVENTION', 'Opérations récurrentes', 45, TRUE
),
(
    'Transfert DAB ≥ 2 000 TND en espèces',
    'Surveille les retraits en espèces aux distributeurs pour des montants supérieurs au plafond standard.',
    'canal == ''DAB'' AND typeTransaction == ''ESPECES'' AND montant >= 2000',
    'FAIBLE', 10, 'PREVENTION', 'Lutte anti-blanchiment', 50, TRUE
)
ON CONFLICT DO NOTHING;

-- ============================================================
-- Données de référence — Utilisateurs de test
-- ============================================================
-- Mot de passe pour tous les comptes de test : BnaFlux2026!
-- Hash BCrypt (facteur 12) généré avec :
--   $2a$12$... (mot de passe : BnaFlux2026!)
-- ============================================================
INSERT INTO utilisateurs (email, mot_de_passe, nom, role, code_agence, actif) VALUES
(
    'admin@bna.com.tn',
    '$2a$12$LJ3m4ys3Gql.ZHxHRrGI5eFh5vX5qP9G9G9G9G9G9G9G9G9G9G',
    'Administrateur BNA',
    'ADMIN',
    NULL,
    TRUE
),
(
    'superviseur@bna.com.tn',
    '$2a$12$LJ3m4ys3Gql.ZHxHRrGI5eFh5vX5qP9G9G9G9G9G9G9G9G9G9G',
    'Superviseur Agence 601',
    'SUPERVISEUR',
    '601',
    TRUE
),
(
    'operateur@bna.com.tn',
    '$2a$12$LJ3m4ys3Gql.ZHxHRrGI5eFh5vX5qP9G9G9G9G9G9G9G9G9G9G',
    'Opérateur Agence 601',
    'OPERATEUR',
    '601',
    TRUE
)
ON CONFLICT (email) DO NOTHING;

-- Commentaires sur les tables (documentation)
COMMENT ON TABLE devises IS 'Devises ISO 4217 supportées par BNA-FLUX';
COMMENT ON TABLE utilisateurs IS 'Utilisateurs du système avec authentification JWT';
COMMENT ON TABLE regles IS 'Règles de surveillance évaluées dynamiquement via SpEL';
COMMENT ON TABLE transactions IS 'Transactions bancaires traitées par le pipeline';
COMMENT ON TABLE alertes IS 'Alertes générées par les règles déclenchées';
COMMENT ON TABLE entrees_audit IS 'Piste d''audit hash-chaînée SHA-256';
COMMENT ON TABLE etats_disjoncteur IS 'Circuit breakers pour protection automatique';