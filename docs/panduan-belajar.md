# Panduan Belajar dari Repo StoredProcedure2JavaDDD

Panduan ini untuk mahasiswa (semester 3 ke atas) yang ingin memahami bukan hanya *apa* yang dibangun repo ini, tapi juga *bagaimana* dan *kenapa*. Repo ini sengaja dibiarkan lengkap: spike sekali pakai, spec, plan, commit per task, temuan review, dan branch `spike-migrator`. Semuanya adalah bahan belajar.

> **Cara memakai panduan ini.** Kerjakan modul berurutan. Setiap modul berisi **Tujuan**, **Baca**, **Jalankan**, **Latihan**, dan **Pertanyaan refleksi**. Jangan lewati latihan "prediksi dulu, baru jalankan". Di situlah sebagian besar pemahaman terbentuk.

---

## Tujuan pembelajaran

Setelah menyelesaikan panduan ini, kamu bisa:

| # | Kemampuan | Mata kuliah terkait |
|---|---|---|
| T1 | Menjelaskan semantik transaksi, `NULL` (logika tiga nilai), dan tipe `numeric` di PostgreSQL, serta kenapa hal-hal itu berbahaya saat migrasi | Basis Data |
| T2 | Memakai library parsing (pglast) untuk mengubah kode sumber menjadi pohon sintaks, lalu menjadi IR milik sendiri | Teknik Kompilasi / Bahasa Pemrograman |
| T3 | Menulis analisis statis sederhana: inferensi tipe, nullability, deteksi pola berisiko | Teknik Kompilasi |
| T4 | Menghasilkan kode dengan template secara deterministik | Rekayasa Perangkat Lunak |
| T5 | Mengenali pola DDD (entity, repository, service, unit of work) dan dua implementasi repository (Map dan JDBC) | Pemrograman Berorientasi Objek / DDD |
| T6 | Membuktikan bahwa dua implementasi setara dengan *differential testing* (uji kesetaraan) dan mengukur kepekaan uji dengan mutasi | Pengujian Perangkat Lunak |
| T7 | Merancang API mengikuti standar industri (SNAP BI) | Pemrograman Web / Integrasi Sistem |
| T8 | Bekerja dengan AI secara bertanggung jawab: AI menulis kode, manusia menentukan kebenaran dan membuktikannya | Etika dan Praktik Profesional |

---

## Persiapan (± 30 menit)

Yang dibutuhkan: Python ≥ 3.12, Java 17+, Maven, binary PostgreSQL 16, dan Git.

```bash
git clone https://github.com/joelianmin/StoredProcedure2JavaDDD.git
cd StoredProcedure2JavaDDD
python3 -m venv migrator/.venv && migrator/.venv/bin/pip install -e migrator
export PG_BIN=/path/ke/postgresql/bin      # folder yang berisi psql, initdb, pg_ctl
scripts/pg.sh start
scripts/run_all.sh                          # harus berakhir dengan 40 Scenarios passed
```

Supaya perintah di modul-modul berikutnya lebih pendek:

```bash
alias psql-lama='$PG_BIN/psql -h localhost -p 5433 -d p2p_legacy'
alias psql-baru='$PG_BIN/psql -h localhost -p 5433 -d p2p_baru'
alias migrator='migrator/.venv/bin/migrator'
```

Kalau `run_all.sh` belum hijau, berhenti dulu dan perbaiki lingkungannya. Semua modul berikutnya mengandalkan langkah ini.

---

## Peta jalan

```
M0 Gambaran ─▶ M1 Masalah ─▶ M2 Jebakan semantik ─▶ M3 Parsing ─▶ M4 Analisis ─▶ M5 Generator
                                                                                      │
M10 Bekerja dengan AI ◀─ M9 Laporan & metrik ◀─ M8 REST SNAP ◀─ M7 Kesetaraan ◀─ M6 DDD
                                                                                      │
                                              M11 Proyek akhir: tambah procedure baru ◀┘
```

