# Artefak, Hubungan Antar-Artefak, dan Alur Proses

Dokumen ini memetakan semua isi repo: apa setiap artefak, siapa yang membuatnya (tim, AI, atau alat), dan bagaimana satu artefak menjadi masukan bagi artefak lain. Cerita tentang jalannya sesi pengerjaan ada di [proses-pengerjaan-dengan-ai.md](proses-pengerjaan-dengan-ai.md).

## 1. Gambaran besar

Ada tiga lapisan artefak:

| Lapisan | Isi | Dibuat oleh | Dipakai untuk |
|---|---|---|---|
| **A. Artefak perencanaan** | spike, spec, plan, catatan proses | AI, atas arahan dan persetujuan tim | Memutuskan *apa* yang dibangun dan *mengapa* |
| **B. Alat dan masukannya** | migrator (Python), runtime Java, korpus, script | AI menulis kodenya. Tim memiliki korpus dan aturan pemetaannya. | Mengubah stored procedure menjadi aplikasi Java |
| **C. Keluaran alat** | proyek `generated/`, hasil uji kesetaraan, laporan kelayakan | Dihasilkan otomatis dan deterministik oleh alat | Bukti untuk keputusan pensiun procedure |

```
 LAPISAN A: PERENCANAAN
 spike/ (Lark) ──pelajaran: NULL berbahaya──┐
 spike-pglast/ ──pelajaran: pglast + IR jalan──┤
                                              ▼
            docs/superpowers/specs/...-design.md  (SPEC: apa & kenapa, katalog J1–J12)
                                              │ diturunkan menjadi
                                              ▼
            docs/superpowers/plans/...md          (PLAN: 10 task, cara cek per task)
                                              │ dieksekusi menjadi
 ─────────────────────────────────────────────┼──────────────────────────────────────────
 LAPISAN B: ALAT + MASUKAN                    ▼
   corpus/schema.sql ─┐                  migrator/ (Python)          java-runtime/ (Java)
   corpus/procs/*.sql ┼──────────────▶   parse → IR → analyzer  ◀──  support/ snap/ equivalence/
   corpus/mapping.yaml┤                  → generator (Jinja2)        (disalin apa adanya)
   corpus/features/ ──┘                         │
 ───────────────────────────────────────────────┼────────────────────────────────────────
 LAPISAN C: KELUARAN                            ▼
                         generated/  (proyek Spring Boot + migrator-analysis.json)
                                                │ mvn test (Cucumber, 3 backend)
                                                ▼
                         generated/target/equivalence-results.jsonl
                                                │ migrator report  (+ experiments/manual/waktu.csv)
                                                ▼
                         laporan/laporan-kelayakan.md   ──▶  LAYAK / TIDAK LAYAK pensiun
```

## 2. Lapisan A: artefak perencanaan

| Artefak | Isi | Hubungan |
|---|---|---|
| `spike/` | **Spike 1, sekali pakai.** Grammar PL/SQL subset Lark (`plsql.lark`), transpiler (`transpile.py`), masukan procedure denda kredit, dan keluaran berupa `ast.json`, service Java, serta `laporan.md`. | Menunjukkan bahwa terjemahan yang *tampak benar* bisa berbeda perilakunya, misalnya pada NULL. Pelajaran ini menjadi alasan adanya testkit kesetaraan dan katalog jebakan di spec. Spike ini tidak dipakai lagi oleh apa pun. |
| `spike-pglast/` | **Spike 2, sekali pakai.** Seluruh migrator dalam satu file (`migrator.py`), masukan `danai_pinjaman.sql` dan `schema.sql`, runtime minimal, 7 file Java hasil generate, dan `demo/Demo.java` yang menjalankan 11 cek. | Membuktikan pendekatan pglast dua tingkat → IR → Java. Strukturnya menjadi cetak biru `migrator/`, dan temuan soal keterbacaan kode memunculkan mode *readable*. |
| `docs/superpowers/specs/2026-11-21-migrator-plpgsql-ke-ddd-java-design.md` | **Spec (dokumen desain).** Masalah, prinsip (deterministik, lokal), subset PL/pgSQL yang didukung, katalog jebakan **J1–J12**, definisi "setara", bentuk output DDD dan SNAP, serta metrik untuk ketiga kriteria dosen. | Sumber kebenaran. Kalau plan atau kode bertentangan dengan spec, spec yang menang. ID jebakan J1–J12 dipakai ulang di `analyzer.py`, di nama skenario `.feature`, dan di laporan. |
| `docs/superpowers/plans/2026-11-21-migrator-plpgsql-ke-ddd-java.md` | **Plan.** 10 task dalam 4 minggu beserta pemeriksaan per task, dan daftar hal yang "Ditunda" karena mode spike. | Diturunkan dari spec. Setiap task menjadi satu commit (lihat §6). |
| `docs/proses-pengerjaan-dengan-ai.md` | Kronologi interaksi tim dengan AI, dan rincian pekerjaan AI. | Menjelaskan asal-usul semua artefak di atas. |
| `docs/demo.md` | Naskah demo langkah demi langkah. | Memakai `scripts/run_all.sh`, `scripts/curl_snap_demo.sh`, dan laporan. |
| `docs/artefak-dan-alur.md` | Dokumen ini. | — |

