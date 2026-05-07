#!/usr/bin/env bash
set -euo pipefail

COMPOSE_CMD="${COMPOSE_CMD:-docker-compose}"
ATTEMPTS="${1:-80}"
SLEEP_SECONDS="${2:-3}"
RESET_GALERA_VOLUMES="${RESET_GALERA_VOLUMES:-0}"

if [[ "$RESET_GALERA_VOLUMES" == "1" ]]; then
  echo "Reset Galera volumes requested."
  "$COMPOSE_CMD" rm -sf db db-node1 db-node2 db-node3 >/dev/null 2>&1 || true
  project_prefix="$(basename "$(pwd)" | tr '[:upper:]' '[:lower:]')"
  docker volume rm -f \
    "${project_prefix}_db-node1-data" \
    "${project_prefix}_db-node2-data" \
    "${project_prefix}_db-node3-data" \
    "${project_prefix}_proxysql-data" >/dev/null 2>&1 || true
fi

"$COMPOSE_CMD" rm -sf db db-node1 db-node2 db-node3 >/dev/null 2>&1 || true

wait_node_ready() {
  local node="$1"
  local i

  for i in $(seq 1 "$ATTEMPTS"); do
    if "$COMPOSE_CMD" exec -T "$node" mariadb -uroot -proot -Nse "SELECT 1;" >/dev/null 2>&1 \
      || "$COMPOSE_CMD" exec -T "$node" mariadb --ssl=0 --protocol=TCP -h127.0.0.1 -uroot -proot -Nse "SELECT 1;" >/dev/null 2>&1; then
      echo "${node} is ready"
      return 0
    fi
    echo "Waiting ${node} (${i}/${ATTEMPTS})"
    sleep "$SLEEP_SECONDS"
  done

  echo "${node} did not become ready in time." >&2
  return 1
}

echo "[1/4] Start bootstrap node db-node1"
"$COMPOSE_CMD" up -d db-node1
wait_node_ready db-node1

echo "[2/4] Start joiner nodes db-node2/db-node3"
"$COMPOSE_CMD" up -d db-node2 db-node3

COMPOSE_CMD="$COMPOSE_CMD" PRIMARY_NODE="db-node1" NODES="db-node1 db-node2 db-node3" bash ./scripts/galera/check-cluster.sh "$ATTEMPTS" "$SLEEP_SECONDS"

echo "[3/4] Ensure monitoring users"
COMPOSE_CMD="$COMPOSE_CMD" DB_ADMIN_SERVICE="db-node1" NODES="db-node1 db-node2 db-node3" bash ./scripts/galera/ensure-galera-users.sh 40 3

echo "[4/4] Start ProxySQL endpoint"
"$COMPOSE_CMD" up -d db

echo "Galera cluster and ProxySQL are up."