| Modul | Topik | Waktu | Tujuan |
|---|---|---|---|
| M0 | Gambaran dan peta artefak | 1 jam | — |
| M1 | Masalah nyata: bug transaksi | 1,5 jam | T1 |
| M2 | Jebakan semantik J1–J12 | 2 jam | T1 |
| M3 | Parsing dua tingkat dan IR | 3 jam | T2 |
| M4 | Analisis statis | 2 jam | T3 |
| M5 | Code generation | 2 jam | T4 |
| M6 | Keluaran bergaya DDD | 2 jam | T5 |
| M7 | Pembuktian kesetaraan dan mutasi | 3 jam | T6 |
| M8 | REST API ala SNAP BI | 1,5 jam | T7 |
| M9 | Laporan dan metrik kualitas | 1 jam | T6 |
| M10 | Bekerja dengan AI | 1,5 jam | T8 |
| M11 | Proyek akhir | 4–6 jam | semua |

---

## M0. Gambaran dan peta artefak

**Tujuan:** tahu apa saja isi repo dan bagaimana bagian-bagiannya saling terhubung.

**Baca:**
1. [`README.md`](../README.md)
2. [`docs/artefak-dan-alur.md`](artefak-dan-alur.md), terutama §1 (diagram tiga lapisan) dan §5 (alur pembuktian)

**Latihan:**
1. Tanpa melihat dokumen, gambar ulang diagram alur `procedure .sql → … → laporan` di kertas. Lalu bandingkan dengan aslinya.
2. Untuk setiap folder tingkat atas, tulis satu kalimat: *siapa yang membuatnya* (tim, AI, atau alat) dan *siapa yang memakainya*.

**Refleksi:** kenapa `generated/` boleh di-commit, padahal biasanya hasil generate tidak di-commit? Petunjuk: deterministik.

---

## M1. Masalah nyata: bug transaksi

**Tujuan:** memahami kenapa logika di stored procedure *lebih aman* daripada versi Java + Map semester lalu, dan kenapa migrasi bisa merusaknya.

**Baca:**
- `corpus/procs/danai_pinjaman.sql`. Perhatikan urutannya: saldo lender **didebit dulu**, baru setelah itu ada validasi "melebihi sisa target".
- `FundingService.fund()` di repo semester lalu (`c6-core-engine-p2p-lending`), yang urutannya sama.

**Jalankan** (prediksi hasilnya dulu sebelum menjalankan):

```sql
-- psql-lama
SELECT id, balance FROM lender;                          -- catat saldo LD001
-- siapkan data jika kosong (lihat Background di corpus/features/danai_pinjaman.feature)
CALL danai_pinjaman('FX1', 'L002', 'LD001', 5000000);    -- melebihi sisa target
SELECT id, balance FROM lender;                          -- apakah saldo LD001 berubah?
```

**Latihan:**
1. Jelaskan dengan kata-katamu sendiri kenapa saldo tidak berubah. Sebutkan istilah *atomicity* dan *rollback*.
2. Di Java dengan `Map` biasa, apa yang terjadi bila exception dilempar setelah `lender.setBalance(...)`? Tulis 10 baris Java yang membuktikannya.
3. Cari di `java-runtime/.../support/SnapshotUnitOfWork.java` bagaimana repo ini membuat `Map` berperilaku seperti transaksi.

**Refleksi:** apakah memperbaiki urutan di procedure (validasi dulu, baru debit) membuat `UnitOfWork` tidak diperlukan lagi? Kenapa jawabannya tetap "perlu"?

---

## M2. Jebakan semantik J1–J12

**Tujuan:** mengalami sendiri perbedaan perilaku PostgreSQL dan Java.

**Baca:** tabel jebakan J1–J12 di spec, `docs/superpowers/specs/2026-11-21-migrator-plpgsql-ke-ddd-java-design.md`.

**Jalankan:** tulis dulu tebakanmu di kolom "Prediksi", lalu jalankan di `psql-lama`.