## 3. Lapisan B: alat dan masukannya

### 3.1 Korpus: masukan, milik tim

Korpus berperan sebagai "sistem lama" yang akan dipensiunkan, sekaligus spesifikasi perilakunya.

| Artefak | Isi | Dipakai oleh |
|---|---|---|
| `corpus/schema.sql` | Tabel `borrower`, `lender`, `loan`, `funding`. Kolom uang bertipe `numeric(15,2)`, `income` boleh NULL. | (1) `setup_db.sh`, dipasang di kedua database; (2) migrator, untuk tipe, PK, dan `NOT NULL`, yang menjadi entity dan repository; (3) testkit, untuk dump tabel |
| `corpus/procs/*.sql` | 5 procedure bisnis P2P lending (ajukan, danai, cairkan, bayar, batalkan) dan 3 procedure uji (`uji_*`) hasil temuan review | (1) dipasang di `p2p_legacy` sebagai **acuan kebenaran** yang dijalankan dengan `CALL`; (2) di-parse migrator menjadi service Java |
| `corpus/mapping.yaml` | Aturan pemetaan procedure ke layanan SNAP: nama layanan, kode layanan 90–94, parameter uang, dan pesan `RAISE` ke `{http, case, message}` | Generator API, yang menghasilkan controller, request, dan `GeneratedSnapRoutes`. **Kode kasusnya masih ilustrasi.** |
| `corpus/features/*.feature` | 40 skenario Gherkin: data awal (Given), pemanggilan procedure (When), dan harapan "setara" (Then). Nama skenario menyebut jebakan yang diuji. | Disalin ke proyek generate dan dijalankan Cucumber terhadap 3 backend |
| `corpus/smoke.sql` | Pemeriksaan cepat manual bahwa procedure terpasang dan bisa dipanggil | Dijalankan manual dengan `psql` |

### 3.2 Migrator (Python): alatnya

Migrator dipasang dari `migrator/pyproject.toml` (pglast 8.9, Jinja2, PyYAML) dengan perintah `migrator generate` dan `migrator report`. Alurnya:

```
procs/*.sql ──parse.py──▶ ir.py (IR) ──analyzer.py──▶ Analysis (tipe, nullability, jebakan)
schema.sql ───parse.py──▶ Table/Column ─┘                     │
mapping.yaml ─mapping.py (validasi) ───────────────────────────┤
                                                               ▼
                         gen/java.py, gen/java_api.py, gen/java_test.py + gen/templates/*.j2
                                                               │  gen/project.py merakit
                                                               ▼
                                                         generated/
```

| Modul | Tanggung jawab | Masukan → keluaran |
|---|---|---|
| `parse.py` | Parsing dua tingkat: `parse_plpgsql` untuk struktur (DECLARE, IF, FOR, RAISE), `parse_sql` untuk SQL dan ekspresi di dalamnya. Konstruksi di luar subset ditolak dengan `Unsupported`. | teks SQL → objek IR |
| `ir.py` | IR milik sendiri yang immutable: `Procedure`, `Stmt` (Assign, If, SelectInto, Update, Insert, Delete, Raise, ForQuery, Return), `Expr`, `Table`. Inilah pemisah antara bentuk pohon pglast dan generator. | — |
| `analyzer.py` | Inferensi tipe, analisis nullability (*definite assignment* terstruktur), dan deteksi jebakan J1–J12 per baris | IR → `Analysis` (status ok/tidak didukung, daftar jebakan) |
| `naming.py` | Aturan nama SQL → Java (camelCase, buang prefix `p_`/`v_`, hindari keyword) dan pengutipan identifier SQL | — |
| `mapping.py` | Membaca dan memvalidasi `mapping.yaml` | YAML → model pemetaan, atau `MappingError` (exit 3) |
| `gen/java.py` | Menerjemahkan statement dan ekspresi IR ke Java. Memakai helper `Sql.*` bila nilai bisa NULL, dan Java biasa bila pasti tidak NULL (mode *readable*). | IR + Analysis → isi `*Service.java` |
| `gen/java_api.py` | Controller dan request SNAP dari mapping | → `api/*` |
| `gen/java_test.py` | Penghubung testkit: `GeneratedBackends`, `GeneratedSchema`, `RowConverters`, runner | → `src/test/java/...` |
| `gen/templates/*.j2` | Template Jinja2 untuk setiap jenis file Java dan properties | — |
| `gen/project.py` | Merakit seluruh proyek, menyalin runtime, dan menulis `migrator-analysis.json` | → `generated/` |
| `report.py` | Menggabungkan analisis statis, hasil uji, dan data eksperimen manual menjadi laporan | → `laporan/laporan-kelayakan.md` |
| `cli.py` | Perintah `generate` (exit 0 OK, 2 ada procedure tidak didukung, 3 mapping tidak valid) dan `report` | — |

