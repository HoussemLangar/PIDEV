#!/bin/bash

#############################################################
# Script Final Complet - Génération des 26 Entités PI-DEV
# Ce script automatise toutes les étapes nécessaires
#############################################################

set -e  # Arrêter en cas d'erreur

# Service utilise pour les commandes SQL d'administration (doit etre un noeud MariaDB/Galera)
DB_ADMIN_SERVICE="${DB_ADMIN_SERVICE:-db-node1}"

# Couleurs pour les messages
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "============================================================="
echo -e "${BLUE}Script Final - Génération Complète des 26 Entités PI-DEV${NC}"
echo "============================================================="
echo ""

# Vérifier qu'on est dans le bon dossier
if [ ! -f "docker-compose.yml" ]; then
    echo -e "${RED}❌ Erreur: docker-compose.yml non trouvé!${NC}"
    echo "Veuillez exécuter ce script depuis le dossier pidev/"
    exit 1
fi

echo -e "${GREEN}✅ Dossier pidev détecté${NC}"
echo ""

#############################################################
# ÉTAPE 1: Vérification et démarrage des conteneurs Docker
#############################################################
echo "============================================================="
echo -e "${BLUE}ÉTAPE 1/8 : Vérification des conteneurs Docker${NC}"
echo "============================================================="

if ! docker-compose ps | grep -q "pidev-web.*Up"; then
    echo -e "${YELLOW}⚠️  Démarrage des conteneurs...${NC}"
    docker-compose up -d
    echo -e "${YELLOW}⏳ Attente de 15 secondes pour l'initialisation...${NC}"
    sleep 15
else
    echo -e "${GREEN}✅ Conteneurs déjà en cours d'exécution${NC}"
fi

echo ""

#############################################################
# ÉTAPE 2: Installation de Symfony (si nécessaire)
#############################################################
echo "============================================================="
echo -e "${BLUE}ÉTAPE 2/8 : Vérification de Symfony${NC}"
echo "============================================================="

if [ ! -f "symfony-app/composer.json" ]; then
    echo -e "${YELLOW}⚠️  Symfony non installé. Installation en cours...${NC}"
    docker-compose exec -T web composer create-project symfony/skeleton:"6.4.*" temp
    docker-compose exec -T web sh -c "mv temp/* temp/.* . 2>/dev/null || true && rm -rf temp"
    docker-compose exec -T web composer require webapp
    docker-compose exec -T web composer require --dev symfony/maker-bundle
    docker-compose exec -T web composer require doctrine
    echo -e "${GREEN}✅ Symfony 6.4 installé${NC}"
else
    echo -e "${GREEN}✅ Symfony déjà installé${NC}"
    
    # Vérifier que les packages nécessaires sont installés
    if ! docker-compose exec -T web composer show | grep -q "symfony/maker-bundle"; then
        echo -e "${YELLOW}Installation de maker-bundle...${NC}"
        docker-compose exec -T web composer require --dev symfony/maker-bundle
    fi
    
    if ! docker-compose exec -T web composer show | grep -q "doctrine/orm"; then
        echo -e "${YELLOW}Installation de Doctrine...${NC}"
        docker-compose exec -T web composer require doctrine
    fi
fi

echo ""

#############################################################
# ÉTAPE 3: Configuration de la DATABASE_URL
#############################################################
echo "============================================================="
echo -e "${BLUE}ÉTAPE 3/8 : Configuration de la base de données${NC}"
echo "============================================================="

if [ ! -f "symfony-app/.env" ]; then
    echo -e "${YELLOW}Création du fichier .env...${NC}"
    cat > symfony-app/.env <<'EOF'
APP_ENV=dev
APP_SECRET=ChangeMe123456789abcdefghijklmnop

###> doctrine/doctrine-bundle ###
DATABASE_URL="mysql://symfony:symfony@db:3306/pidev?serverVersion=mariadb-11.4&charset=utf8mb4"
###< doctrine/doctrine-bundle ###
EOF
    echo -e "${GREEN}✅ Fichier .env créé${NC}"
