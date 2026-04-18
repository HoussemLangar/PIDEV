#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JDK_HOME="$PROJECT_DIR/.jdks/jdk-21/current"

if [ ! -x "$JDK_HOME/bin/java" ]; then
  echo "JDK local introuvable: $JDK_HOME/bin/java"
  echo "Exécutez d'abord: cd $PROJECT_DIR && mkdir -p .jdks && curl -L \"https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse\" -o .jdks/temurin21.tar.gz"
  exit 1
fi

export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

# Favor X11 backend for JavaFX/WebView on Linux to avoid common GDK/GTK runtime warnings.
export GDK_BACKEND="${GDK_BACKEND:-x11}"
export NO_AT_BRIDGE="${NO_AT_BRIDGE:-1}"

LOG_DIR="$PROJECT_DIR/logs"
LOG_FILE="$LOG_DIR/javafx-app.log"
mkdir -p "$LOG_DIR"

cd "$PROJECT_DIR"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting JavaFX app" | tee -a "$LOG_FILE"
mvn -q javafx:run 2>&1 | tee -a "$LOG_FILE"
exit_code=${PIPESTATUS[0]}
echo "[$(date '+%Y-%m-%d %H:%M:%S')] JavaFX app exited with code $exit_code" | tee -a "$LOG_FILE"
exit "$exit_code"
