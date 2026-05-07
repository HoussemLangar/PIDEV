#!/usr/bin/env bash
set -euo pipefail

cd /home/pi-dev/PIDEV

# Ensure the app is launched as a real desktop window (X11), not via Xpra/web.
docker-compose stop javafx-xpra >/dev/null 2>&1 || true

# Allow local docker container to access the X server.
xhost +local:docker >/dev/null 2>&1 || true

# Start JavaFX container if needed and run the desktop app.
docker-compose up -d javafx
docker-compose exec javafx mvn javafx:run
