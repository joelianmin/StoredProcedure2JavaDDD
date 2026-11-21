#!/bin/bash
# Buat ulang p2p_legacy (skema + stored procedure) dan p2p_baru (skema + tabel API). Idempoten.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/pg.sh" start >/dev/null
PG_BIN="${PG_BIN:-/Users/joelianmin/pasarkita-native/pg16/bin}"
OPTS=(-h "${P2P_PG_HOST:-localhost}" -p "${P2P_PG_PORT:-5433}")
for db in p2p_legacy p2p_baru; do
  "$PG_BIN/dropdb" "${OPTS[@]}" --if-exists "$db"
  "$PG_BIN/createdb" "${OPTS[@]}" "$db"
done
PSQL=("$PG_BIN/psql" "${OPTS[@]}" -q -v ON_ERROR_STOP=1)
"${PSQL[@]}" -d p2p_legacy -f "$ROOT/corpus/schema.sql"
for f in "$ROOT"/corpus/procs/*.sql; do "${PSQL[@]}" -d p2p_legacy -f "$f"; done
"${PSQL[@]}" -d p2p_baru -f "$ROOT/corpus/schema.sql"
"${PSQL[@]}" -d p2p_baru -f "$ROOT/java-runtime/src/main/resources/schema-api.sql"
echo "p2p_legacy dan p2p_baru siap"
