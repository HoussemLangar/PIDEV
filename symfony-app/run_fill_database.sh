#!/bin/bash

# Script wrapper pour exécuter le remplissage de la base de données
# Usage: ./run_fill_database.sh

echo "════════════════════════════════════════════════════════"
echo "   🚀 Remplissage de la Base de Données"
echo "════════════════════════════════════════════════════════"
echo ""

# Vérifier si Docker est en cours d'exécution
if ! docker ps > /dev/null 2>&1; then
    echo "❌ ERREUR: Docker n'est pas en cours d'exécution"
    echo "   Démarrez Docker avant de continuer."
    exit 1
fi

# Vérifier si le conteneur pidev-web existe
if ! docker ps --format '{{.Names}}' | grep -q "pidev-web"; then
    echo "❌ ERREUR: Le conteneur pidev-web n'est pas en cours d'exécution"
    echo "   Démarrez-le avec: docker-compose up -d"
    exit 1
fi

echo "✅ Docker et conteneur pidev-web détectés"
echo ""

# Afficher un avertissement
echo "⚠️  AVERTISSEMENT:"
echo "   Ce script va REMPLIR votre base de données avec des données de test."
echo "   Les données existantes (sauf utilisateurs) ne seront PAS supprimées."
echo ""
read -p "   Voulez-vous continuer? (o/N) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[OoYy]$ ]]; then
    echo "❌ Opération annulée."
    exit 0
fi

echo ""
echo "📊 Vérification des utilisateurs existants..."
USER_COUNT=$(docker exec pidev-web bin/console doctrine:query:sql "SELECT COUNT(*) as count FROM users" --quiet 2>/dev/null | tail -1 | grep -oE '[0-9]+')

if [ -z "$USER_COUNT" ] || [ "$USER_COUNT" -eq 0 ]; then
    echo "❌ ERREUR: Aucun utilisateur trouvé dans la base de données"
    echo "   Créez d'abord des utilisateurs avant d'exécuter ce script."
    exit 1
fi

echo "✅ $USER_COUNT utilisateur(s) trouvé(s)"
echo ""

# Exécuter le script de remplissage
echo "🚀 Démarrage du remplissage..."
echo "   (Cela peut prendre 30-60 secondes)"
echo ""

docker exec pidev-web php fill_database.php

EXIT_CODE=$?

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo "════════════════════════════════════════════════════════"
    echo "   ✅ Remplissage terminé avec succès!"
    echo "════════════════════════════════════════════════════════"
    echo ""
    echo "📊 Prochaines étapes:"
    echo "   1. Consultez REMPLISSAGE_DATABASE.md pour plus d'infos"
    echo "   2. Accédez à votre application pour voir les données"
    echo "   3. Testez les différentes fonctionnalités"
    echo ""
else
    echo "════════════════════════════════════════════════════════"
    echo "   ❌ Erreur lors du remplissage"
    echo "════════════════════════════════════════════════════════"
    echo ""
    echo "🔍 Vérifiez:"
    echo "   - Les logs Docker: docker logs pidev-web"
    echo "   - Le fichier REMPLISSAGE_DATABASE.md section 'Dépannage'"
    echo ""
    exit 1
fi
