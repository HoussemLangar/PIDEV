#!/usr/bin/env bash
set -euo pipefail

KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"
INDEX_PATTERN="${INDEX_PATTERN:-pidev-logs-*}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Commande requise manquante: $1" >&2
    exit 1
  }
}

require_cmd curl

PYTHON_BIN=""
if command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
  PYTHON_BIN="python"
else
  echo "Aucun interpreteur Python detecte (python3/python)." >&2
  echo "Provisioning Kibana ignore pour ne pas bloquer le pipeline." >&2
  exit 0
fi

DV_JSON="$(curl -fsS -H 'kbn-xsrf: true' "$KIBANA_URL/api/data_views")"
DV_ID="$("$PYTHON_BIN" -c 'import json,sys
obj=json.loads(sys.stdin.read())
for dv in obj.get("data_view",[]):
    if dv.get("title")=="pidev-logs-*":
        print(dv.get("id",""))
        break
' <<< "$DV_JSON")"

if [[ -z "$DV_ID" ]]; then
  CREATE_DV_PAYLOAD='{"data_view":{"name":"PIDEV Logs","title":"'"$INDEX_PATTERN"'","timeFieldName":"@timestamp"}}'
  DV_ID="$(curl -fsS -X POST "$KIBANA_URL/api/data_views/data_view" \
    -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
    -d "$CREATE_DV_PAYLOAD" | "$PYTHON_BIN" -c 'import json,sys; print(json.load(sys.stdin)["data_view"]["id"])')"
fi

curl -fsS -X POST "$KIBANA_URL/api/kibana/settings/defaultIndex" \
  -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"value":"'"$DV_ID"'"}' >/dev/null

make_search() {
  local id="$1"

  local payload
  payload="$("$PYTHON_BIN" - <<'PY'
import json, os
query = os.environ['KBN_QUERY']
obj = {
  "attributes": {
    "title": os.environ['KBN_TITLE'],
    "description": "Logs stream",
    "columns": ["@timestamp", "app", "message"],
    "sort": ["@timestamp", "desc"],
    "kibanaSavedObjectMeta": {
      "searchSourceJSON": json.dumps({
        "query": {"language": "kuery", "query": query},
        "filter": [],
        "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"
      })
    }
  },
  "references": [
    {
      "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
      "type": "index-pattern",
      "id": os.environ['KBN_DV_ID']
    }
  ]
}
print(json.dumps(obj))
PY
)"

  curl -fsS -X POST "$KIBANA_URL/api/saved_objects/search/$id?overwrite=true" \
    -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
    -d "$payload" >/dev/null
}

KBN_DV_ID="$DV_ID" KBN_TITLE="PIDEV Logs - All" KBN_QUERY="" make_search "pidev-search-all"
KBN_DV_ID="$DV_ID" KBN_TITLE="PIDEV Logs - Symfony" KBN_QUERY="app : \"symfony\"" make_search "pidev-search-symfony"
KBN_DV_ID="$DV_ID" KBN_TITLE="PIDEV Logs - JavaFX" KBN_QUERY="app : \"javafx\"" make_search "pidev-search-javafx"
KBN_DV_ID="$DV_ID" KBN_TITLE="PIDEV Logs - Database" KBN_QUERY="app : \"database\"" make_search "pidev-search-database"

DASH_PAYLOAD="$("$PYTHON_BIN" - <<'PY'
import json

panels = [
    {
        "version": "8.15.2",
        "type": "search",
        "gridData": {"x": 0, "y": 0, "w": 24, "h": 16, "i": "1"},
        "panelIndex": "1",
        "panelRefName": "panel_0",
        "embeddableConfig": {},
    },
    {
        "version": "8.15.2",
        "type": "search",
        "gridData": {"x": 24, "y": 0, "w": 24, "h": 8, "i": "2"},
        "panelIndex": "2",
        "panelRefName": "panel_1",
        "embeddableConfig": {},
    },
    {
        "version": "8.15.2",
        "type": "search",
        "gridData": {"x": 24, "y": 8, "w": 24, "h": 8, "i": "3"},
        "panelIndex": "3",
        "panelRefName": "panel_2",
        "embeddableConfig": {},
    },
    {
      "version": "8.15.2",
      "type": "search",
      "gridData": {"x": 0, "y": 16, "w": 48, "h": 8, "i": "4"},
      "panelIndex": "4",
      "panelRefName": "panel_3",
      "embeddableConfig": {},
    },
]

obj = {
    "attributes": {
        "title": "PIDEV Logs Overview",
    "description": "Centralized logs dashboard for Symfony, JavaFX and Database",
        "timeRestore": False,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"language": "kuery", "query": ""},
                "filter": []
            })
        },
        "panelsJSON": json.dumps(panels),
        "optionsJSON": json.dumps({
            "useMargins": True,
            "syncColors": False,
            "syncCursor": True,
            "syncTooltips": False,
            "hidePanelTitles": False
        })
    },
    "references": [
        {"name": "panel_0", "type": "search", "id": "pidev-search-all"},
        {"name": "panel_1", "type": "search", "id": "pidev-search-symfony"},
      {"name": "panel_2", "type": "search", "id": "pidev-search-javafx"},
      {"name": "panel_3", "type": "search", "id": "pidev-search-database"}
    ]
}

print(json.dumps(obj))
PY
)"

curl -fsS -X POST "$KIBANA_URL/api/saved_objects/dashboard/pidev-logs-overview?overwrite=true" \
  -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
  -d "$DASH_PAYLOAD" >/dev/null

echo "Kibana dashboard provisioned: PIDEV Logs Overview"
echo "Data view id: $DV_ID"
