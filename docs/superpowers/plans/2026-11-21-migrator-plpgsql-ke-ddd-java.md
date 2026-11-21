# Migrator PL/pgSQL → DDD Java + REST SNAP BI: Rencana Implementasi (VERSI SPIKE)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Mode spike [Tim]:** testing diminimalkan. Tidak ada TDD, unit test, oracle test, test MockMvc, atau uji mutasi. Setiap task diakhiri **satu pemeriksaan cepat** (perintah dan hasil yang diharapkan). Satu-satunya "test" yang dipertahankan adalah **pemeriksaan kesetaraan** (Task 6), karena itu fitur inti alat (spec G3), bukan test pengembangan. Kode spike tidak harus kokoh. Kalau nanti dijadikan versi final, kembali ke spec dan tambahkan test yang dipangkas (lihat "Ditunda").

**Goal:** CLI Python deterministik yang mem-parse stored procedure PL/pgSQL (pglast) dan meng-generate aplikasi Spring Boot bergaya DDD dengan endpoint ala SNAP BI, ditambah pemeriksaan kesetaraan terhadap procedure asli di PostgreSQL dan laporan kelayakan pensiun.

**Architecture:** Parse dua tingkat (`parse_plpgsql` untuk struktur, `parse_sql` untuk SQL/ekspresi) → IR sendiri → analyzer (tipe, nullability, jebakan) → generator Jinja2 → proyek Maven + runtime Java tetap yang disalin dari `java-runtime/`. Kesetaraan diperiksa oleh Cucumber di proyek hasil generate dengan tiga backend: legacy `CALL`, Java + JDBC, dan Java + Map.

**Tech Stack:** Python 3.12, pglast 8.9, Jinja2 3.1.6, PyYAML 6.0.4 · Java 17 (JDK 21), Maven 3.9, Spring Boot 3.5.3, Cucumber 7.15.0 · PostgreSQL 18.7 (Homebrew `postgresql@18`).

**Spec:** `docs/superpowers/specs/2026-11-21-migrator-plpgsql-ke-ddd-java-design.md`

## Global Constraints

- Tanpa LLM dan tanpa jaringan saat alat jalan. Input sama → output identik (urutan file dan key JSON terurut).
- `numeric` → `BigDecimal` selalu. Tidak boleh ada `double`/`float`.
- Konstruksi di luar spec §6.3 atau melanggar aturan korpus → `Unsupported`, dan procedure ditandai TIDAK LAYAK. Tidak boleh menebak.
- Skema hanya boleh: PK satu kolom, `NOT NULL`, tipe `text`/`numeric(p,s)`/`integer`/`boolean`. Variabel procedure memakai `numeric` polos. Teks hanya dibandingkan dengan `=`/`<>`.
- Paket Java: `com.p2p.{domain,repository,infrastructure,service,api,support,snap}`, dan `com.p2p.equivalence` untuk test.
- SQLSTATE yang dibandingkan: `P0001` (+ pesan identik), `23502`, `23505`, `22012`, `22003`.
- SNAP: `responseCode` = HTTP + kode layanan + kode kasus. Sukses `200xx00` "Successful"; header hilang `400xx02`; format salah `400xx01`; `X-EXTERNAL-ID` ganda `409xx00`; RAISE tanpa pemetaan `403xx00`; lainnya `500xx00`. Kode layanan: `90` loan-submission, `91` loan-funding, `92` loan-disbursement, `93` loan-repayment, `94` loan-cancellation. Pesan asli RAISE tidak dikirim ke klien.
- Koneksi PostgreSQL: `localhost:5432`, user OS, tanpa password. Database `p2p_legacy` dan `p2p_baru`.
- Setiap file hasil generate diberi header `// DIHASILKAN OTOMATIS oleh migrator dari <sumber>`, dan setiap pernyataan diberi komentar `// <file>.sql:<baris>`.

## Review Focus

Spike: tidak ada test tambahan. Lima hal ini cukup dicek mata saat demo, dan menjadi test pertama bila spike dilanjutkan:
1. Argumen `<NULL>` ke procedure (perilaku tiga backend harus sama).
2. Petik/backslash di literal dan pesan RAISE (Java harus ter-compile).
3. Nominal melebihi `numeric(15,2)` → `22003` juga di backend Map.
4. Nama kolom/parameter yang merupakan keyword Java.
5. `X-EXTERNAL-ID` sama dari partner berbeda atau hari berikutnya → diterima.

---

## Struktur file

