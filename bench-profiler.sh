cd /home/pi-dev/PIDEV

BASE="https://santea.tn:8443"
HOME_URL="$BASE/"
FEATURE_URL="$BASE/login"   # change ici si tu veux (ex: /messages/api/conversations)
N=20                        # nombre de requêtes mesurées
WARMUP=5

measure() {
  local name="$1"
  local url="$2"
  local tmp
  tmp=$(mktemp)

  echo "=== $name ==="
  echo "URL: $url"
  echo "Warmup: $WARMUP requêtes..."
  for i in $(seq 1 "$WARMUP"); do
    curl -k -s -o /dev/null "$url"
  done

  echo "Mesure: $N requêtes..."
  for i in $(seq 1 "$N"); do
    curl -k -s -o /dev/null -w "%{time_total}\n" "$url" >> "$tmp"
  done

  awk '
  BEGIN {min=999999; max=0; sum=0; n=0}
  {
    ms=$1*1000;
    if (ms<min) min=ms;
    if (ms>max) max=ms;
    sum+=ms; n++
  }
  END {
    avg=sum/n;
    printf("Avg: %.2f ms | Min: %.2f ms | Max: %.2f ms | N: %d\n", avg, min, max, n);
  }' "$tmp"

  # Récupère un token profiler pour lire la mémoire
  headers=$(mktemp)
  curl -k -s -D "$headers" -o /dev/null "$url"
  token=$(awk 'tolower($1)=="x-debug-token:"{gsub("\r","",$2); print $2}' "$headers")
  if [ -n "$token" ]; then
    echo "Profiler (mémoire): $BASE/_profiler/$token?panel=time"
  else
    echo "Token profiler introuvable (vérifie APP_ENV=dev, APP_DEBUG=1)."
  fi

  rm -f "$tmp" "$headers"
  echo
}

measure "Page d'accueil" "$HOME_URL"
measure "Fonctionnalité principale" "$FEATURE_URL"