| Query | Jebakan | Prediksi | Hasil |
|---|---|---|---|
| `SELECT NULL > 1;` | J1 | | |
| `SELECT 5 + NULL;` | J2 | | |
| `SELECT round(2.5), round(-2.5), round(1.005, 2);` | J5 | | |
| `SELECT 1::numeric / 3, 100::numeric / 12;` | J6 | | |
| `SELECT 7 / 2, -7 / 2;` | J7 | | |
| `SELECT 'a' \|\| NULL;` | J8 | | |
| `SELECT 10.0 = 10.00;` | J9 | | |
| `SELECT 1.005::numeric(15,2), 12345678901234.5::numeric(15,2);` | J11 | | |

Lalu lakukan hal yang sama di Java (`jshell`):

```java
new BigDecimal("2.5").setScale(0, RoundingMode.HALF_EVEN)   // bandingkan dengan HALF_UP
new BigDecimal("10.0").equals(new BigDecimal("10.00"))      // vs compareTo
BigDecimal.ONE.divide(new BigDecimal(3))                    // apa yang terjadi?
```

**Latihan:**
1. Untuk setiap baris di atas, temukan helper di `java-runtime/.../support/Sql.java` yang meniru perilaku PostgreSQL. Contohnya `Sql.div` untuk J6 dan `Sql.fitNumeric` untuk J11.
2. Baca `Sql.and` dan `Sql.or`. Buat tabel kebenaran tiga nilai (TRUE/FALSE/NULL) untuk AND dan OR, lalu cocokkan dengan kodenya.

**Refleksi:** jebakan mana yang paling mungkin lolos dari *code review* manusia? Kenapa?

---

## M3. Parsing dua tingkat dan IR

**Tujuan:** memahami bagaimana teks SQL menjadi struktur data yang bisa diolah program.

**Baca (urut):**
1. `spike-pglast/migrator.py`: versi satu file yang lebih mudah dibaca dulu
2. `migrator/src/migrator/ir.py`: definisi IR (semua node `frozen dataclass`)
3. `migrator/src/migrator/parse.py`: versi lengkap

**Jalankan:** lihat pohon mentah pglast, lalu bandingkan dengan IR.

```bash
migrator/.venv/bin/python - <<'EOF'
import json, pglast
src = open('corpus/procs/danai_pinjaman.sql').read()
print(json.dumps(pglast.parse_plpgsql(src), indent=1)[:1500])   # tingkat 1: struktur PL/pgSQL
print(pglast.parse_sql("SELECT balance - 5 FROM lender WHERE id = 'x'")[0])  # tingkat 2: SQL
EOF

migrator/.venv/bin/python -c "from pathlib import Path; from migrator.parse import *; \
T=parse_schema(Path('corpus/schema.sql').read_text()); \
p=parse_procedure(Path('corpus/procs/danai_pinjaman.sql').read_text(), T, 'danai_pinjaman.sql'); \
[print(s) for s in p.body]"
```

**Latihan:**
1. Kenapa perlu dua tingkat? Tunjukkan bagian mana yang hanya berupa *string* di keluaran `parse_plpgsql`, sehingga harus di-parse lagi dengan `parse_sql`.
2. Kenapa repo ini membuat IR sendiri, dan tidak langsung memakai pohon pglast di generator? Sebutkan minimal dua alasan. Petunjuk: perubahan nama `returningList` → `returningClause` di pglast (lihat `_ret()` di `parse.py`).
3. **Uji penolakan.** Salin `danai_pinjaman.sql` ke folder sementara, tambahkan `EXECUTE 'SELECT 1';` atau loop `FOR` tanpa `ORDER BY`, lalu jalankan `migrator generate --procs <folder itu> ...`. Apa pesan errornya dan berapa exit code-nya? Kenapa "gagal eksplisit" lebih baik daripada "menebak"?
4. Bandingkan pendekatan grammar buatan sendiri (`spike/plsql.lark`) dengan pglast. Tuliskan untung-rugi keduanya dalam satu paragraf.