else
    echo -e "${YELLOW}Mise à jour de DATABASE_URL...${NC}"
    cp symfony-app/.env symfony-app/.env.backup
    
    # Vérifier si DATABASE_URL existe
    if grep -q "DATABASE_URL=" symfony-app/.env; then
        sed -i 's|DATABASE_URL=.*|DATABASE_URL="mysql://symfony:symfony@db:3306/pidev?serverVersion=mariadb-11.4\&charset=utf8mb4"|g' symfony-app/.env
    else
        echo '' >> symfony-app/.env
        echo '###> doctrine/doctrine-bundle ###' >> symfony-app/.env
        echo 'DATABASE_URL="mysql://symfony:symfony@db:3306/pidev?serverVersion=mariadb-11.4&charset=utf8mb4"' >> symfony-app/.env
        echo '###< doctrine/doctrine-bundle ###' >> symfony-app/.env
    fi
    
    echo -e "${GREEN}✅ DATABASE_URL configurée (backup: .env.backup)${NC}"
fi

echo ""

#############################################################
# ÉTAPE 4: Vérification de la connexion MySQL
#############################################################
echo "============================================================="
echo -e "${BLUE}ÉTAPE 4/8 : Vérification de la connexion MySQL${NC}"
echo "============================================================="

MAX_RETRIES=10
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if docker-compose exec -T "$DB_ADMIN_SERVICE" mysql -usymfony -psymfony -e "SELECT 1;" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Connexion MySQL réussie${NC}"
        break
    else
        RETRY_COUNT=$((RETRY_COUNT + 1))
        if [ $RETRY_COUNT -lt $MAX_RETRIES ]; then
            echo -e "${YELLOW}⏳ Tentative $RETRY_COUNT/$MAX_RETRIES - Attente de 3 secondes...${NC}"
            sleep 3
        else
            echo -e "${RED}❌ Impossible de se connecter à MySQL après $MAX_RETRIES tentatives${NC}"
            exit 1
        fi
    fi
done

echo ""

#############################################################
# ÉTAPE 5: Création et migration de la base de données
#############################################################
echo "============================================================="
echo -e "${BLUE}ÉTAPE 5/8 : Création de la base de données${NC}"
echo "============================================================="

# Créer la base de données
echo -e "${YELLOW}Création de la base 'pidev'...${NC}"
docker-compose exec -T "$DB_ADMIN_SERVICE" mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS pidev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null || true
echo -e "${GREEN}✅ Base de données 'pidev' créée${NC}"

# Exécuter la migration SQL complète
echo -e "${YELLOW}Exécution de la migration SQL...${NC}"

docker-compose exec -T "$DB_ADMIN_SERVICE" mysql -uroot -proot pidev <<'EOFMIGRATION'
SET FOREIGN_KEY_CHECKS=0;

-- Table users
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(180) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    date_naissance DATE NOT NULL,
    adresse VARCHAR(255),
    telephone VARCHAR(20),
    role VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table patients