### 3.3 Runtime Java: disalin apa adanya ke proyek hasil

`java-runtime/` adalah kode tulisan tangan yang tidak di-generate tapi dibutuhkan oleh kode hasil generate. Migrator menyalinnya:

| Paket | Isi | Disalin ke | Peran |
|---|---|---|---|
| `com.p2p.support` | `Sql` (logika tiga nilai, propagasi NULL, pembagian ala PostgreSQL, `fitNumeric`, format `RAISE`), `ProcedureException` + `SqlStates`, `UnitOfWork` (`SnapshotUnitOfWork` untuk Map, `SpringTxUnitOfWork` untuk JDBC) | `generated/src/main/java` | Meniru semantik PostgreSQL di Java (jebakan J1–J12) |
| `com.p2p.snap` | Filter header, idempotensi `X-EXTERNAL-ID`, `responseCode`, `SnapAmount`, penanganan error | `generated/src/main/java` | Lapisan REST ala SNAP BI |
| `com.p2p.equivalence` | Step Cucumber, backend legacy (`CALL` via JDBC), pembanding tabel `TableDiff`, `EquivalenceRecorder` | `generated/src/test/java` | Mesin pembuktian kesetaraan |
| `src/main/resources/schema-api.sql` | Tabel `snap_external_id` | Dipasang oleh `setup_db.sh` di `p2p_baru` | Penyimpanan idempotensi |
| `pom.xml` | Untuk mengompilasi runtime secara terpisah | — | — |

### 3.4 Script

| Script | Fungsi | Memakai |
|---|---|---|
| `scripts/pg.sh` | Menyalakan atau mematikan cluster PostgreSQL 16 sendiri di `.pgdata`, port 5433 (lokasi binary bisa diganti dengan `PG_BIN`) | — |
| `scripts/setup_db.sh` | Membuat ulang `p2p_legacy` (schema + procedure lama) dan `p2p_baru` (schema + `schema-api.sql`, **tanpa** procedure) | korpus, `schema-api.sql` |
| `scripts/run_all.sh` | Alur lengkap: setup DB → generate → `mvn test` → report | semua di atas |
| `scripts/curl_snap_demo.sh` | 6 permintaan contoh ke endpoint `/v1.0/loan-funding` | aplikasi hasil generate yang sedang berjalan |

## 4. Lapisan C: keluaran alat

| Artefak | Isi | Asal |
|---|---|---|
| `generated/` | Proyek Spring Boot 3.5 / Java 17 lengkap. **Dibuat ulang setiap `run_all.sh`.** Di-commit supaya hasilnya bisa dibaca tanpa menjalankan alat. Karena alatnya deterministik, men-generate ulang menghasilkan file yang identik. | `migrator generate` |
| `generated/src/main/java/com/p2p/domain/` | Entity `Loan`, `Lender`, `Borrower`, `Funding` | `schema.sql` |
| `.../repository/` | Interface repository per tabel | `schema.sql` |
| `.../infrastructure/` | `InMemory*Repository` (Map, gaya semester lalu) dan `Jdbc*Repository` (PostgreSQL) | `schema.sql` |
| `.../service/` | Satu `@Service` per procedure, yaitu terjemahan logikanya | `procs/*.sql` |
| `.../api/` | Controller, request, dan rute SNAP per procedure | `mapping.yaml` |
| `.../support/`, `.../snap/` | Salinan runtime | `java-runtime/` |
| `generated/src/test/` | Testkit kesetaraan dan salinan `.feature` | `java-runtime/equivalence`, `corpus/features` |
| `generated/migrator-analysis.json` | Hasil analisis statis per procedure: status, jebakan, dan jejak baris SQL asal | `analyzer.py` |
| `generated/target/equivalence-results.jsonl` | Satu baris per (skenario × backend): setara atau tidak, beserta selisihnya. *Tidak di-commit, karena keluaran build.* | `mvn test` |
| `laporan/laporan-kelayakan.md` | Putusan **LAYAK/TIDAK LAYAK** per procedure dan metrik kebermanfaatan, keefektifan, dan efisiensi | `migrator report` |
| `experiments/manual/` | Protokol eksperimen migrasi manual dan `waktu.csv` (masih kosong, diisi Orang B) | Tim. Dibaca oleh `report` untuk metrik efisiensi. |

