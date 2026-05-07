#!/usr/bin/env sh
set -eu

APP_DIR="/var/www/html"
VAR_DIR="$APP_DIR/var"

mkdir -p "$VAR_DIR/log" "$VAR_DIR/cache"

# In dev/CI, /var/www/html is bind-mounted from host, so build-time chown is overridden.
# Re-apply writable permissions at container startup.
chown -R www-data:www-data "$VAR_DIR" 2>/dev/null || true
chmod -R ug+rwX "$VAR_DIR" 2>/dev/null || true

# Keep the official PHP entrypoint behavior, then run Apache foreground.
exec docker-php-entrypoint "$@"