```
18PLPTubesIde/
├── scripts/{setup_db.sh, run_all.sh}
├── corpus/{schema.sql, procs/*.sql, mapping.yaml, features/*.feature}
├── migrator/                       # Python: pyproject.toml, src/migrator/{ir,parse,analyzer,mapping,report,cli}.py, src/migrator/gen/{project.py, java.py, templates/}
├── java-runtime/                   # Java tetap: src/main/java/com/p2p/{support,snap,equivalence}
├── generated/                      # output (.gitignore)
└── laporan/laporan-kelayakan.md
```

Jadwal: **Minggu 1** Task 0–3 · **Minggu 2** Task 4–6 · **Minggu 3** Task 7–8 · **Minggu 4** Task 9.

---

### Task 0: Repo dan kerangka

- [ ] `git init`, `.gitignore` (`.venv/`, `__pycache__/`, `target/`, `generated/`, `spike*/.venv/`, `spike*/build/`).
- [ ] `migrator/pyproject.toml`: dependensi `pglast==8.9`, `jinja2==3.1.6`, `pyyaml==6.0.4`; entry point `migrator = "migrator.cli:main"`. Pasang dengan `python3 -m venv migrator/.venv && migrator/.venv/bin/pip install -e migrator`.
- [ ] `java-runtime/pom.xml`: parent Spring Boot 3.5.3, `release 17`, dependensi `web`, `jdbc`, `postgresql`, `cucumber-java:7.15.0`.
- [ ] **Cek:** `mvn -q -f java-runtime/pom.xml compile` → BUILD SUCCESS.
- [ ] Commit: `chore: kerangka spike`

### Task 1: PostgreSQL + korpus

- [ ] **MINTA IZIN MANUSIA**, lalu `brew install postgresql@18 && brew services start postgresql@18`.
- [ ] `corpus/schema.sql` persis seperti berikut:

```sql
CREATE TABLE borrower (id text PRIMARY KEY, name text NOT NULL, kyc_verified boolean NOT NULL,
  income numeric(15,2), balance numeric(15,2) NOT NULL, loan_limit numeric(15,2) NOT NULL);
CREATE TABLE lender (id text PRIMARY KEY, name text NOT NULL, balance numeric(15,2) NOT NULL);
CREATE TABLE loan (id text PRIMARY KEY, borrower_id text NOT NULL, amount numeric(15,2) NOT NULL,
  collected_amount numeric(15,2) NOT NULL, tenor_months integer NOT NULL,
  interest_rate numeric(5,4) NOT NULL, status text NOT NULL);
CREATE TABLE funding (id text PRIMARY KEY, loan_id text NOT NULL, lender_id text NOT NULL,
  amount numeric(15,2) NOT NULL);
```

- [ ] `java-runtime/src/main/resources/schema-api.sql`: tabel `snap_external_id(partner_id text, external_id text, day date, PRIMARY KEY (partner_id, external_id, day))`.
- [ ] Lima procedure di `corpus/procs/`. Signature dan pesan RAISE harus persis karena mapping dan feature bergantung padanya. Draf ditulis AI, lalu **direview Orang B**:

| Procedure | Signature | Perilaku dan pesan RAISE |
|---|---|---|
| `ajukan_pinjaman` | `(p_loan_id text, p_borrower_id text, p_jumlah numeric, p_tenor integer)` | borrower tidak ada → `'Borrower tidak ditemukan: %'`; `IF NOT v_kyc` → `'Borrower belum verifikasi KYC'`; **`IF v_income <= 3000000`** (NULL lolos, jebakan J1) → `'Penghasilan borrower tidak memenuhi syarat'`; `IF p_jumlah > v_limit` → `'Limit pinjaman borrower tidak mencukupi'`; INSERT loan (collected 0, rate 0.1000, `'PENDING'`); `loan_limit -= p_jumlah` |
| `danai_pinjaman` | `(p_funding_id text, p_loan_id text, p_lender_id text, p_jumlah numeric)` | `'Loan tidak ditemukan: %'`; `'Lender tidak ditemukan: %'`; `IF v_status <> 'PENDING'` → `'Loan % tidak dalam status PENDING'`; `IF v_saldo < p_jumlah` → `'Saldo lender tidak mencukupi'`; **debit lender dulu**, lalu `IF p_jumlah > v_target - v_terkumpul` → `'Pendanaan melebihi sisa target pinjaman'`; INSERT funding; collected += jumlah; bila penuh → `'FUNDED'` |
| `cairkan_pinjaman` | `(p_loan_id text)` | `'Loan tidak ditemukan: %'`; `IF v_status <> 'FUNDED'` → `'Hanya pinjaman berstatus FUNDED yang bisa dicairkan'`; saldo borrower += amount; `'DISBURSED'` |
| `bayar_pinjaman` | `(p_loan_id text)` | `'Loan tidak ditemukan: %'`; `IF v_status <> 'DISBURSED'` → `'Loan belum dicairkan'`; `v_bunga := round(v_amount * v_rate * v_tenor / 12, 2)`; `v_total := v_amount + v_bunga`; `IF v_saldo < v_total` → `'Saldo borrower tidak mencukupi'`; saldo borrower −= total; `'REPAID'` |
| `batalkan_pinjaman` | `(p_loan_id text)` | `'Loan tidak ditemukan: %'`; `IF v_status = 'DISBURSED' OR v_status = 'REPAID'` → `'Pinjaman tidak dapat dibatalkan setelah dicairkan'`; `FOR r IN SELECT id, lender_id, amount FROM funding WHERE loan_id = p_loan_id ORDER BY id LOOP` refund lender `END LOOP`; `DELETE FROM funding WHERE loan_id = p_loan_id`; `loan_limit += v_amount`; `status 'CANCELLED', collected_amount 0` |

