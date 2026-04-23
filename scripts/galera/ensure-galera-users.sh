#!/usr/bin/env bash
set -euo pipefail

COMPOSE_CMD="${COMPOSE_CMD:-docker-compose}"
DB_ADMIN_SERVICE="${DB_ADMIN_SERVICE:-db-node1}"
NODES="${NODES:-db-node1 db-node2 db-node3}"
ATTEMPTS="${1:-40}"
SLEEP_SECONDS="${2:-3}"

is_node_ready() {
	local node="$1"
	"$COMPOSE_CMD" exec -T "$node" mariadb -uroot -proot -Nse "SELECT 1;" >/dev/null 2>&1 \
		|| "$COMPOSE_CMD" exec -T "$node" mariadb --ssl=0 --protocol=TCP -h127.0.0.1 -uroot -proot -Nse "SELECT 1;" >/dev/null 2>&1
}

pick_admin_node() {
	local candidates=("$DB_ADMIN_SERVICE")
	local n
	for n in ${NODES}; do
		if [[ "$n" != "$DB_ADMIN_SERVICE" ]]; then
			candidates+=("$n")
		fi
	done

	local i node
	for i in $(seq 1 "$ATTEMPTS"); do
		for node in "${candidates[@]}"; do
			if is_node_ready "$node"; then
				echo "$node"
				return 0
			fi
		done
		echo "Waiting for Galera admin node (${i}/${ATTEMPTS})" >&2
		sleep "$SLEEP_SECONDS"
	done

	return 1
}

admin_node="$(pick_admin_node)" || {
	echo "No Galera node is ready for admin SQL operations." >&2
	exit 1
}

run_admin_sql() {
	local sql="$1"

	"$COMPOSE_CMD" exec -T "$admin_node" mariadb -uroot -proot -Nse "$sql" \
		|| "$COMPOSE_CMD" exec -T "$admin_node" mariadb --ssl=0 --protocol=TCP -h127.0.0.1 -uroot -proot -Nse "$sql"
}

wait_monitor_credentials() {
	local i node

	for i in $(seq 1 "$ATTEMPTS"); do
		local all_ok=1
		for node in ${NODES}; do
			if ! "$COMPOSE_CMD" exec -T "$admin_node" mariadb --connect-timeout=3 --ssl=0 -h"$node" -P3306 -umonitor -pmonitor -Nse "SELECT 1;" >/dev/null 2>&1; then
				all_ok=0
				break
			fi
		done

		if [[ "$all_ok" -eq 1 ]]; then
			return 0
		fi

		sleep "$SLEEP_SECONDS"
	done

	echo "monitor credentials are not accepted on all Galera nodes." >&2
	for node in ${NODES}; do
		echo "${node}:" >&2
		"$COMPOSE_CMD" exec -T "$admin_node" mariadb --connect-timeout=3 --ssl=0 -h"$node" -P3306 -uroot -proot -Nse "SELECT User, Host FROM mysql.user WHERE User IN ('monitor','exporter') ORDER BY User, Host;" || true
	done
	return 1
}

run_admin_sql "
CREATE USER IF NOT EXISTS 'monitor'@'%' IDENTIFIED BY 'monitor';
ALTER USER 'monitor'@'%' IDENTIFIED BY 'monitor';
CREATE USER IF NOT EXISTS 'monitor'@'172.18.%' IDENTIFIED BY 'monitor';
ALTER USER 'monitor'@'172.18.%' IDENTIFIED BY 'monitor';
GRANT USAGE, PROCESS, REPLICATION CLIENT ON *.* TO 'monitor'@'%';
GRANT USAGE, PROCESS, REPLICATION CLIENT ON *.* TO 'monitor'@'172.18.%';

CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY 'exporter';
ALTER USER 'exporter'@'%' IDENTIFIED BY 'exporter';
CREATE USER IF NOT EXISTS 'exporter'@'172.18.%' IDENTIFIED BY 'exporter';
ALTER USER 'exporter'@'172.18.%' IDENTIFIED BY 'exporter';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'172.18.%';

FLUSH PRIVILEGES;
"

wait_monitor_credentials

echo "Users monitor/exporter ensured on ${admin_node}."
