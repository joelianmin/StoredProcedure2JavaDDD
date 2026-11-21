# StoredProcedure2JavaDDD

Spike tugas besar: alat **deterministik** yang memindahkan logika bisnis dari **stored procedure PostgreSQL (PL/pgSQL)** ke aplikasi **Java Spring Boot bergaya DDD**, lalu **membuktikan** bahwa hasilnya berperilaku sama sebelum procedure lama dipensiunkan. Domainnya P2P lending (BFSI), dan endpoint REST-nya mengikuti format SNAP BI.

Alat ini berjalan sepenuhnya lokal tanpa LLM, sehingga source code tidak pernah keluar. AI hanya dipakai untuk membangun alatnya.

```
procedure .sql ─pglast─▶ IR ─analyzer─▶ generator ─▶ Spring Boot (entity, repository Map/JDBC, service, API SNAP)
                                                          │
       procedure asli (CALL di PostgreSQL) ◀── skenario Gherkin ──▶ uji kesetaraan ─▶ laporan LAYAK/TIDAK LAYAK
```

## Menjalankan

Prasyarat: Python ≥ 3.12, Java 17+, Maven, dan binary PostgreSQL 16. Lokasi binary PostgreSQL ditentukan lewat `PG_BIN`.

```bash
python3 -m venv migrator/.venv && migrator/.venv/bin/pip install -e migrator
export PG_BIN=/path/ke/postgresql/bin
scripts/pg.sh start
scripts/run_all.sh          # setup DB → generate → uji kesetaraan → laporan
```

Hasil saat ini: 40/40 skenario setara untuk backend JDBC dan Map, dan 8/8 procedure **LAYAK**. Lihat [laporan/laporan-kelayakan.md](laporan/laporan-kelayakan.md).

## Dokumen

| Dokumen | Isi |
|---|---|
| [docs/artefak-dan-alur.md](docs/artefak-dan-alur.md) | Peta semua artefak, hubungan antar-artefak, dan alur proses |
| [docs/proses-pengerjaan-dengan-ai.md](docs/proses-pengerjaan-dengan-ai.md) | Bagaimana tim berinteraksi dengan AI, dan apa yang dikerjakan AI |
| [docs/superpowers/specs/…-design.md](docs/superpowers/specs/2026-11-21-migrator-plpgsql-ke-ddd-java-design.md) | Spec: desain, katalog jebakan J1–J12, metrik |
| [docs/superpowers/plans/….md](docs/superpowers/plans/2026-11-21-migrator-plpgsql-ke-ddd-java.md) | Plan: 10 task, 4 minggu |
| [docs/demo.md](docs/demo.md) | Naskah demo |

## Struktur

```
corpus/        masukan: schema, procedure lama, mapping.yaml (SNAP), skenario .feature
migrator/      alat (Python): parse → IR → analyzer → generator → report
java-runtime/  kode Java pendukung yang disalin ke proyek hasil (support, snap, equivalence)
generated/     keluaran alat: proyek Spring Boot (dibuat ulang oleh run_all.sh)
laporan/       laporan kelayakan pensiun
experiments/   protokol eksperimen migrasi manual (metrik efisiensi)
scripts/       PostgreSQL lokal, setup DB, alur lengkap, demo curl
spike/         spike 1 (Lark), sekali pakai
spike-pglast/  spike 2 (pglast), sekali pakai, cetak biru migrator/
```