Semua `SELECT INTO` memakai `WHERE id = …` dan langsung diikuti `IF NOT FOUND THEN RAISE`.
- [ ] `scripts/setup_db.sh`: buat ulang `p2p_legacy` (schema + procs) dan `p2p_baru` (schema + `schema-api.sql`).
- [ ] **Cek:** `scripts/setup_db.sh` tanpa ERROR. Lalu di `p2p_legacy`, di dalam `BEGIN … ROLLBACK`, insert 1 borrower (income 5000000, limit 50000000) dan 1 lender (saldo 20000000), jalankan `CALL ajukan…`, `CALL danai…` (penuh), `CALL cairkan…`, lalu `SELECT status` → `DISBURSED`.
- [ ] Commit: `feat: korpus dan setup PostgreSQL`

### Task 2: Parser → IR (dua tingkat)

Bangun ulang dari `spike-pglast/migrator.py` sebagai titik awal, tapi perbaiki semua pintasan yang tercatat di spec §13.

- [ ] `ir.py`: dataclass sesuai spec §6.1. Nama ekspresi: `Const`, `VarRef`, `FieldRef`, `ColumnRef`, `Found`, `Binary`, `Unary`, `IsNull`, `FuncCall`. Nama pernyataan: `Assign`, `If`, `SelectInto`, `Update`, `Insert`, `Delete`, `Raise`, `ForQuery`, `Return`, masing-masing dengan `line`. `Column` membawa `precision`/`scale`.
- [ ] `parse.py`: `parse_schema(sql) -> dict[str, Table]`, `parse_procedure(sql, tables, source_file) -> Procedure`, `Unsupported(construct, line, detail)`. Aturan di spec §6.3 dan §6.6 ditegakkan: WHERE kesetaraan, `SELECT INTO` lewat PK, FOR wajib `ORDER BY` PK, INSERT dengan daftar kolom, dan tolak `now()`/sequence/JOIN/EXECUTE/EXCEPTION/COMMIT/FK/CHECK/DEFAULT.
- [ ] **Cek:** `migrator/.venv/bin/python -c "…parse kelima procedure korpus dan print ringkasan IR…"` → lima procedure ter-parse tanpa error. Juga `EXECUTE 'x'` → `Unsupported`.
- [ ] Commit: `feat(migrator): parser dua tingkat ke IR`

### Task 3: Analyzer

- [ ] `analyzer.py`:
  - `type_of(expr, …)` dengan promosi integer∘numeric → numeric.
  - `nullable(expr, ctx)`: parameter selalu nullable; variabel non-null hanya bila semua definisinya non-null, termasuk "guarded select" (SELECT INTO kolom NOT NULL yang langsung diikuti `IF NOT FOUND THEN RAISE`).
  - Deteksi jebakan J1–J12 sesuai aturan di spec §7.2.
  - `analyze(proc, tables) -> Analysis(procedure, source_file, status, reason, tables_read, tables_written, branch_count, constructs, traps)` dengan `to_dict()` terurut.
- [ ] **Cek:** print analisis `ajukan_pinjaman` → ada J1 yang menyebut `v_income`. Analisis `danai_pinjaman` → ada J3, J4, J10, J11, dan J12.
- [ ] Commit: `feat(migrator): analyzer`

### Task 4: Runtime Java `support` + generator domain/repository/service

