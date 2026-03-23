# SANTEA

## Présentation
Ce projet a été développé dans le cadre du module PIDEV – 3ème année du cycle ingénieur à Esprit School of Engineering (année universitaire 2025–2026).

Il s’agit d’une application web full-stack permettant aux équipes de :
- gérer leurs tâches,
- suivre l’avancement des projets,
- collaborer efficacement.

## Stack technique
- Symfony 6.4
- PHP 8.1
- MySQL 8.0
- phpMyAdmin
- Apache
- JavaFX (OpenJDK 17)

## Démarrage rapide

### 1. Démarrer les conteneurs Docker
```bash
docker-compose up -d
```

### 2. Installer Symfony 6.4
```bash
docker-compose exec web composer create-project symfony/skeleton:"6.4.*" temp
docker-compose exec web sh -c "mv temp/* temp/.* . 2>/dev/null || true && rm -rf temp"
docker-compose exec web composer require webapp
```

### 3. Accès aux services
- **Application Symfony**: http://localhost:8000
- **phpMyAdmin**: http://localhost:8080
  - Serveur: db
  - Utilisateur: root
  - Mot de passe: root

### 4. Base de données
- Host: db (depuis les conteneurs) ou localhost:3306 (depuis l'hôte)
- Database: pidev
- User: symfony
- Password: symfony
- Root password: root

## Commandes utiles

### Symfony
```bash
# Entrer dans le conteneur web
docker-compose exec web bash

# Créer une entité
docker-compose exec web php bin/console make:entity

# Créer une migration
docker-compose exec web php bin/console make:migration

# Exécuter les migrations
docker-compose exec web php bin/console doctrine:migrations:migrate

# Créer un contrôleur
docker-compose exec web php bin/console make:controller
```

### JavaFX
```bash
# Entrer dans le conteneur JavaFX
docker-compose exec javafx bash

# Compiler (nécessite JavaFX SDK)
javac --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls Main.java

# Exécuter
java --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls Main
```

### Docker
```bash
# Voir les logs
docker-compose logs -f web

# Arrêter les conteneurs
docker-compose down

# Arrêter et supprimer les volumes
docker-compose down -v

# Reconstruire les images
docker-compose build --no-cache
```

## Structure du projet
```
pidev/
├── docker/
│   └── php/
│       └── Dockerfile
├── symfony-app/          # Application Symfony
├── javafx-app/           # Application JavaFX
│   └── src/
│       └── Main.java
├── docker-compose.yml
└── README.md
```

## Contexte académique

Projet réalisé à Esprit School of Engineering – Tunisie
PIDEV – 3ème année | 2025–2026
