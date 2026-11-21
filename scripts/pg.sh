#!/bin/bash
# Cluster PostgreSQL 16 milik tubes ini (terpisah dari proyek lain). Pakai: scripts/pg.sh start|stop|status
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PG_BIN="${PG_BIN:-/Users/joelianmin/pasarkita-native/pg16/bin}"
DATA="$ROOT/.pgdata"
PORT="${P2P_PG_PORT:-5433}"
case "${1:-status}" in
  start)
    [ -f "$DATA/PG_VERSION" ] || "$PG_BIN/initdb" -D "$DATA" -U "$(whoami)" --auth=trust -E UTF8 --locale=C >/dev/null
    "$PG_BIN/pg_ctl" -D "$DATA" -o "-p $PORT -k /tmp" -l "$DATA/server.log" -w status >/dev/null 2>&1 \
      || "$PG_BIN/pg_ctl" -D "$DATA" -o "-p $PORT -k /tmp" -l "$DATA/server.log" -w start >/dev/null
    echo "PostgreSQL berjalan di port $PORT" ;;
  stop) "$PG_BIN/pg_ctl" -D "$DATA" -w stop ;;
  status) "$PG_BIN/pg_ctl" -D "$DATA" status ;;
esac
