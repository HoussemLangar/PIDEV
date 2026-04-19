# PI-DEV - Environnement de développement

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

### 3.bis Monitoring + Logs (Prometheus, Grafana, ELK)
```bash
# Démarrer la stack observabilité
docker-compose --profile observability up -d

# Provisionner le dashboard Kibana (logs Symfony + JavaFX)
bash /home/pi-dev/PIDEV/observability/kibana/provision-kibana-dashboard.sh
```

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001 (admin/admin)
- **Kibana**: http://localhost:5601
- **Elasticsearch API**: http://localhost:9200

Dans Kibana, utilisez le dashboard **PIDEV Logs Overview**.
Le dashboard contient 4 volets: All, Symfony, JavaFX et Database.

Sources observées:
- **Monitoring Symfony**: métriques Apache via `apache-exporter` (`/server-status`)
- **Monitoring JavaFX**: métriques conteneur (CPU/RAM/restarts) via `cadvisor`
- **Logs Symfony**: `symfony-app/var/log/*.log`
- **Logs JavaFX**: `javafx-app/logs/javafx-app.log`

### 4. Base de données
- Host: db (depuis les conteneurs) ou localhost:3306 (depuis l'hôte)
- Database: pidev
- User: symfony
- Password: symfony
- Root password: root

## Commandes utiles

### Jenkins CI/CD
Le projet inclut un pipeline Jenkins declaratif dans `Jenkinsfile`.

Pre-requis Jenkins agent:
- Docker
- docker-compose (v1)
- Acces au daemon Docker (`/var/run/docker.sock`)

Creation job Jenkins:
1. Nouveau job `Pipeline`.
2. `Pipeline script from SCM`.
3. SCM `Git` vers ce repository.
4. Script path: `Jenkinsfile`.

Parametres pipeline:
- `RUN_TESTS`: execute les tests Symfony et JavaFX.
- `ENABLE_OBSERVABILITY`: demarre Prometheus/Grafana/ELK et provisionne Kibana.
- `CLEANUP_AFTER_BUILD`: stoppe les conteneurs en fin de run.

Etapes pipeline:
1. Verification outillage Docker/Compose.
2. Build images `web` et `javafx`.
3. Validation Symfony (`composer install`, lint yaml).
4. Tests Symfony (si actives).
5. Build JavaFX Maven.
6. Tests JavaFX (si actives).
7. Deploiement observabilite (si active).
8. Smoke checks HTTP + endpoints observabilite.

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
# Mode 1: X11 local (nécessite xhost)
# Entrer dans le conteneur JavaFX
docker-compose exec javafx bash

# Lancer l'application JavaFX (Maven)
docker-compose exec javafx mvn javafx:run

# Compiler uniquement
docker-compose exec javafx mvn -DskipTests compile
```

Pour centraliser les logs JavaFX dans ELK, utilisez de préférence le script:

```bash
bash /home/pi-dev/PIDEV/javafx-app/scripts/run-javafx.sh
```

```bash
# Mode 2: Xpra (sans xhost, accès navigateur)
docker-compose up -d javafx-xpra
xdg-open http://localhost:14500/index.html
```

Le mode Xpra lance JavaFX dans un display virtuel et expose l'interface via navigateur.

### Lancer JavaFX via une shortcut Bureau + autostart

Si vous voulez lancer la meme application que `mvn -q javafx:run` via une icone Bureau:

```bash
bash /home/pi-dev/PIDEV/scripts/install-javafx-shortcut.sh
```

Ce script cree:
- `~/Desktop/santea-javafx.desktop` (shortcut Bureau)
- `~/.config/autostart/santea-javafx.desktop` (ouverture automatique a la connexion Linux)

La commande executee est:

```bash
/home/pi-dev/PIDEV/javafx-app/scripts/run-javafx.sh
```

Cette commande lance bien Maven en mode local avec:

```bash
mvn -q javafx:run
```

### Deux shortcuts separes (Windows + VM)

Objectif:
- Une shortcut Windows qui ouvre seulement JavaFX.
- Une shortcut Linux dans la VM qui ouvre JavaFX dans la VM.

Shortcut Windows (native):
1. La VM doit avoir l'autologin Linux + autostart JavaFX deja configures.
2. Depuis Windows, executer:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\windows\create-javafx-shortcut-windows.ps1
```

3. Une icone Bureau `SanteA JavaFX.lnk` est creee.
4. Double-clic sur cette icone: la VM se lance en GUI, puis JavaFX s'ouvre automatiquement dans la VM.

Shortcut dans la VM Linux:

```bash
bash /home/pi-dev/PIDEV/scripts/install-javafx-shortcut.sh
```

Cette commande cree l'icone `~/Desktop/santea-javafx.desktop` dans la VM.

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
