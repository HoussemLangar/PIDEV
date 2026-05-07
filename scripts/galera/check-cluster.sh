#!/usr/bin/env bash
set -euo pipefail

COMPOSE_CMD="${COMPOSE_CMD:-docker-compose}"
PRIMARY_NODE="${PRIMARY_NODE:-db-node1}"
NODES="${NODES:-db-node1 db-node2 db-node3}"
EXPECTED_SIZE="${EXPECTED_SIZE:-3}"
ATTEMPTS="${1:-40}"
SLEEP_SECONDS="${2:-3}"

query_status_value() {
  local node="$1"
  local key="$2"

  local out
  out=$("$COMPOSE_CMD" exec -T "$node" mariadb -uroot -proot -Nse "SHOW STATUS LIKE '${key}';" 2>/dev/null | awk '{print $2}' || true)
  if [[ -z "$out" ]]; then
    out=$("$COMPOSE_CMD" exec -T "$node" mariadb --ssl=0 --protocol=TCP -h127.0.0.1 -uroot -proot -Nse "SHOW STATUS LIKE '${key}';" 2>/dev/null | awk '{print $2}' || true)
  fi
  echo "$out"
}

all_candidate_nodes() {
  local ordered="${PRIMARY_NODE} ${NODES}"
  local seen=" "
  local out=()
  local node

  for node in ${ordered}; do
    if [[ "$seen" != *" ${node} "* ]]; then
      out+=("$node")
      seen+="${node} "
    fi
  done

  echo "${out[*]}"
}

for i in $(seq 1 "$ATTEMPTS"); do
  wsrep_size=""
  wsrep_status=""
  ready_node=""
  debug_obs=""

  for node in $(all_candidate_nodes); do
    node_size="$(query_status_value "$node" "wsrep_cluster_size")"
    node_status="$(query_status_value "$node" "wsrep_cluster_status")"

    if [[ -n "$node_size" || -n "$node_status" ]]; then
      if [[ -z "$ready_node" ]]; then
        ready_node="$node"
        wsrep_size="$node_size"
        wsrep_status="$node_status"
      fi
      debug_obs+=" ${node}[size=${node_size:-n/a},status=${node_status:-n/a}]"
    fi

    if [[ "$node_size" == "$EXPECTED_SIZE" && "$node_status" == "Primary" ]]; then
      ready_node="$node"
      wsrep_size="$node_size"
      wsrep_status="$node_status"
      break
    fi
  done

  if [[ "$wsrep_size" == "$EXPECTED_SIZE" && "$wsrep_status" == "Primary" ]]; then
    echo "Galera cluster ready on ${ready_node}: size=${wsrep_size}, status=${wsrep_status}"
    for node in ${NODES}; do
      state="$(query_status_value "$node" "wsrep_local_state_comment")"
      echo "${node}: ${state:-unknown}"
    done
    exit 0
  fi

  echo "Waiting Galera cluster (${i}/${ATTEMPTS}) size=${wsrep_size:-n/a} status=${wsrep_status:-n/a} observations:${debug_obs:- none}"
  sleep "$SLEEP_SECONDS"
done

echo "Galera cluster did not reach expected state in time." >&2
exit 1
