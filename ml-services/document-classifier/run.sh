#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Document Classifier ML Service"
echo "Using rule-based document classification"
echo ""

if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 not found."
    exit 1
fi

# Start service
PORT=${ML_PORT:-5001}
export FLASK_ENV=production
echo "🚀 Starting on http://localhost:$PORT"
echo "   Health check: GET http://localhost:$PORT/health"
echo "   Classify: POST http://localhost:$PORT/classify"
echo "   Press Ctrl+C to stop"
echo ""

python3 "$PROJECT_DIR/app.py"
