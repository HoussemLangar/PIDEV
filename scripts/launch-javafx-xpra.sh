#!/usr/bin/env bash
set -euo pipefail

cd /home/pi-dev/PIDEV
docker-compose up -d javafx-xpra
sleep 2
xdg-open http://localhost:14500/index.html >/dev/null 2>&1 || true
