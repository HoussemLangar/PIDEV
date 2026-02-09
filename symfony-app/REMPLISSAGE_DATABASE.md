# 📦 Script de Remplissage de la Base de Données

## 🎯 Objectif

Ce script remplit automatiquement votre base de données avec des données **réalistes, cohérentes et présentables** pour faciliter les démonstrations et les tests.

## ⚠️ Important

**Ce script NE CRÉE PAS les utilisateurs.** Il utilise les utilisateurs existants dans votre base de données pour créer toutes les autres données associées.

## 📋 Prérequis

Avant d'exécuter le script, assurez-vous d'avoir:

1. ✅ Une base de données configurée et migrée
2. ✅ Au moins quelques utilisateurs créés (Patients, Médecins, Pharmaciens, Coaches, Nutritionnistes)
3. ✅ Docker en cours d'exécution (si vous utilisez Docker)

## 🚀 Utilisation

### Option 1: Exécution directe

```bash
php fill_database.php
```

### Option 2: Avec Docker

```bash
docker exec pidev-web php fill_database.php
```

### Option 3: Avec permissions d'exécution

```bash
chmod +x fill_database.php
./fill_database.php
```

## 📊 Données Créées

Le script génère automatiquement:

| Catégorie | Entités créées | Quantité approximative |
|-----------|---------------|------------------------|
| 🏥 **Structures** | Cliniques | 5 |
| 💊 **Santé** | Médicaments | 15 |
| 💊 **Pharmacie** | Pharmacies | 7 |
| 📦 **Stock** | Stocks médicaments | 60-100 |
| 🩺 **Symptômes** | Liste symptômes | 15 |
| 📋 **Abonnements** | Abonnements + Factures | 1-3 par patient |
| 📅 **Accompagnement** | Plans personnalisés | 0-3 par patient |
| 🗓️ **Rendez-vous** | Disponibilités | 10-20 par médecin |
| 🗓️ **Rendez-vous** | Consultations | 0-3 par patient |
| 📄 **Rapports** | Rapports médicaux | 0-3 par patient |
| 📄 **Analyses** | Rapports analyses | 0-2 par patient |
| 📝 **Journal** | Entrées quotidiennes | 3-10 par patient |
| 🩺 **Symptômes** | Symptômes quotidiens | 2-8 par patient |
| 📱 **Contenus** | Articles/Vidéos | 10 |
| ❤️ **Interactions** | Likes | 5-20 par contenu |
| 💬 **Interactions** | Commentaires | 2-8 par contenu |
| 🛒 **Réservations** | Médicaments | 0-3 par patient |
| 🔔 **Notifications** | Alertes/Rappels | 3-10 par utilisateur |
| 💬 **Messagerie** | Conversations | 10-20 |
| 💬 **Messagerie** | Messages | 3-15 par conversation |
| 🎥 **Télémédecine** | Téléconsultations | 0-2 par patient |
| 📎 **Documents** | Fichiers partagés | 0-3 par utilisateur |

## 🎨 Caractéristiques des Données

### ✨ Réalistes
- Noms de médicaments réels
- Prix cohérents
- Adresses et coordonnées GPS réelles (Tunisie)
- Horaires de pharmacies réalistes
- Symptômes médicaux authentiques

### 🔗 Cohérentes
- Relations entre entités respectées
- Dates logiques (passé/présent/futur)
- Statuts appropriés selon les dates
- Progressions calculées automatiquement

### 🎯 Présentables
- Textes en français correct
- Descriptions professionnelles
- Données visuellement agréables
- Prêtes pour démonstration client

## 📝 Détails des Entités

### 🏥 Cliniques
- 5 cliniques à Tunis et environs
- Adresses réelles
- Horaires d'ouverture variés
- Coordonnées complètes

### 💊 Médicaments
- 15 médicaments courants
- Prix réalistes en TND
- Descriptions et dosages précis
- Catégories (douleur, infection, digestif, etc.)

### 💊 Pharmacies
- 7 pharmacies géolocalisées
- Stocks automatiques (8-15 médicaments par pharmacie)
- Prix avec marge de 15%
- Horaires incluant gardes 24/7

### 📋 Abonnements
- Types: Basic (29.99 TND), Premium (49.99 TND), VIP (99.99 TND)
- Durées: 1, 3, 6 ou 12 mois
- Statuts: active, pending, expired
- Factures avec TVA à 19%

### 📅 Plans d'Accompagnement
- Titres professionnels et variés
- Assignation coach et/ou nutritionniste
- Durée: 30 à 180 jours
- Progression: 0-100%
- Statuts: pending, active, completed, cancelled

