#!/bin/bash
# Alur lengkap: siapkan DB -> generate -> uji kesetaraan -> laporan kelayakan pensiun.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
scripts/setup_db.sh
migrator/.venv/bin/migrator generate --schema corpus/schema.sql --procs corpus/procs \
  --mapping corpus/mapping.yaml --features corpus/features --runtime java-runtime --out generated
GEN=$?
if [ $GEN -eq 3 ]; then echo "mapping.yaml tidak valid, berhenti."; exit 3; fi
echo "Menjalankan uji kesetaraan (mvn test)..."
mvn -q -f generated/pom.xml test > generated/mvn-test.log 2>&1
TEST=$?
grep -E "Scenarios|Steps|TIDAK SETARA|\[jdbc\]|\[memory\]" generated/mvn-test.log || true
migrator/.venv/bin/migrator report --project generated --manual experiments/manual/waktu.csv --out laporan/laporan-kelayakan.md
[ $TEST -eq 0 ] && [ $GEN -eq 0 ]