**Refleksi:** apa yang hilang kalau IR **tidak** menyimpan nomor baris (`line`)? Cek pemakaiannya di Java hasil generate.

---

## M4. Analisis statis

**Tujuan:** memahami bagaimana alat "tahu" sebuah nilai bisa NULL atau tidak, dan bagaimana alat mendeteksi jebakan.

**Baca:** `migrator/src/migrator/analyzer.py`, terutama `Env`, `nullable`, dan bagian yang memanggil `TrapHit`.

**Jalankan:**

```bash
python3 -m json.tool generated/migrator-analysis.json | less     # jebakan per procedure per baris
```

**Latihan (prediksi dulu):**
1. Baca `corpus/procs/uji_null_cabang.sql`. Saat `p_flag = false`, apa yang terjadi pada `IF v_extra > 50`? Apa hasilnya di PostgreSQL, dan apa yang akan terjadi di Java kalau `v_extra` dianggap pasti tidak NULL?
2. Ini adalah **bug kritis** yang ditemukan reviewer di versi awal alat. Lihat perbaikannya:
   ```bash
   git show 08f2ebf -- migrator/src/migrator/analyzer.py | less
   ```
   Jelaskan konsep *definite assignment*: variabel dianggap pasti terisi hanya bila terisi di **semua** cabang.
3. Tulis procedure kecil yang membuat analyzer melaporkan J1, J10, dan J12. Cek hasilnya di `migrator-analysis.json`.

**Refleksi:** analyzer ini sengaja *konservatif*, artinya kalau ragu, nilai dianggap bisa NULL. Apa konsekuensinya terhadap keterbacaan kode, dan apa konsekuensinya terhadap kebenaran?

---

## M5. Code generation

**Tujuan:** memahami bagaimana IR menjadi kode Java dengan template, dan kenapa determinisme penting.

**Baca:**
- `migrator/src/migrator/gen/java.py`: penerjemahan statement dan ekspresi
- `migrator/src/migrator/gen/templates/Service.java.j2`
- `generated/src/main/java/com/p2p/service/DanaiPinjamanService.java`

**Jalankan:**

```bash
# 1. Mode readable vs tidak
migrator generate --schema corpus/schema.sql --procs corpus/procs --mapping corpus/mapping.yaml \
  --features corpus/features --runtime java-runtime --out /tmp/gen-raw --no-readable
diff generated/src/main/java/com/p2p/service/DanaiPinjamanService.java \
     /tmp/gen-raw/src/main/java/com/p2p/service/DanaiPinjamanService.java

# 2. Determinisme: generate dua kali, bandingkan
migrator generate --schema corpus/schema.sql --procs corpus/procs --mapping corpus/mapping.yaml \
  --features corpus/features --runtime java-runtime --out /tmp/gen-2
diff -r -x target -x mvn-test.log -x generate-seconds.txt generated /tmp/gen-2 && echo IDENTIK
```

**Latihan:**
1. Telusuri satu baris: komentar `// danai_pinjaman.sql:27` di Java → baris 27 di SQL → node IR-nya → fungsi di `gen/java.py` yang menghasilkannya.
2. Cari bagaimana `AND` diterjemahkan (evaluasi pendek dengan ternary). Kenapa `Sql.and(a, b)` biasa bisa **salah**? Petunjuk: skenario "AND berhenti di sisi kiri yang false" di `uji_temuan_review.feature`.
3. Buka `naming.py`. Apa yang terjadi pada kolom bernama `class`? Bagaimana dengan parameter `p_id` dan variabel `v_id` dalam procedure yang sama?

**Refleksi:** "Kode hasil generate jangan diedit manual." Kenapa? Apa yang seharusnya diubah kalau hasilnya salah?

---

## M6. Keluaran bergaya DDD

**Tujuan:** membandingkan hasil generate dengan desain DDD semester lalu.

**Baca:** `generated/src/main/java/com/p2p/` (`domain/`, `repository/`, `infrastructure/`, `service/`) dan `support/UnitOfWork*.java`.

