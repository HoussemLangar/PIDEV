#!/usr/bin/env bash
set -euo pipefail

COMPOSE_CMD="${COMPOSE_CMD:-docker-compose}"
TARGET_GALERA_SERVICE="${TARGET_GALERA_SERVICE:-db-node1}"
GALERA_NODES="${GALERA_NODES:-db-node1 db-node2 db-node3}"
TARGET_ROOT_USER="${TARGET_ROOT_USER:-root}"
TARGET_ROOT_PASSWORD="${TARGET_ROOT_PASSWORD:-root}"
TARGET_DATABASE="${TARGET_DATABASE:-pidev}"

OLD_MYSQL_HOST="${OLD_MYSQL_HOST:-127.0.0.1}"
OLD_MYSQL_PORT="${OLD_MYSQL_PORT:-3306}"
OLD_MYSQL_USER="${OLD_MYSQL_USER:-root}"
OLD_MYSQL_PASSWORD="${OLD_MYSQL_PASSWORD:-root}"
OLD_MYSQL_DATABASE="${OLD_MYSQL_DATABASE:-pidev}"
OLD_MYSQL_DOCKER_NETWORK="${OLD_MYSQL_DOCKER_NETWORK:-}"

STAMP="$(date +%Y%m%d_%H%M%S)"
DUMP_FILE="${DUMP_FILE:-/tmp/${OLD_MYSQL_DATABASE}_${STAMP}.sql}"

is_target_ready() {
  local node="$1"
  "$COMPOSE_CMD" exec -T "$node" mariadb -u"$TARGET_ROOT_USER" -p"$TARGET_ROOT_PASSWORD" -Nse "SELECT 1;" >/dev/null 2>&1 \
    || "$COMPOSE_CMD" exec -T "$node" mariadb --ssl=0 --protocol=TCP -h127.0.0.1 -u"$TARGET_ROOT_USER" -p"$TARGET_ROOT_PASSWORD" -Nse "SELECT 1;" >/dev/null 2>&1
}

resolve_target_node() {
  if is_target_ready "$TARGET_GALERA_SERVICE"; then
    echo "$TARGET_GALERA_SERVICE"
    return 0
  fi

  local node
  for node in ${GALERA_NODES}; do
    if is_target_ready "$node"; then
      echo "$node"
      return 0
    fi
  done

  return 1
}

echo "[1/6] Dump from old MySQL ${OLD_MYSQL_HOST}:${OLD_MYSQL_PORT}/${OLD_MYSQL_DATABASE}"
network_args=()
if [[ -n "$OLD_MYSQL_DOCKER_NETWORK" ]]; then
  network_args=(--network "$OLD_MYSQL_DOCKER_NETWORK")
fi

docker run --rm "${network_args[@]}" -e MYSQL_PWD="$OLD_MYSQL_PASSWORD" mysql:8.0 \
  sh -lc "mysqldump --single-transaction --quick --routines --triggers --events --set-gtid-purged=OFF -h '$OLD_MYSQL_HOST' -P '$OLD_MYSQL_PORT' -u '$OLD_MYSQL_USER' '$OLD_MYSQL_DATABASE'" \
  > "$DUMP_FILE"

if [[ ! -s "$DUMP_FILE" ]]; then
  echo "Dump file is empty: $DUMP_FILE" >&2
  exit 1
fi

echo "[2/6] Wait for Galera cluster"
COMPOSE_CMD="$COMPOSE_CMD" PRIMARY_NODE="$TARGET_GALERA_SERVICE" NODES="$GALERA_NODES" bash ./scripts/galera/check-cluster.sh 80 3

target_node="$(resolve_target_node)" || {
  echo "No reachable Galera node found for migration target." >&2
  exit 1
}

echo "[3/6] Ensure Galera users for monitoring/metrics"
COMPOSE_CMD="$COMPOSE_CMD" DB_ADMIN_SERVICE="$target_node" NODES="$GALERA_NODES" bash ./scripts/galera/ensure-galera-users.sh

echo "[4/6] Ensure target database exists on Galera"
"$COMPOSE_CMD" exec -T "$target_node" mariadb --protocol=TCP -h127.0.0.1 -u"$TARGET_ROOT_USER" -p"$TARGET_ROOT_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS \\`$TARGET_DATABASE\\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

echo "[5/6] Import dump into Galera node ($target_node)"
"$COMPOSE_CMD" exec -T "$target_node" mariadb --protocol=TCP -h127.0.0.1 -u"$TARGET_ROOT_USER" -p"$TARGET_ROOT_PASSWORD" "$TARGET_DATABASE" < "$DUMP_FILE"

echo "[6/6] Validate table counts source vs target"
source_tables=$(docker run --rm "${network_args[@]}" -e MYSQL_PWD="$OLD_MYSQL_PASSWORD" mysql:8.0 \
  sh -lc "mysql -h '$OLD_MYSQL_HOST' -P '$OLD_MYSQL_PORT' -u '$OLD_MYSQL_USER' -Nse \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${OLD_MYSQL_DATABASE}'\"" | tr -d '[:space:]')

target_tables=$("$COMPOSE_CMD" exec -T "$target_node" mariadb --protocol=TCP -h127.0.0.1 -u"$TARGET_ROOT_USER" -p"$TARGET_ROOT_PASSWORD" -Nse \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${TARGET_DATABASE}'" | tr -d '[:space:]')

echo "Source tables: ${source_tables}"
echo "Target tables: ${target_tables}"

if [[ "$source_tables" != "$target_tables" ]]; then
  echo "Table count mismatch after migration." >&2
  exit 1
fi

echo "[7/7] Validate wsrep state"
COMPOSE_CMD="$COMPOSE_CMD" PRIMARY_NODE="$target_node" NODES="$GALERA_NODES" bash ./scripts/galera/check-cluster.sh

echo "Migration completed successfully. Dump file kept at: $DUMP_FILE"