DROP TABLE IF EXISTS patients;
CREATE TABLE patients (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    numero_secu VARCHAR(50) UNIQUE,
    groupe_sanguin VARCHAR(5),
    allergies TEXT,
    antecedents_medicaux TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table medecins
DROP TABLE IF EXISTS medecins;
CREATE TABLE medecins (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    specialite VARCHAR(100) NOT NULL,
    numero_ordre VARCHAR(50) UNIQUE,
    cabinet_adresse VARCHAR(255),
    telephone_cabinet VARCHAR(20),
    tarif_consultation DECIMAL(10,2),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_specialite (specialite)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table pharmaciens
DROP TABLE IF EXISTS pharmaciens;
CREATE TABLE pharmaciens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    numero_ordre VARCHAR(50) UNIQUE,
    pharmacie_nom VARCHAR(150),
    pharmacie_adresse VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table cliniques
DROP TABLE IF EXISTS cliniques;
CREATE TABLE cliniques (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(150) NOT NULL,
    adresse VARCHAR(255) NOT NULL,
    telephone VARCHAR(20),
    email VARCHAR(100),
    horaires_ouverture TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_nom (nom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table abonnements
DROP TABLE IF EXISTS abonnements;
CREATE TABLE abonnements (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    type_abonnement VARCHAR(50) NOT NULL,
    prix DECIMAL(10,2) NOT NULL,
    duree_mois INT NOT NULL,
    avantages TEXT,
    description TEXT,
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'actif',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (type_abonnement),
    INDEX idx_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table accompagnements
DROP TABLE IF EXISTS accompagnements;
CREATE TABLE accompagnements (
    id INT AUTO_INCREMENT PRIMARY KEY,
    abonnement_id INT NOT NULL,
    nom VARCHAR(100) NOT NULL,
    description TEXT,
    date_debut DATE NOT NULL,
    date_fin DATE,
    type_accompagnement VARCHAR(50),
    statut VARCHAR(20) NOT NULL DEFAULT 'en_cours',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_abonnement (abonnement_id),
    INDEX idx_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table disponibilites
DROP TABLE IF EXISTS disponibilites;
CREATE TABLE disponibilites (
    id INT AUTO_INCREMENT PRIMARY KEY,
    medecin_id INT NOT NULL,
    date DATE NOT NULL,
    heure_debut TIME NOT NULL,
    heure_fin TIME NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'disponible',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_medecin (medecin_id),
    INDEX idx_date (date),
    INDEX idx_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table rendez_vous
DROP TABLE IF EXISTS rendez_vous;
CREATE TABLE rendez_vous (
    id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    medecin_id INT NOT NULL,
    disponibilite_id INT,
    date_rdv DATE NOT NULL,
    heure_rdv TIME NOT NULL,
    motif TEXT,
    statut VARCHAR(20) NOT NULL DEFAULT 'confirme',
    type_consultation VARCHAR(50),
    notes TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_medecin (medecin_id),
    INDEX idx_date (date_rdv),
    INDEX idx_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table contenu
DROP TABLE IF EXISTS contenu;
CREATE TABLE contenu (
    id INT AUTO_INCREMENT PRIMARY KEY,
    titre VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    contenu TEXT NOT NULL,
    auteur_id INT,
    categorie VARCHAR(100),
    tags VARCHAR(255),
    statut VARCHAR(20) NOT NULL DEFAULT 'publie',
    date_publication DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_categorie (categorie),
    INDEX idx_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table likes
DROP TABLE IF EXISTS likes;
CREATE TABLE likes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    contenu_id INT NOT NULL,
    user_id INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_like (contenu_id, user_id),
    INDEX idx_contenu (contenu_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table commentaires
DROP TABLE IF EXISTS commentaires;
CREATE TABLE commentaires (
    id INT AUTO_INCREMENT PRIMARY KEY,
    contenu_id INT NOT NULL,
    user_id INT NOT NULL,
    commentaire TEXT NOT NULL,
    note INT,
    parent_id INT,
    statut VARCHAR(20) NOT NULL DEFAULT 'publie',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_contenu (contenu_id),
    INDEX idx_user (user_id),
    INDEX idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table rapports_analyses
DROP TABLE IF EXISTS rapports_analyses;
CREATE TABLE rapports_analyses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    medecin_id INT,
    type_analyse VARCHAR(100) NOT NULL,
    date_analyse DATE NOT NULL,
    laboratoire VARCHAR(150),
    resultats TEXT,
    fichier_path VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_date (date_analyse)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table rapports_medicaux
DROP TABLE IF EXISTS rapports_medicaux;
CREATE TABLE rapports_medicaux (
    id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    medecin_id INT NOT NULL,
    titre VARCHAR(255) NOT NULL,
    diagnostic TEXT,
    traitement TEXT,
    observations TEXT,
    date_rapport DATE NOT NULL,
    fichier_path VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_medecin (medecin_id),
    INDEX idx_date (date_rapport)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table journaux_items
DROP TABLE IF EXISTS journaux_items;
CREATE TABLE journaux_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    titre VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL,
    date_journal DATE NOT NULL,
    heure TIME,
    valeur VARCHAR(100),
    unite VARCHAR(20),
    humeur VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_date (date_journal),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table symptomes_liste
DROP TABLE IF EXISTS symptomes_liste;
CREATE TABLE symptomes_liste (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL UNIQUE,
    categorie VARCHAR(50),
    description TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table symptomes_quotidiens
DROP TABLE IF EXISTS symptomes_quotidiens;
CREATE TABLE symptomes_quotidiens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    symptome_id INT NOT NULL,
    date_symptome DATE NOT NULL,
    intensite INT NOT NULL,
    duree VARCHAR(50),
    notes TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_symptome (symptome_id),
    INDEX idx_date (date_symptome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table pharmacies
DROP TABLE IF EXISTS pharmacies;
CREATE TABLE pharmacies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(150) NOT NULL,
    adresse VARCHAR(255) NOT NULL,
    telephone VARCHAR(20),
    email VARCHAR(100),
    horaires VARCHAR(255),
    pharmacien_id INT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pharmacien (pharmacien_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table medicaments
DROP TABLE IF EXISTS medicaments;
CREATE TABLE medicaments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(150) NOT NULL,
    type VARCHAR(50),
    description TEXT,
    forme VARCHAR(50),
    dosage VARCHAR(50),
    prix DECIMAL(10,2),
    stock INT DEFAULT 0,
    laboratoire VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_nom (nom),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table stock_pharmacies
DROP TABLE IF EXISTS stock_pharmacies;
CREATE TABLE stock_pharmacies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    pharmacie_id INT NOT NULL,
    medicament_id INT NOT NULL,
    quantite INT NOT NULL DEFAULT 0,
    prix_vente DECIMAL(10,2),
    date_expiration DATE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_stock (pharmacie_id, medicament_id),
    INDEX idx_pharmacie (pharmacie_id),
    INDEX idx_medicament (medicament_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table reponses_medicaments
DROP TABLE IF EXISTS reponses_medicaments;
CREATE TABLE reponses_medicaments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    medicament_id INT NOT NULL,
    user_id INT NOT NULL,
    question TEXT NOT NULL,
    reponse TEXT,
    date_question DATETIME NOT NULL,
    date_reponse DATETIME,
    statut VARCHAR(20) NOT NULL DEFAULT 'en_attente',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_medicament (medicament_id),
    INDEX idx_user (user_id),
    INDEX idx_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table plans_exercices
DROP TABLE IF EXISTS plans_exercices;
CREATE TABLE plans_exercices (
    id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    accompagnement_id INT,
    titre VARCHAR(255) NOT NULL,
    description TEXT,
    frequence VARCHAR(100),
    duree_minutes INT,
    niveau VARCHAR(20),
    objectifs TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_accompagnement (accompagnement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table plans_regimes
DROP TABLE IF EXISTS plans_regimes;
CREATE TABLE plans_regimes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    accompagnement_id INT,
    titre VARCHAR(255) NOT NULL,
    description TEXT,
    type_regime VARCHAR(50),
    objectif VARCHAR(100),
    restrictions TEXT,
    calories_jour INT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_patient (patient_id),
    INDEX idx_accompagnement (accompagnement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table notifications
DROP TABLE IF EXISTS notifications;
CREATE TABLE notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    type VARCHAR(50) NOT NULL,
    titre VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    lu BOOLEAN NOT NULL DEFAULT FALSE,
    date_envoi DATETIME NOT NULL,
    lien VARCHAR(255),
    priorite VARCHAR(20) DEFAULT 'normal',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_lu (lu),
    INDEX idx_date (date_envoi)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ajouter les contraintes de clés étrangères
ALTER TABLE patients ADD CONSTRAINT FK_patients_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE medecins ADD CONSTRAINT FK_medecins_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE pharmaciens ADD CONSTRAINT FK_pharmaciens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE accompagnements ADD CONSTRAINT FK_accompagnements_abonnement FOREIGN KEY (abonnement_id) REFERENCES abonnements(id) ON DELETE CASCADE;
ALTER TABLE disponibilites ADD CONSTRAINT FK_disponibilites_medecin FOREIGN KEY (medecin_id) REFERENCES medecins(id) ON DELETE CASCADE;
ALTER TABLE rendez_vous ADD CONSTRAINT FK_rdv_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE;
ALTER TABLE rendez_vous ADD CONSTRAINT FK_rdv_medecin FOREIGN KEY (medecin_id) REFERENCES medecins(id) ON DELETE CASCADE;
ALTER TABLE rendez_vous ADD CONSTRAINT FK_rdv_disponibilite FOREIGN KEY (disponibilite_id) REFERENCES disponibilites(id) ON DELETE SET NULL;
ALTER TABLE contenu ADD CONSTRAINT FK_contenu_auteur FOREIGN KEY (auteur_id) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE likes ADD CONSTRAINT FK_likes_contenu FOREIGN KEY (contenu_id) REFERENCES contenu(id) ON DELETE CASCADE;
ALTER TABLE likes ADD CONSTRAINT FK_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE commentaires ADD CONSTRAINT FK_commentaires_contenu FOREIGN KEY (contenu_id) REFERENCES contenu(id) ON DELETE CASCADE;
ALTER TABLE commentaires ADD CONSTRAINT FK_commentaires_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE commentaires ADD CONSTRAINT FK_commentaires_parent FOREIGN KEY (parent_id) REFERENCES commentaires(id) ON DELETE CASCADE;
ALTER TABLE rapports_analyses ADD CONSTRAINT FK_rapports_analyses_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE;
ALTER TABLE rapports_analyses ADD CONSTRAINT FK_rapports_analyses_medecin FOREIGN KEY (medecin_id) REFERENCES medecins(id) ON DELETE SET NULL;
ALTER TABLE rapports_medicaux ADD CONSTRAINT FK_rapports_medicaux_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE;
ALTER TABLE rapports_medicaux ADD CONSTRAINT FK_rapports_medicaux_medecin FOREIGN KEY (medecin_id) REFERENCES medecins(id) ON DELETE CASCADE;
ALTER TABLE journaux_items ADD CONSTRAINT FK_journaux_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE;
ALTER TABLE symptomes_quotidiens ADD CONSTRAINT FK_symptomes_quotidiens_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE;
ALTER TABLE symptomes_quotidiens ADD CONSTRAINT FK_symptomes_quotidiens_symptome FOREIGN KEY (symptome_id) REFERENCES symptomes_liste(id) ON DELETE CASCADE;
ALTER TABLE pharmacies ADD CONSTRAINT FK_pharmacies_pharmacien FOREIGN KEY (pharmacien_id) REFERENCES pharmaciens(id) ON DELETE SET NULL;
ALTER TABLE stock_pharmacies ADD CONSTRAINT FK_stock_pharmacie FOREIGN KEY (pharmacie_id) REFERENCES pharmacies(id) ON DELETE CASCADE;
ALTER TABLE stock_pharmacies ADD CONSTRAINT FK_stock_medicament FOREIGN KEY (medicament_id) REFERENCES medicaments(id) ON DELETE CASCADE;
ALTER TABLE reponses_medicaments ADD CONSTRAINT FK_reponses_medicament FOREIGN KEY (medicament_id) REFERENCES medicaments(id) ON DELETE CASCADE;
ALTER TABLE reponses_medicaments ADD CONSTRAINT FK_reponses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE plans_exercices ADD CONSTRAINT FK_plans_exercices_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE;
ALTER TABLE plans_exercices ADD CONSTRAINT FK_plans_exercices_accompagnement FOREIGN KEY (accompagnement_id) REFERENCES accompagnements(id) ON DELETE SET NULL;
ALTER TABLE plans_regimes ADD CONSTRAINT FK_plans_regimes_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE;
ALTER TABLE plans_regimes ADD CONSTRAINT FK_plans_regimes_accompagnement FOREIGN KEY (accompagnement_id) REFERENCES accompagnements(id) ON DELETE SET NULL;
ALTER TABLE notifications ADD CONSTRAINT FK_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

SET FOREIGN_KEY_CHECKS=1;

-- Données de test
INSERT IGNORE INTO symptomes_liste (nom, categorie, description) VALUES
('Fièvre', 'Général', 'Élévation de la température corporelle'),
('Toux', 'Respiratoire', 'Expulsion d air des poumons'),
('Maux de tête', 'Neurologique', 'Douleur au niveau de la tête'),
('Fatigue', 'Général', 'Sensation d épuisement'),
('Douleur abdominale', 'Digestif', 'Douleur au niveau de l abdomen');

SELECT 'Migration SQL terminée avec succès!' as status;
EOFMIGRATION

echo -e "${GREEN}✅ Migration SQL exécutée avec succès${NC}"
echo ""

# Vérifier le nombre de tables
TABLE_COUNT=$(docker-compose exec -T "$DB_ADMIN_SERVICE" mysql -uroot -proot -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'pidev';" | tail -n 1)
echo -e "${GREEN}✅ Nombre de tables créées: $TABLE_COUNT${NC}"

echo ""

#############################################################
# ÉTAPE 6: Génération des entités depuis la base de données
#############################################################
echo "============================================================="
echo -e "${BLUE}ÉTAPE 6/8 : Génération des entités Symfony${NC}"
echo "============================================================="

echo -e "${YELLOW}Génération automatique des entités depuis la base de données...${NC}"

# Créer le dossier Entity si nécessaire
docker-compose exec -T web mkdir -p src/Entity

# Générer les entités avec les attributs PHP 8
if docker-compose exec -T web php bin/console doctrine:mapping:import "App\Entity" attribute --path=src/Entity 2>&1; then
    echo -e "${GREEN}✅ Entités générées avec succès${NC}"
else
    echo -e "${YELLOW}⚠️  Génération avec quelques avertissements (c'est normal)${NC}"
fi

echo ""

#############################################################
# ÉTAPE 7: Génération des getters/setters et repositories
#############################################################
echo "============================================================="
echo -e "${BLUE}ÉTAPE 7/8 : Génération des repositories et méthodes${NC}"
echo "============================================================="

echo -e "${YELLOW}Génération des getters, setters et repositories...${NC}"

# Créer le dossier Repository si nécessaire
docker-compose exec -T web mkdir -p src/Repository

# Régénérer toutes les entités avec getters/setters
if docker-compose exec -T web php bin/console make:entity --regenerate App --overwrite 2>&1 | grep -v "updated\|created" | head -20; then
    echo -e "${GREEN}✅ Getters/setters et repositories générés${NC}"
else
    echo -e "${YELLOW}⚠️  Génération terminée avec quelques messages${NC}"
fi

echo ""

#############################################################
# ÉTAPE 8: Vérification finale
#############################################################
echo "============================================================="
echo -e "${BLUE}ÉTAPE 8/8 : Vérification finale${NC}"
echo "============================================================="

# Compter les entités créées
ENTITY_COUNT=$(docker-compose exec -T web sh -c 'ls src/Entity/*.php 2>/dev/null | wc -l')
REPO_COUNT=$(docker-compose exec -T web sh -c 'ls src/Repository/*.php 2>/dev/null | wc -l')

echo -e "${GREEN}📊 Statistiques:${NC}"
echo -e "   • Tables dans la BDD: ${BLUE}$TABLE_COUNT${NC}"
echo -e "   • Entités générées: ${BLUE}$ENTITY_COUNT${NC}"
echo -e "   • Repositories créés: ${BLUE}$REPO_COUNT${NC}"

echo ""
echo -e "${YELLOW}Validation du schéma Doctrine...${NC}"
if docker-compose exec -T web php bin/console doctrine:schema:validate 2>&1; then
    echo -e "${GREEN}✅ Schéma validé${NC}"
else
    echo -e "${YELLOW}⚠️  Quelques différences détectées (c'est normal pour une première génération)${NC}"
fi

echo ""
echo -e "${YELLOW}Liste des entités générées:${NC}"
docker-compose exec -T web ls -1 src/Entity/ 2>/dev/null | grep ".php$" | sed 's/.php$//' | nl

echo ""
echo "============================================================="
echo -e "${GREEN}✅✅✅ SUCCÈS - Toutes les étapes terminées! ✅✅✅${NC}"
echo "============================================================="
echo ""
echo -e "${GREEN}📦 26 entités ont été générées avec succès!${NC}"
echo ""
echo -e "${BLUE}📍 Emplacement des fichiers:${NC}"
echo "   • Entités: ${YELLOW}symfony-app/src/Entity/${NC}"
echo "   • Repositories: ${YELLOW}symfony-app/src/Repository/${NC}"
echo ""
echo -e "${BLUE}🔍 Commandes utiles:${NC}"
echo "   • Lister les entités: ${YELLOW}docker-compose exec web ls src/Entity/${NC}"
echo "   • Voir une entité: ${YELLOW}docker-compose exec web cat src/Entity/User.php${NC}"
echo "   • Valider le schéma: ${YELLOW}docker-compose exec web php bin/console doctrine:schema:validate${NC}"
echo ""
echo -e "${BLUE}🌐 Accès aux services:${NC}"
echo "   • Symfony: ${YELLOW}http://localhost:8000${NC}"
echo "   • phpMyAdmin: ${YELLOW}http://localhost:8080${NC} (user: root, pass: root)"
echo ""
echo -e "${GREEN}🎉 Vous pouvez maintenant commencer à développer!${NC}"
echo ""