- [ ] `java-runtime/.../support/`:
  - `ProcedureException(sqlState, message)`
  - `Sql`: helper null-safe, `div` meniru skala PostgreSQL (port `select_div_scale`: `rscale = max(16 − qweight×4, scale1, scale2, 0)`, HALF_UP), `round` HALF_UP, `fitNumeric(v,p,s)` dengan `22003`, `format` dengan `%`/`%%`/`<NULL>`, dan operasi integer `22003`/`22012`
  - `SqlStates` (PSQLException → ProcedureException)
  - `Snapshotable`, `UnitOfWork`, `SnapshotUnitOfWork`, `SpringTxUnitOfWork`, `UnitOfWorkConfig` (profil `memory`/`postgres`)

  Bisa mulai dari `spike-pglast/runtime/`.
- [ ] Generator (`gen/project.py`, `gen/java.py`, template Jinja2):
  - `pom.xml`, `Application`, properties (`spring.profiles.default=memory`)
  - per tabel: entity (`copy()`), repository interface (`save`, `insert`, `findById`, `findAll`, serta `findBy…`/`deleteBy…` sesuai pola WHERE korpus), `InMemory*` (`TreeMap`, snapshot, cek NOT NULL dan `fitNumeric`), `Jdbc*` (`JdbcTemplate`, upsert `ON CONFLICT`)
  - per procedure: `@Service <Proc>Service.execute(...)` di dalam `unitOfWork.run`, dengan aturan terjemahan seperti spike (RHS UPDATE dihitung dulu, `found` diperbarui)
  - Keterbacaan spec §13: pakai Java biasa bila analyzer memastikan operand non-null, selain itu pakai helper `Sql.*`.
  - Runtime disalin ke `src/main/java`. Keyword Java diberi akhiran `_`. `migrator-analysis.json` ditulis terurut.
- [ ] CLI: `migrator generate --schema … --procs … [--mapping …] [--features …] --runtime java-runtime --out generated` (exit 2 bila ada unsupported).
- [ ] **Cek:** generate dua kali ke dua folder lalu `diff -r` → tidak ada beda. Lalu `mvn -q -f generated/pom.xml compile` → BUILD SUCCESS.
- [ ] Commit: `feat(generator): Spring Boot domain, repository, service`

### Task 5: Testkit kesetaraan

- [ ] `java-runtime/.../equivalence/` (disalin ke `src/test/java` proyek hasil generate):
  - `Backend` (`reset`, `insert`, `call → Outcome`, `dump`)
  - `BackendFactory` (ServiceLoader, urutan `[legacy, jdbc, memory]`)
  - `Outcome(success, sqlState, message)` dengan `equivalentTo` (pesan hanya dibandingkan untuk P0001)
  - `SchemaInfo`, `PgSql` (insert dengan cast, `CALL`, dump `ORDER BY pk`, truncate), `LegacyBackend`, `TestDatabases`
  - `TableDiff` (BigDecimal dibandingkan dengan `compareTo`)
  - `EquivalenceRecorder` (`target/equivalence-results.jsonl`)
  - `EquivalenceSteps` dengan step persis:
    - `Given tabel {string} berisi:`
    - `When procedure {string} dipanggil dengan:` (DataTable: nama parameter lalu nilai; `<NULL>` = null)
    - `Then procedure lama berhasil`
    - `Then procedure lama gagal dengan {string} {string}`
    - `Then hasil lama dan baru setara`
- [ ] Generator test-side: `GeneratedSchema`, `RepositorySet`, `ServiceInvoker`, `RowConverters`, `GeneratedBackends`, `RunCucumberTest`, file `META-INF/services`, serta menyalin `corpus/features/*.feature`.
- [ ] **Cek:** satu skenario `danai` "melebihi sisa target" (`P0001`) → `mvn -q -f generated/pom.xml test` lulus, dan jsonl berisi `jdbc` dan `memory` dengan `equal: true`.
- [ ] Commit: `feat(verifier): testkit kesetaraan tiga backend`

### Task 6: Skenario kesetaraan (inti pembuktian) — **Orang A**

- [ ] `corpus/features/`: satu feature per procedure, satu skenario per cabang IF (lihat `branch_count`). Wajib ada:
  - `danai`: sebagian, penuh → FUNDED, melebihi target (saldo lender kembali, J4), saldo kurang, ID funding ganda `23505`
  - `ajukan`: income NULL lolos (J1), income 3000000 ditolak
  - `bayar`: bunga dengan pembulatan .005 (J5/J6)
  - `batalkan`: 3 funding dari 2 lender (FOR)
  - `overflow`: collected > 9999999999999.99 → `22003` (J11)
- [ ] **Cek:** `scripts/setup_db.sh && migrator generate … && mvn -q -f generated/pom.xml test` → 0 failures. Kalau ada perbedaan, **PostgreSQL legacy adalah acuan**: perbaiki generator atau runtime.
- [ ] Commit: `test(korpus): skenario kesetaraan`