**Latihan:**
1. Buat tabel perbandingan untuk `Loan`, `LoanRepository`, `InMemoryLoanRepository`, dan `FundingService`/`DanaiPinjamanService`: versi semester lalu vs versi hasil generate. Apa yang sama, dan apa yang berbeda (tipe uang, transaksi, nama)?
2. Kenapa `InMemory*Repository` memakai `TreeMap`, bukan `HashMap`? Petunjuk: loop `FOR ... ORDER BY` dan urutan baris.
3. Apa gunanya `findByIdForUpdate` di `Jdbc*Repository`? Jelaskan masalah *lost update* dengan dua transaksi yang berjalan bersamaan.
4. Jalankan aplikasi dengan dua profil, `--spring.profiles.active=memory` dan `postgres`. Apa yang berganti, dan apa yang tetap?

**Refleksi:** service hasil generate masih bergaya "skrip transaksi" (*transaction script*), belum *rich domain model* dengan perilaku di entity. Apa yang dibutuhkan agar generator bisa menghasilkan `loan.fund(...)`? Spec menyebut "file pemetaan". Seperti apa bentuknya menurutmu?

---

## M7. Pembuktian kesetaraan dan mutasi

**Tujuan:** memahami *differential testing*: kebenaran tidak ditulis ulang, tapi dibandingkan dengan sistem lama.

**Baca:**
- `corpus/features/danai_pinjaman.feature`
- `java-runtime/.../equivalence/EquivalenceSteps.java`, `LegacyBackend.java`, `TableDiff.java`

**Jalankan:**

```bash
mvn -q -f generated/pom.xml test
tail -5 generated/target/equivalence-results.jsonl
```

**Latihan: uji mutasi manual.** Untuk setiap mutasi: ubah satu hal di `generated/`, prediksi skenario mana yang gagal, jalankan `mvn -q -f generated/pom.xml test`, lalu pulihkan dengan generate ulang.

| # | Mutasi | Prediksimu |
|---|---|---|
| 1 | `Sql.java`: `RoundingMode.HALF_UP` → `HALF_EVEN` | |
| 2 | `SnapshotUnitOfWork.java`: jangan pulihkan snapshot saat exception | |
| 3 | `DanaiPinjamanService.java`: ganti `Sql.ge(...)` menjadi `Sql.gt(...)` pada pengecekan status FUNDED | |
| 4 | `Sql.format`: NULL dicetak `null`, bukan `<NULL>` | |
| 5 | `Sql.div`: bagi dengan skala 2 saja | |

1. Mutasi mana yang **tidak** tertangkap? Tulis skenario baru di `corpus/features/` yang menangkapnya, lalu jalankan `scripts/run_all.sh`.
2. Kenapa satu skenario dijalankan terhadap **dua** backend Java (jdbc dan memory)? Bug macam apa yang hanya muncul di salah satunya?
3. Definisi "setara" di repo ini: SQLSTATE sama, pesan sama untuk `P0001`, dan isi tabel identik. Apa yang *tidak* tercakup? Petunjuk: urutan efek samping, konkurensi.

**Refleksi:** uji kesetaraan membuktikan "sama dengan sistem lama", bukan "benar menurut bisnis". Kapan keduanya berbeda? Bagaimana skenario `J4` (bug transaksi lama) menunjukkannya?

---

## M8. REST API ala SNAP BI

**Tujuan:** melihat bagaimana procedure menjadi endpoint yang mengikuti standar industri.

**Baca:** `corpus/mapping.yaml`, `java-runtime/.../snap/SnapHeaderFilter.java`, `SnapExceptionHandler.java`, dan `generated/.../api/LoanFundingController.java`.

**Jalankan:** lihat `docs/demo.md` bagian 5, lalu `scripts/curl_snap_demo.sh`.

