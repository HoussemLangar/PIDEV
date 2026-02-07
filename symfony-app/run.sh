#!/usr/bin/env bash
set -euo pipefail

# Simple runner for the development stack (Docker Compose)
# - starts containers
# - runs composer install inside the PHP container if needed
# Does NOT run migrations or change the database schema.

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

echo "Starting Docker Compose stack..."
docker-compose up -d --build

WEB_SERVICE=$(docker-compose ps --services --filter "status=running" | grep -E "pidev-web|web" || true)
if [ -z "$WEB_SERVICE" ]; then
  echo "Warning: web service not running yet. Listing services:" 
  docker-compose ps
else
  echo "Web service running: $WEB_SERVICE"
fi

echo "Installing PHP dependencies inside the web container (composer install)..."
if docker-compose exec -T ${WEB_SERVICE} php -v >/dev/null 2>&1; then
  docker-compose exec -T ${WEB_SERVICE} composer install --no-interaction --prefer-dist || true
else
  echo "Could not exec into web container to run composer. You can run: docker-compose exec pidev-web composer install"
fi

echo "Application should be available at: http://localhost:8000"
echo "Note: this script does NOT run database migrations or alter the DB."
