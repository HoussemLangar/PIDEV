#!/usr/bin/env bash
set -euo pipefail

APP_NAME="SanteA JavaFX"
PROJECT_DIR="/home/pi-dev/PIDEV"
RUN_SCRIPT="$PROJECT_DIR/javafx-app/scripts/run-javafx.sh"
ICON_PATH="$PROJECT_DIR/javafx-app/src/main/resources/com/santea/images/heart.png"
DESKTOP_DIR="${XDG_DESKTOP_DIR:-$HOME/Desktop}"
AUTOSTART_DIR="$HOME/.config/autostart"
APPLICATIONS_DIR="$HOME/.local/share/applications"
LAUNCHER_NAME="santea-javafx.desktop"
DESKTOP_ENTRY_PATH="$DESKTOP_DIR/$LAUNCHER_NAME"
AUTOSTART_ENTRY_PATH="$AUTOSTART_DIR/$LAUNCHER_NAME"
APPLICATION_ENTRY_PATH="$APPLICATIONS_DIR/$LAUNCHER_NAME"

if [ ! -x "$RUN_SCRIPT" ]; then
  echo "Erreur: script introuvable ou non executable: $RUN_SCRIPT"
  echo "Rends-le executable avec: chmod +x $RUN_SCRIPT"
  exit 1
fi

mkdir -p "$DESKTOP_DIR"
mkdir -p "$AUTOSTART_DIR"
mkdir -p "$APPLICATIONS_DIR"

if [ ! -f "$ICON_PATH" ]; then
  ICON_PATH="utilities-terminal"
fi

cat > "$DESKTOP_ENTRY_PATH" <<EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=$APP_NAME
Comment=Lance l'application JavaFX SanteA
Exec=$RUN_SCRIPT
Path=$PROJECT_DIR/javafx-app
Terminal=false
StartupNotify=true
Icon=$ICON_PATH
Categories=Development;
EOF

chmod +x "$DESKTOP_ENTRY_PATH"
cp "$DESKTOP_ENTRY_PATH" "$AUTOSTART_ENTRY_PATH"
chmod +x "$AUTOSTART_ENTRY_PATH"
cp "$DESKTOP_ENTRY_PATH" "$APPLICATION_ENTRY_PATH"
chmod +x "$APPLICATION_ENTRY_PATH"

# Refresh desktop database if available.
if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database "$DESKTOP_DIR" >/dev/null 2>&1 || true
fi

echo "Shortcut creee: $DESKTOP_ENTRY_PATH"
echo "Autostart cree: $AUTOSTART_ENTRY_PATH"
echo "Application menu cree: $APPLICATION_ENTRY_PATH"
echo "Lance manuellement avec: $RUN_SCRIPT"
