# Naskah Demo (±10 menit)

Persiapan: `scripts/pg.sh start`, lalu `scripts/run_all.sh` sekali (memanaskan Maven).

## 1. Masalahnya (1 menit)
- Tunjukkan `FundingService.fund()` di repo semester lalu: saldo lender **didebit sebelum** validasi
  "Pendanaan melebihi sisa target". Dengan Map tidak ada rollback, jadi saldo hilang.
- Tunjukkan versi legacy-nya: `corpus/procs/danai_pinjaman.sql`. Urutannya sama, tapi di PostgreSQL
  `RAISE` membatalkan semuanya.
- Pertanyaannya: bagaimana memindahkan logika ini ke Java **tanpa** mengubah perilaku?

## 2. Parsing dua tingkat + IR (2 menit)
```bash
migrator/.venv/bin/python -c "from pathlib import Path; from migrator.parse import *; \
T=parse_schema(Path('corpus/schema.sql').read_text()); \
print(parse_procedure(Path('corpus/procs/danai_pinjaman.sql').read_text(), T, 'danai_pinjaman.sql').body[0])"
```
- `pglast.parse_plpgsql` mengurai struktur, `pglast.parse_sql` mengurai SQL/ekspresi di dalamnya,
  lalu keduanya digabung menjadi IR milik sendiri.
- Tunjukkan bahwa konstruksi terlarang ditolak eksplisit (mis. `EXECUTE`, FOR tanpa `ORDER BY`).

## 3. Generate (1 menit)
```bash
migrator/.venv/bin/migrator generate --schema corpus/schema.sql --procs corpus/procs \
  --mapping corpus/mapping.yaml --features corpus/features --runtime java-runtime --out generated
```
- Buka `generated/src/main/java/com/p2p/service/DanaiPinjamanService.java`: setiap pernyataan punya
  komentar `// danai_pinjaman.sql:<baris>`; nilai yang pasti tidak NULL memakai Java biasa,
  yang bisa NULL memakai `Sql.*` (logika tiga nilai).

## 4. Bukti kesetaraan (2 menit)
```bash
mvn -q -f generated/pom.xml test     # 34 skenario x (jdbc, memory) vs CALL asli
```
- Skenario "Melebihi sisa target ... (J4)": saldo lender kembali di ketiga backend.
- **Langsung tunjukkan kepekaan:** ganti `HALF_UP` menjadi `HALF_EVEN` di
  `generated/src/main/java/com/p2p/support/Sql.java`, jalankan lagi → skenario pembulatan 1.005 gagal
  (`lama=4878.39 baru=4878.40`). Generate ulang untuk memulihkan.

## 5. REST ala SNAP BI (2 menit)
```bash
mvn -q -f generated/pom.xml package -DskipTests
java -jar generated/target/p2p-lending-migrated-0.1.0.jar --spring.profiles.active=postgres --server.port=8089 &
# isi data p2p_baru: loan L001 (10 juta, PENDING), lender LD001 (20 juta), lalu:
scripts/curl_snap_demo.sh
```
- `2009100` sukses, `4049101` loan tidak ada, `4099100` X-EXTERNAL-ID ganda, `4009102` header hilang,
  `4039101` melebihi target (pesan asli RAISE tidak bocor ke klien).

## 6. Laporan (1 menit)
- `laporan/laporan-kelayakan.md`: putusan LAYAK per procedure, jebakan yang ditangani, metrik
  keefektifan / efisiensi / kebermanfaatan.

## Pembagian bicara
- Orang A: bagian 2, 4 (kebenaran, jebakan, kesetaraan).
- Orang B: bagian 1, 5, 6 (masalah nyata, API, metrik).