**Latihan:**
1. Uraikan `responseCode` `4039101` menjadi tiga bagian: HTTP, kode layanan, dan kode kasus.
2. Kenapa pesan asli `RAISE` tidak dikirim ke klien? Kaitkan dengan keamanan.
3. Kirim request yang sama dua kali dengan `X-EXTERNAL-ID` yang sama. Apa hasilnya, dan di tabel mana catatannya tersimpan? Kenapa idempotensi penting untuk transaksi uang?
4. Ubah satu kode kasus di `mapping.yaml`, generate ulang, lalu lihat perubahannya. Rusak format YAML-nya dengan sengaja: berapa exit code `migrator generate`?

**Refleksi:** kode layanan 90–94 adalah ekstensi non-standar. Bagaimana cara memverifikasinya terhadap dokumen resmi sebelum dipakai sungguhan?

---

## M9. Laporan dan metrik kualitas

**Tujuan:** mengaitkan hasil teknis dengan kriteria penilaian: kebermanfaatan, keefektifan, dan efisiensi (ISO 25010 *quality in use*).

**Baca:** `laporan/laporan-kelayakan.md`, `migrator/src/migrator/report.py`, dan `experiments/manual/README.md`.

**Latihan:**
1. Untuk setiap metrik di laporan, tunjukkan dari file mana angkanya berasal.
2. Lakukan eksperimen manual sesuai protokol: migrasikan satu procedure ke Java dengan tangan, catat waktunya di `experiments/manual/waktu.csv`, lalu jalankan `migrator report`. Berapa kali lebih cepat alatnya, setelah memperhitungkan waktu review?
3. Uji mutasi "ditunda" karena mode spike. Rancang cara otomatis menghitung "% mutasi tertangkap" dari latihan M7.

**Refleksi:** metrik mana di laporan yang paling mudah "dipercantik" tanpa benar-benar meningkatkan kualitas? Bagaimana mencegahnya?

---

## M10. Bekerja dengan AI

**Tujuan:** belajar dari proses pembuatan repo ini. Seluruh kodenya ditulis AI, sedangkan manusia memutuskan dan membuktikan.

**Baca:**
- [`docs/proses-pengerjaan-dengan-ai.md`](proses-pengerjaan-dengan-ai.md)
- spec, lalu plan (bandingkan keduanya: plan adalah turunan spec)

**Jalankan:**

```bash
git log --reverse --format='%h %ad %s' --date=format:'%H:%M'   # satu task = satu commit
git show 2a01532 --stat                                         # contoh satu task
git diff spike-migrator main --stat                             # apa yang ditambah setelah merge
git show 08f2ebf                                                # perbaikan temuan review
```

**Latihan:**
1. Dari kronologi di catatan proses, pilih tiga pesan pendek tim yang paling mengubah arah proyek. Apa yang akan terjadi tanpa pesan-pesan itu?
2. Reviewer independen menemukan 1 bug kritis dan 6 bug penting yang lolos dari AI pelaksana. Pilih satu, lalu jelaskan kenapa bug itu "terlihat benar" dan bagaimana skenario uji membuktikan perbaikannya.
3. Bagian "Keputusan yang dibuat AI sendiri" berisi 9 butir. Pilih dua dan putuskan: setuju atau tidak? Beri alasan.
4. Bandingkan `spike-pglast/migrator.py` (spike, sekali pakai) dengan `migrator/` (versi terstruktur). Apa yang ditambahkan saat spike "dinaikkan" menjadi alat?

**Refleksi:** prinsip "alat murni deterministik, source code tidak boleh keluar" adalah alasan alat ini tidak memakai LLM saat dijalankan. Jawab pertanyaan penguji: *"Kenapa tidak minta ChatGPT menerjemahkan saja?"*

---

## M11. Proyek akhir: tambah satu procedure baru

Tujuannya menjalani seluruh alur sendiri. Contoh procedure: `tarik_saldo_lender(p_lender_id, p_jumlah)` atau `hitung_denda_keterlambatan(p_loan_id, p_hari)`.

**Langkah:**