### 🗓️ Rendez-vous
- Disponibilités sur 2 semaines
- Créneaux de 30 minutes
- 70% disponibles
- Statuts intelligents selon dates

### 📄 Rapports Médicaux
- Titres variés et professionnels
- Diagnostics, traitements, recommandations
- Dates cohérentes
- Associations médecin-patient

### 📝 Journal de Santé
- Types: physical, mental, nutrition, medication, symptom
- Titres contextualisés
- Dates sur 90 jours glissants

### 💬 Messagerie
- Conversations aléatoires entre utilisateurs
- 3-15 messages par conversation
- Alternance expéditeur/destinataire
- 70% des messages marqués comme lus

### 🎥 Téléconsultations
- 60% des patients ont au moins une téléconsultation
- Statuts: scheduled, in_progress, completed, cancelled
- Durées réalistes (10-45 minutes)
- Salles uniques (room-{uuid})

## 🔍 Vérification des Données

Après exécution, vérifiez vos données:

```bash
# Compter les entités créées
docker exec pidev-web bin/console doctrine:query:sql "SELECT COUNT(*) FROM pharmacies"
docker exec pidev-web bin/console doctrine:query:sql "SELECT COUNT(*) FROM medicaments"
docker exec pidev-web bin/console doctrine:query:sql "SELECT COUNT(*) FROM factures"
```

## 🧹 Nettoyage (si nécessaire)

Pour supprimer toutes les données SAUF les utilisateurs:

```sql
-- ATTENTION: Ceci supprime TOUTES les données (sauf users)
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE accompaniment_plans;
TRUNCATE TABLE accompagnements;
TRUNCATE TABLE commentaires;
TRUNCATE TABLE conversations;
TRUNCATE TABLE contenus;
TRUNCATE TABLE disponibilites;
TRUNCATE TABLE document_accesses;
TRUNCATE TABLE factures;
TRUNCATE TABLE journaux_items;
TRUNCATE TABLE likes;
TRUNCATE TABLE messages;
TRUNCATE TABLE notifications;
TRUNCATE TABLE pharmacies;
TRUNCATE TABLE rapports_analyses;
TRUNCATE TABLE rapports_medicaux;
TRUNCATE TABLE rendez_vous;
TRUNCATE TABLE reponses_medicaments;
TRUNCATE TABLE reservations_medicaments;
TRUNCATE TABLE shared_documents;
TRUNCATE TABLE stock_pharmacies;
TRUNCATE TABLE symptomes_quotidiens;
TRUNCATE TABLE teleconsultations;
TRUNCATE TABLE abonnements;
TRUNCATE TABLE cliniques;
TRUNCATE TABLE medicaments;
TRUNCATE TABLE symptomes_liste;

SET FOREIGN_KEY_CHECKS = 1;
```

## 🐛 Dépannage

### Erreur: "Aucun utilisateur trouvé"
**Solution:** Créez d'abord des utilisateurs avant d'exécuter ce script.

### Erreur: "Class not found"
**Solution:** Vérifiez que vous êtes dans le bon répertoire et que l'autoloader Composer est à jour:
```bash
composer dump-autoload
```

### Erreur: "Memory limit"
**Solution:** Augmentez la limite mémoire PHP:
```bash
php -d memory_limit=512M fill_database.php
```

### Erreur: "Foreign key constraint"
**Solution:** Vérifiez que toutes vos migrations sont à jour:
```bash
docker exec pidev-web bin/console doctrine:migrations:status
docker exec pidev-web bin/console doctrine:migrations:migrate
```

## 💡 Conseils

1. **Première exécution:** Commencez avec peu d'utilisateurs (5-10) pour tester
2. **Production:** N'EXÉCUTEZ JAMAIS ce script en production
3. **Personnalisation:** Modifiez les tableaux de données selon vos besoins
4. **Performance:** L'exécution peut prendre 30-60 secondes selon la quantité de données

## 📞 Support

En cas de problème, vérifiez:
- ✅ Version PHP >= 8.1
- ✅ Extensions Doctrine installées
- ✅ Base de données accessible
- ✅ Migrations à jour
- ✅ Utilisateurs existants dans la base

## 🎉 Résultat

Après exécution réussie, vous aurez une base de données complète avec:
- Des relations cohérentes entre toutes les entités
- Des données réalistes prêtes pour démonstration
- Des statistiques exploitables
- Une expérience utilisateur complète

Bonne démonstration ! 🚀