## 5. Alur proses pembuktian

Inti alat ini adalah satu skenario yang dijalankan tiga kali dengan data awal yang sama:

```
                       skenario .feature (Given data awal, When panggil procedure)
                                   │
          ┌────────────────────────┼────────────────────────┐
          ▼                        ▼                        ▼
   backend LEGACY            backend JDBC              backend MEMORY
   CALL procedure lama       Service Java baru         Service Java baru
   di p2p_legacy             + Jdbc repository         + InMemory (Map) repository
   (ACUAN KEBENARAN)         di p2p_baru               tanpa database
          │                        │                        │
          ▼                        ▼                        ▼
   hasil + dump tabel        hasil + dump tabel        hasil + dump tabel
          └──────────┬─────────────┴──────────┬─────────────┘
                     ▼                        ▼
              bandingkan legacy vs jdbc   bandingkan legacy vs memory
              (SQLSTATE sama, pesan sama untuk P0001, isi tabel identik per PK)
                     └──────────┬─────────────┘
                                ▼
                   equivalence-results.jsonl ──▶ laporan kelayakan
```

Procedure dinyatakan **LAYAK pensiun** bila: (1) seluruh isinya bisa diterjemahkan tanpa konstruksi yang tidak didukung, dan (2) semua skenarionya setara di kedua backend. Setelah itu, procedure tersebut tidak perlu dipasang lagi di database baru. Itulah sebabnya `p2p_baru` sejak awal tidak berisi procedure.

## 6. Dari plan ke commit

| Task di plan | Commit | Artefak yang lahir |
|---|---|---|
| — | `cf5c29b` | spec, plan |
| 0 | `487a24d` | kerangka `migrator/`, `java-runtime/` |
| 1 | `82f39c3` | `corpus/schema.sql`, `corpus/procs/` (5), `scripts/pg.sh`, `setup_db.sh` |
| 2 | `19a5fa5` | `parse.py`, `ir.py` |
| 3 | `2a01532` | `analyzer.py` |
| 4 | `b9dd868` | `support/`, `gen/java.py`, template domain, repository, dan service |
| 5 | `9cac661` | `equivalence/`, `gen/java_test.py` |
| 6 | `721a030` | `corpus/features/` (34 skenario) |
| 7 | `bbdfd8c` | `snap/`, `schema-api.sql` |
| 8 | `d3266d5` | `gen/java_api.py`, `mapping.py`, `corpus/mapping.yaml` |
| 9 | `2cb1ace` | `report.py`, `run_all.sh`, `experiments/manual/`, `docs/demo.md`, `curl_snap_demo.sh` |
| review | `08f2ebf` | perbaikan temuan review, 3 procedure `uji_*`, `uji_temuan_review.feature` (jumlah skenario menjadi 40) |
| — | `faeced3` | `docs/proses-pengerjaan-dengan-ai.md` |
| — | (commit ini) | `spike/`, `spike-pglast/`, `generated/`, dokumen ini, `README.md` |

## 7. Ke mana harus melihat bila ada yang berubah

| Bila … | Ubah | Lalu |
|---|---|---|
| menambah procedure | `corpus/procs/` + skenario di `corpus/features/` + entri di `mapping.yaml` | `scripts/run_all.sh` |
| ada konstruksi PL/pgSQL baru yang ditolak | `parse.py` → `ir.py` → `analyzer.py` → `gen/java.py` (sesuai urutan alur) | tambahkan skenario jebakannya |
| kode kasus SNAP resmi berbeda | `corpus/mapping.yaml` saja | generate ulang |
| perilaku NULL atau pembulatan di Java salah | `java-runtime/.../support/Sql.java` | skenario yang gagal harus menjadi setara |
| laporan butuh metrik baru | `report.py` | `migrator report` |