### Task 7: Runtime SNAP

- [ ] `java-runtime/.../snap/`:
  - `SnapAmount(value, currency)`: `toBigDecimal(field)` dengan regex `^\d{1,13}\.\d{2}$` dan `IDR`
  - `SnapResponse`, `SnapRequestException(http, caseCode, message)`, `ErrorRule(prefix, http, caseCode, message)`
  - `SnapErrors`: `code`, `success`, `fromRequest`, `fromProcedure` (prefix-match untuk P0001, bawaan sesuai Global Constraints, pesan asli ke log)
  - `SnapRoutes`
  - `ExternalIdRegistry` + `InMemory…` (profil memory) + `Jdbc…` (profil postgres, `ON CONFLICT DO NOTHING`)
  - `SnapHeaderFilter`: cek urut `Content-Type`, `Authorization: Bearer`, `X-TIMESTAMP` (ISO 8601 + offset), `X-SIGNATURE` (base64), `X-PARTNER-ID` (≤36), `X-EXTERNAL-ID` (digit ≤36), `CHANNEL-ID` (digit ≤5); lalu registrasi per partner per hari (Asia/Jakarta); ID tetap terpakai walau service gagal
  - `SnapExceptionHandler`, `SnapConfig` (Clock bean)
- [ ] **Cek:** `mvn -q -f java-runtime/pom.xml compile` → BUILD SUCCESS. Pengujian perilakunya di Task 8 lewat curl.
- [ ] Commit: `feat(runtime): konvensi SNAP BI`

### Task 8: Generator API + mapping.yaml

- [ ] `mapping.py`: muat dan validasi `corpus/mapping.yaml` (struktur spec §6.7: `service`, `serviceCode`, `money`, `errors`). Kesalahan → exit 3 dengan pesan.
- [ ] Generate per procedure yang punya mapping:
  - `api/<Service>Request` (record: `partnerReferenceNo` + field camelCase; parameter `money` bertipe `SnapAmount`)
  - `api/<Service>Controller` (`POST /v1.0/<service>`, field wajib null → `400xx02`, `ProcedureException` → `SnapErrors.fromProcedure` dengan `ErrorRule` statis)
  - `api/GeneratedSnapRoutes`
- [ ] `corpus/mapping.yaml` untuk lima procedure (kode layanan 90–94). Kode kasus dipilih **Orang A** dari tabel resmi SNAP.
- [ ] **Cek:** jalankan `mvn -q -f generated/pom.xml spring-boot:run -Dspring-boot.run.profiles=postgres` lalu tiga curl ke `/v1.0/loan-funding`:
  1. header lengkap + loan ada → `"responseCode":"2009100"`
  2. loan `L999` → kode sesuai mapping
  3. ulangi `X-EXTERNAL-ID` yang sama → `4099100`
- [ ] Commit: `feat(generator): endpoint REST ala SNAP BI`

### Task 9: Laporan, run_all, eksperimen manual, demo

- [ ] `report.py` + `migrator report --project generated [--manual experiments/manual/waktu.csv] --out laporan/laporan-kelayakan.md`. Isi per procedure: status, konstruksi, jebakan, jumlah skenario setara per varian, LOC service, dan putusan **LAYAK / TIDAK LAYAK — alasan**. Ditambah ringkasan metrik §9 (tanpa metrik mutasi). Tanpa timestamp.
- [ ] `scripts/run_all.sh`: setup_db → generate (catat detik) → mvn test → report.
- [ ] **Tim (Orang B):** migrasi manual `bayar_pinjaman` dan catat menitnya di `experiments/manual/waktu.csv` (`procedure,cara,tahap,menit`). Lakukan hal yang sama dengan alat.
- [ ] `docs/demo.md` (±10 menit): bug transaksi repo lama → generate → skenario "melebihi target" setara → curl SNAP → laporan.
- [ ] **Cek:** `scripts/run_all.sh` → laporan menyatakan kelima procedure **LAYAK**.
- [ ] Commit: `docs: laporan kelayakan dan naskah demo`

---

## Ditunda (dipangkas karena mode spike)

Dikembalikan bila spike dilanjutkan menjadi versi final:
- Unit test Python (parser, analyzer, generator) dan test determinisme otomatis
- Oracle test `Sql` terhadap PostgreSQL (`div`, `round`, `format`)
- Test MockMvc per endpoint
- Uji mutasi (spec §8) dan metrik "% mutasi tertangkap"
- Skenario untuk setiap jebakan J1–J12 dan untuk lima butir Review Focus
- Pemetaan transisi status ke pola State lewat `mapping.yaml`