1. **Tulis procedure** di `corpus/procs/` hanya dengan konstruksi yang didukung (lihat tabel subset di spec). Minimal harus memuat satu `SELECT INTO`, satu `IF/ELSIF`, satu `UPDATE`, dan satu `RAISE`.
2. **Tulis skenario** di `corpus/features/<nama>.feature`. Satu skenario per cabang IF, ditambah minimal dua jebakan (misalnya NULL dan pembulatan).
3. **Tambahkan pemetaan** di `corpus/mapping.yaml`: layanan baru, kode layanan, dan error.
4. **Jalankan** `scripts/run_all.sh`. Target: semua skenario setara, dan procedure berstatus **LAYAK** di laporan.
5. **Uji kepekaan:** lakukan satu mutasi di service hasil generate, lalu pastikan skenariomu menangkapnya.
6. **Bonus:** pakai satu konstruksi yang belum didukung (misalnya `CASE`), lalu tambahkan dukungannya di `parse.py` → `ir.py` → `analyzer.py` → `gen/java.py`, lengkap dengan skenario kesetaraannya.

**Kriteria selesai:**

| Kriteria | Bukti |
|---|---|
| Procedure terjemah tanpa konstruksi tak didukung | `migrator generate` exit 0 |
| Semua cabang dan jebakan diuji | daftar skenario ↔ cabang IF |
| Setara di jdbc dan memory | output `mvn test`, laporan LAYAK |
| Skenario peka terhadap mutasi | catatan mutasi yang tertangkap |
| Endpoint SNAP berjalan | hasil `curl` |
| Bisa menjelaskan | jawab pertanyaan uji mandiri di bawah |

---

## Pertanyaan uji mandiri (persiapan ujian lisan)

1. Apa beda `parse_plpgsql` dan `parse_sql`, dan kenapa keduanya dibutuhkan?
2. Apa itu IR, dan apa yang akan rusak kalau generator membaca pohon pglast secara langsung?
3. Sebutkan tiga jebakan J1–J12 dan cara repo ini menanganinya di Java.
4. Kenapa `10.0 = 10.00` harus dibandingkan dengan `compareTo`, bukan `equals`?
5. Bagaimana `SnapshotUnitOfWork` meniru rollback? Apa keterbatasannya? Petunjuk: konkurensi.
6. Apa definisi "setara" di repo ini? Sebutkan satu hal yang tidak dicakup definisi itu.
7. Kenapa uji kesetaraan lebih kuat daripada unit test biasa untuk migrasi, dan kapan justru lebih lemah?
8. Bagaimana alat memutuskan sebuah procedure LAYAK dipensiunkan?
9. Apa arti `responseCode` `4099100`?
10. Apa saja peran manusia dalam proyek yang kodenya 100% ditulis AI?

---

## Glosarium singkat

| Istilah | Arti |
|---|---|
| **AST / pohon sintaks** | Struktur pohon hasil parsing kode sumber |
| **IR** (*Intermediate Representation*) | Bentuk antara milik alat sendiri, independen dari library parsing |
| **Logika tiga nilai** | TRUE, FALSE, dan NULL (tidak diketahui). Di SQL, `NULL > 1` menghasilkan NULL, bukan FALSE. |
| **Definite assignment** | Analisis yang memastikan variabel pasti sudah diisi di semua jalur sebelum dipakai |
| **Differential testing** | Menguji dengan membandingkan dua implementasi pada masukan yang sama |
| **Uji mutasi** | Sengaja menanam bug kecil untuk mengukur apakah test mampu menangkapnya |
| **Unit of Work** | Pola yang mengelompokkan perubahan menjadi satu transaksi: semua berhasil atau semua batal |
| **SQLSTATE** | Kode error standar SQL, misalnya `P0001` (RAISE), `22003` (overflow numerik), `23502` (NOT NULL) |
| **Idempotensi** | Request yang sama dikirim berkali-kali tetap hanya berefek satu kali |
| **Spike** | Prototipe sekali pakai untuk menjawab pertanyaan desain, bukan fondasi produk |
