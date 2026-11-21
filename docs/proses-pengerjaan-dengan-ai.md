# Proses Pengerjaan Spike dengan AI

Dokumen ini mencatat jalannya pembuatan spike dalam satu sesi Claude Code (model Claude Opus) pada 21 November 2026, pukul 07.39 sampai 13.10 WIB. Isinya ada dua: bagaimana tim berinteraksi dengan AI, dan apa saja yang dikerjakan AI secara rinci. Tujuannya supaya tim bisa menjelaskan asal-usul setiap bagian repo saat demo dan tanya-jawab, dan supaya jelas mana keputusan tim dan mana keputusan AI.

Semua kode di repo ini ditulis oleh AI. Tim memutuskan masalah, arah, batasan, dan persetujuan.

---

## 1. Pola interaksi

Sesi dibuka dengan perintah `pakai superpowers brainstorm`. Perintah ini memuat *skill* brainstorming yang membuat AI bertanya dulu sebelum membangun apa pun. Alur kerjanya mengikuti urutan skill *superpowers*:

```
brainstorming ──▶ spec (dokumen desain) ──▶ plan (rencana kerja) ──▶ eksekusi ──▶ review akhir ──▶ merge & publish
   (tanya-jawab)     disetujui tim            disetujui tim          "native"     subagent baru      atas perintah tim
```

Ciri interaksinya:

- **Pesan tim pendek, AI yang menjabarkan.** Sebagian besar pesan tim hanya beberapa kata ("domain bfsi", "3", "a", "sesuai"). AI menanggapi dengan pilihan yang sudah dijabarkan beserta untung-ruginya dan rekomendasinya. Tim cukup memilih.
- **Divergen dulu, baru konvergen.** Atas permintaan tim ("kita berpikir divergen dulu"), AI mengumpulkan puluhan ide sebelum menyaring. Penyaringan dilakukan bertahap: ISO 25010 → domain BFSI → konteks modernisasi → ide nomor 3 (PL/SQL ke service code).
- **Batasan dari tim mengubah desain secara drastis.** Beberapa kalimat pendek dari tim membalik arah proyek. "pakai postgresql" dan "awalnya postgresql stored procedure" menghapus kebutuhan Oracle dan interpreter semantik. "semester lalu sudah belajar ddd dengan reponya pakai map" menjadi dasar bentuk kode hasil generate.
- **Spike untuk "membuat terbayang".** Dua kali tim meminta atau menyetujui spike sekali pakai sebelum memutuskan ("biar terbayang buat versi spike dulu", "yang mudah dulu saja").
- **Tim bisa menyela di tengah pekerjaan.** Dua pesan dikirim saat AI sedang bekerja: "ini versi spike. minimalkan testing" saat rencana disusun, dan "postgresql sudah ada versi 16" saat eksekusi. AI langsung menyesuaikan.
- **Setiap tahap butuh persetujuan.** Desain disetujui ("setuju", "sesuai"), cara eksekusi dipilih ("native"), dan publikasi repo diperintahkan secara eksplisit ("buat repo baru ... dan push", "buat public dan langsung merge").

---

## 2. Kronologi percakapan

Waktu dalam WIB. Kolom kanan merangkum tanggapan AI.

| Waktu | Pesan tim | Tanggapan dan hasil dari AI |
|---|---|---|
| 07.39 | pakai superpowers brainstorm | Memuat skill brainstorming. Folder masih kosong, jadi AI menanyakan isi tugasnya. |
| 07.41 | tugas besar untuk pemanfaatan parsing library dan code generator untuk berbagai usecase nyata. | Menjelaskan pola "parse input → generate kode", lalu menanyakan ketentuan dosen. |
| 07.45 | boleh full ai. tim 2 orang. apa yang jadi peran utama tim? | Peran tim: memilih masalah, merancang bahasa input/output, menguji hasil, dan mampu menjelaskannya. Mengusulkan pembagian sisi input dan sisi output. |
| 07.48 | fokus dulu memilih masalahnya. bisa diawali dari pendekatan iso 25010 | Tabel 9 karakteristik ISO 25010 (versi 2023), masing-masing dengan contoh masalah dan bentuk solusi parsing + generate. |
| 07.51 | kita berpikir divergen dulu | Sekitar 30 ide, dikelompokkan per karakteristik ISO 25010. |
| 07.53 | domain bfsi | Putaran ide khusus BFSI: QRIS, SWIFT/ISO 20022, ISO 8583, SNAP BI, COBOL, RegTech (AML, POJK, XBRL, SLIK), kredit dan risiko. |
| 07.56 | dalam context modernization | Ide modernisasi per lapisan: kode, data, antarmuka/integrasi, UI. |
| 07.58 | 3 | AI salah tafsir: dikira lapisan ke-3 (antarmuka), lalu menjabarkan ide integrasi. |
| 07.59 | PL/SQL stored procedure → service code maksudnya 3 yang ini | AI mengoreksi diri, lalu memecah ide ini menjadi variasi arah transformasi, fokus pemahaman, dan ekstraksi aturan bisnis. |
| 08.01 | biar terbayang buat versi spike dulu | **Spike 1 (Lark):** procedure denda kredit PL/SQL di-parse dengan Lark lalu di-generate menjadi service Java. Temuan: NULL di `hari_tunggakan` membuat PL/SQL masuk ELSE, sedangkan Java akan crash. Dari sini muncul gagasan bahwa yang bernilai adalah *pembuktian kesetaraan*. |
| 08.04 | karena full ai, mana peran utama tim? | Peran tim: pemilik realisme, pembuat keputusan pemetaan, skeptis dan pembukti, penilai kualitas, dan penjelas. Pembagian Orang A (penjaga kebenaran) dan Orang B. |
| 08.06 | kebermanfaatan, keefektifan dan efisiensi | Memetakan ketiga kriteria dosen ke model *quality in use* ISO 25010 beserta cara mengukurnya. Mengangkat pertanyaan yang pasti muncul: "kenapa tidak langsung pakai ChatGPT?" |
| 08.09 | alat murni deterministik. source code tidak boleh keluar. ai buat alatnya. | Ditetapkan sebagai tiga prinsip proyek. Prinsip kedua menjadi jawaban untuk pertanyaan "kenapa tidak pakai ChatGPT": bank tidak boleh mengirim source code ke LLM publik. Menawarkan tiga opsi pembanding kesetaraan. |
| 08.12 | oracle express bisa diinstall pada laptop ini? | Memeriksa laptop: i5 4 thread, RAM 8 GB, macOS 12, disk tersisa 4,2 GB. Kesimpulan: belum bisa karena disk penuh. Tidak ada yang dipasang. |
| 08.14 | pakai postgresql | Target berubah menjadi Oracle → Java + PostgreSQL. Mengusulkan interpreter semantik Oracle sebagai pembanding. |
| 08.15 | stored procedure tidak digunakan lagi skenarionya | Skenario "pensiun total". Muncul gagasan **laporan kelayakan pensiun** sebagai gerbang sebelum procedure dimatikan. |
| 08.17 | tidak pakai oracle rdbms. tetapi awalnya postgresql stored procedure. ... semester lalu sudah belajar ddd dengan reponya pakai map. ... | Sumber diganti menjadi PL/pgSQL, sehingga procedure asli bisa dijalankan langsung di PostgreSQL dan interpreter tidak diperlukan. Output berbentuk DDD dengan dua repository: Map dan PostgreSQL. |
| 08.19 | semester lalu topiknya peer to peer lending | Mengusulkan korpus procedure P2P lending. |
| 08.24 | (tautan repo c6-core-engine-p2p-lending) | AI membaca repo semester lalu. Temuan: (1) bug transaksi di `FundingService.fund()`, saldo didebit sebelum validasi; (2) uang dihitung dengan `double`; (3) file `.feature` bisa dipakai ulang sebagai kasus bisnis. |
| 08.26 | 4 minggu | Cakupan 5 procedure. Tiga pendekatan: Java+ANTLR, Python+Lark, Python+pglast. |
| 08.27 | 3 | Memilih **Python + pglast**. AI menulis draf spec, lalu menjalankan subagent "builder check" yang membacanya dari sudut pandang pembangun. Tiga pertanyaan diputuskan AI, satu diajukan ke tim. |
| 08.35 | yang mudah dulu saja. karena ini versi spike. | Opsi A: proyek baru bergaya sama. **Spike 2 (pglast):** `danai_pinjaman` → IR → 7 file Java → `javac` → 11/11 cek lulus, termasuk skenario bug transaksi lama. Temuan: kode yang setia pada NULL sulit dibaca, sehingga muncul mode *readable*. |
| 08.40 | setuju. buatkan rest api. | Desain disetujui. Menanyakan maksud REST API: (A) bagian dari kode hasil generate, atau (B) migrator dijadikan server. |
| 08.41 | A pakai springboot | Menambahkan §6.7 REST API Spring Boot dengan profil `memory` dan `postgres`. |
| 08.42 | endpoint menurut snap bi | Riset web soal SNAP BI. Catatan: SNAP hanya mendefinisikan layanan pembayaran, jadi operasi pinjaman diperlakukan sebagai ekstensi non-standar yang mengikuti konvensinya. |
| 08.43 | a | SNAP hanya format (header, `responseCode` 7 digit, objek `amount`, idempotensi `X-EXTERNAL-ID`), tanpa kriptografi. |
| 08.44 | sesuai | Spec final disetujui. AI menyusun rencana kerja (plan). |
| ~08.50 | *(menyela)* ini versi spike. minimalkan testing | Plan ditulis ulang menjadi versi spike dengan 10 task. TDD, unit test, MockMvc, dan uji mutasi dipindah ke bagian "Ditunda". Pemeriksaan kesetaraan tetap dipertahankan karena itu fitur yang dinilai. |
| 09.35 | native | Eksekusi: AI mengerjakan semua task sendiri di sesi ini (lihat §3.5). |
| *(saat eksekusi)* | *(menyela)* postgresql sudah ada versi 16 | AI memakai binary PostgreSQL 16 yang sudah ada, tidak memasang lewat Homebrew. Cluster sendiri di `.pgdata` port 5433. |
| 13.07 | buat repo baru StoredProcedure2JavaDDD dan push | Membuat repo GitHub (awalnya privat) dan push branch. |
| 13.09 | buat public dan langsung merge | Merge ke `main`, uji ulang 40/40, push, repo dijadikan publik, branch lokal dihapus. |

---

## 3. Yang dikerjakan AI secara rinci

### 3.1 Riset dan pemeriksaan

| Kegiatan | Hasil |
|---|---|
| Membaca repo semester lalu di GitHub | Menemukan pola DDD tim (entity, `InMemory*Repository` berbasis `Map`, service, Cucumber), bug urutan debit/validasi di `FundingService`, dan pemakaian `double` untuk uang. Bug transaksi itu menjadi skenario J4 di korpus. |
| Memeriksa spesifikasi laptop | Membuktikan Oracle tidak muat (disk 99% penuh). Keputusan ini akhirnya membawa ke PostgreSQL. |
| Pencarian web SNAP BI | Format `responseCode`, header wajib, dan objek `amount`. Sumbernya dokumentasi vendor, karena dokumen resmi BI tidak bisa diakses langsung. **Kode layanan 90–94 dan kode kasus di `mapping.yaml` adalah ilustrasi** dan harus dicek tim ke tabel resmi. |
| Pengecekan pglast | Memastikan pglast 8.9 (parser PostgreSQL 18) bisa mem-parse PL/pgSQL (`parse_plpgsql`) dan SQL di dalamnya (`parse_sql`). |

### 3.2 Dua spike sekali pakai (tidak di-push)

1. **`spike/`, Lark:** grammar PL/SQL subset ditulis sendiri, lalu procedure denda di-generate menjadi Java. Spike ini menunjukkan bahaya NULL dan menggeser fokus proyek ke pembuktian kesetaraan.
2. **`spike-pglast/`, pglast:** membuktikan alur dua tingkat (struktur PL/pgSQL, lalu ekspresi SQL) ke IR milik sendiri ke Java yang ter-compile, dengan 11/11 cek lulus.

Kedua folder hanya ada di laptop dan sengaja tidak masuk repo.

### 3.3 Dokumen desain (spec)

`docs/superpowers/specs/2026-11-21-migrator-plpgsql-ke-ddd-java-design.md` berisi:

- tujuan, non-goal, dan prinsip (deterministik, lokal, AI hanya membangun alat)
- subset PL/pgSQL yang didukung, dan konstruksi yang ditolak secara eksplisit
- **katalog jebakan semantik J1–J12**: NULL di IF, aritmetika NULL, SELECT INTO tanpa baris, rollback saat RAISE, `round`, pembagian numeric, pembagian integer, konkatenasi NULL, beda skala, format `%` pada RAISE, pembulatan `numeric(p,s)`, dan error bawaan database
- definisi "setara": isi tabel identik per PK, dan hasil sama (sukses, atau SQLSTATE sama, ditambah pesan sama untuk `P0001`)
- bentuk output DDD, repository Map dan JDBC, UnitOfWork, REST SNAP, dan laporan kelayakan pensiun
- metrik untuk ketiga kriteria dosen

Setelah draf selesai, sebuah **subagent baru** membacanya sebagai "calon pembangun" untuk mencari bagian yang masih membuatnya menebak. Hasilnya lima pertanyaan. Tiga diputuskan AI sendiri (§4 butir 2–4), satu berupa daftar usulan untuk dicek tim, dan satu hanya bisa dijawab tim: apakah kode harus menyatu dengan repo lama, atau cukup proyek baru yang bergaya sama. Tim memilih proyek baru.

### 3.4 Rencana kerja (plan)

`docs/superpowers/plans/2026-11-21-migrator-plpgsql-ke-ddd-java.md` berisi 10 task yang dijadwalkan untuk 4 minggu, versi spike dengan testing minimal. Rencana ini juga mencatat apa saja yang ditunda.

### 3.5 Eksekusi (mode "native")

AI mengerjakan setiap task, menjalankan satu pemeriksaan per task, lalu commit:

| Commit | Task | Isi |
|---|---|---|
| `cf5c29b` | — | Spec dan plan |
| `487a24d` | 0 | Kerangka proyek Python (`migrator/`) dan runtime Java (`java-runtime/`) |
| `82f39c3` | 1 | `corpus/schema.sql`, 5 procedure P2P lending, `scripts/pg.sh` dan `setup_db.sh` (cluster PG16 sendiri, database `p2p_legacy` dan `p2p_baru`) |
| `19a5fa5` | 2 | `parse.py`: pglast dua tingkat ke `ir.py` (dataclass immutable). Konstruksi di luar subset ditolak dengan `Unsupported`. |
| `2a01532` | 3 | `analyzer.py`: inferensi tipe, analisis nullability, deteksi jebakan J1–J12 per baris |
| `b9dd868` | 4 | Runtime `com.p2p.support` (`Sql` untuk logika tiga nilai, port `select_div_scale` PostgreSQL, `fitNumeric`, `format`; `ProcedureException`; `UnitOfWork`). Generator Jinja2: entity, repository interface, `InMemory*` (TreeMap + snapshot), `Jdbc*` (upsert, `FOR UPDATE`), dan `@Service` per procedure. |
| `9cac661` | 5 | Testkit kesetaraan Cucumber dengan tiga backend: **legacy** (`CALL` di PostgreSQL), **jdbc**, dan **memory**. Membandingkan hasil dan dump seluruh tabel. |
| `721a030` | 6 | 34 skenario kesetaraan untuk 5 procedure, nama skenario menyebut jebakan yang diuji |
| `bbdfd8c` | 7 | Runtime SNAP: validasi header, idempotensi per partner per hari (Asia/Jakarta, tabel `snap_external_id`), `responseCode` |
| `d3266d5` | 8 | Generator controller SNAP per procedure dari `corpus/mapping.yaml`, dengan validasi mapping (exit 3 bila tidak valid) |
| `2cb1ace` | 9 | `migrator report` (laporan kelayakan), `scripts/run_all.sh`, protokol eksperimen manual, `docs/demo.md`, `scripts/curl_snap_demo.sh` |
| `08f2ebf` | review | Perbaikan temuan review akhir (§3.6) |

### 3.6 Review akhir dan perbaikan

Seorang **reviewer subagent baru** (konteks bersih, tanpa membawa asumsi AI pelaksana) memeriksa seluruh branch. Temuannya:

| Tingkat | Temuan | Perbaikan |
|---|---|---|
| Critical | Analisis nullability tidak *sound*: variabel bisa dianggap pasti tidak NULL padahal hanya diisi di salah satu cabang IF | Analisis *definite assignment* terstruktur. Skenario `uji_null_cabang` dibuktikan gagal dulu, lalu lulus. |
| Important | `FOUND` tidak di-set setelah loop `FOR` | Flag `_iteratedN`, dengan skenario `uji_found_for` |
| Important | `AND`, `OR`, dan `COALESCE` dievaluasi penuh, padahal PostgreSQL memotongnya (bisa memicu bagi nol yang seharusnya tidak terjadi) | Ternary dengan evaluasi pendek, dengan skenario `uji_and_pendek` |
| Important | Variabel `NOT NULL`/`CONSTANT` dan `DEFAULT` parameter diterima diam-diam | Ditolak eksplisit sebagai tidak didukung |
| Important | Nama tabel atau kolom yang berupa keyword PostgreSQL atau perlu dikutip | `sql_ident()` memakai daftar keyword pglast |
| Important | Satu procedure yang error membuat seluruh generate gagal | Error ditangkap per procedure. Exit code 2 bila ada yang tidak didukung. |
| Important | *Lost update* di repository JDBC | `findByIdForUpdate` (`SELECT … FOR UPDATE`) |

Setiap perbaikan dibuktikan dengan skenario yang gagal sebelum diperbaiki dan lulus sesudahnya. Hasil akhirnya **40/40 skenario setara** untuk jdbc dan memory, dan semua procedure **LAYAK**.

**Minor yang sengaja ditunda** (untuk dibahas tim):

- urutan loop `FOR` di Map mengikuti urutan *locale C*, padahal PostgreSQL mengikuti *collation* database
- error di luar procedure (misalnya JSON rusak) belum diberi body SNAP
- prioritas SQLSTATE bila `23502` dan `22003` terjadi bersamaan
- profil `memory` tidak aman untuk request bersamaan

### 3.7 Publikasi

1. Membuat repo GitHub `StoredProcedure2JavaDDD`, lalu push.
2. Fast-forward merge `spike-migrator` ke `main`, menjalankan ulang `scripts/run_all.sh` (40/40 lulus), push `main`, dan menjadikan repo publik.
3. Mengingatkan tim bahwa email author commit dan path lokal default di `scripts/pg.sh` kini terlihat publik. Path itu bisa ditimpa dengan `PG_BIN`.

---

## 4. Keputusan yang dibuat AI sendiri

Keputusan-keputusan ini tidak diminta tim secara eksplisit. Tim perlu memeriksanya karena tim yang harus mempertahankannya saat tanya-jawab.

1. **Python + pglast**, bukan grammar buatan sendiri, karena parser resmi PostgreSQL menjamin sintaksnya benar. *(Tim memilih opsi 3 dari usulan AI.)*
2. **Nilai yang dibuat database dilarang di korpus**, seperti `now()`, sequence, dan UUID. Tanpa larangan ini, isi kedua database tidak akan pernah sama persis.
3. **Skema dibatasi** pada PK, `NOT NULL`, dan tipe dasar, tanpa FK, `CHECK`, atau `DEFAULT`.
4. **Loop wajib memakai `ORDER BY`** supaya urutan di PostgreSQL dan Map sama.
5. **Mode *readable***: bila nilai pasti tidak NULL, generator menghasilkan Java biasa, bukan helper `Sql.*`. Opsi `--no-readable` mematikannya.
6. **Kode layanan SNAP 90–94 dan kode kasus** hanya ilustrasi dan harus diverifikasi.
7. **Pesan asli `RAISE` tidak dikirim ke klien**, hanya `responseMessage` versi SNAP.
8. **Konvensi nama Java**: prefix `p_`/`v_` dibuang, keyword Java diberi akhiran `_`, dan nama lokal hasil generate memakai prefix `_`.
9. **Pemangkasan testing** (atas arahan "minimalkan testing"): tidak ada unit test, MockMvc, atau uji mutasi. Semuanya dicatat di bagian "Ditunda" pada plan.

---

## 5. Hambatan teknis yang ditemui AI

| Masalah | Penyelesaian |
|---|---|
| Git 2.10 di laptop tidak mengenal `git init -b` | `git init` lalu `git checkout -b` |
| Di pglast, `returningList` berganti nama menjadi `returningClause` | Helper `_ret()` yang menerima kedua nama |
| Surefire melaporkan "Tests run: 0", sehingga skenario gagal tidak menggagalkan build | Runner JUnit yang memanggil `io.cucumber.core.cli.Main.run` secara langsung |
| Bash 3.2 di macOS menolak array dan perintah kompleks | Ditulis sebagai file script (`scripts/curl_snap_demo.sh`) |
| Grammar Lark di spike 1 tidak mendukung minus unary | Ditambahkan ke grammar |
| Disk laptop penuh, sehingga Oracle dan Docker tidak bisa dipasang | Proyek beralih ke PostgreSQL, memakai binary PG16 yang sudah ada |

---

## 6. Angka sesi

- Durasi: sekitar 5,5 jam, dari brainstorm sampai repo publik. Brainstorm sampai desain disetujui memakan sekitar 65 menit. Eksekusi 10 task sampai perbaikan review selesai sekitar 50 menit.
- Pesan tim: sekitar 30 pesan, kebanyakan 1–10 kata.
- Pemanggilan alat oleh AI: sekitar 140, terdiri dari Bash 92, Write 20, Edit 16, Read 4, skill 4, subagent 2, dan web search 1.
- Hasil: sekitar 2.650 baris Python (migrator), 1.190 baris Java (runtime), 670 baris korpus (SQL, Gherkin, YAML), 40 skenario kesetaraan, dan 8 procedure LAYAK.

---

## 7. Yang tetap menjadi pekerjaan tim

AI membangun alatnya. Tim yang menjamin kebenarannya:

- **Orang A (penjaga kebenaran):** meninjau 40 skenario. Apakah ada cabang atau jebakan yang belum diuji? Juga memverifikasi kode kasus SNAP di `corpus/mapping.yaml` terhadap tabel resmi.
- **Orang B (pemilik realisme dan pengukur):** menilai apakah korpus procedure cukup realistis, lalu menjalankan eksperimen migrasi manual dan mengisi `experiments/manual/waktu.csv` untuk metrik efisiensi.
- **Berdua:** memutuskan nasib minor yang ditunda (§3.6), memutuskan apakah uji mutasi dikembalikan untuk memperkuat keefektifan, dan berlatih menjelaskan alur parse → IR → analyzer → generator → pembuktian kesetaraan.

## 8. Pelajaran tentang bekerja dengan AI

1. **Batasan yang jelas lebih berharga daripada instruksi panjang.** Kalimat "alat murni deterministik. source code tidak boleh keluar" menentukan arsitektur sekaligus menjadi argumen kebermanfaatan.
2. **Konteks nyata tim mengubah segalanya.** Tautan repo semester lalu menghasilkan gaya kode, skenario, dan bug transaksi yang menjadi bukti kebermanfaatan.
3. **Spike murah, jadi pakailah untuk memutuskan.** Kedua spike dibuat dalam hitungan menit dan masing-masing mengubah desain.
4. **Kode AI yang "terlihat benar" tetap perlu dibuktikan.** Reviewer independen menemukan satu bug kritis dan beberapa bug penting yang lolos dari AI pelaksana. Testkit kesetaraan adalah cara membuktikannya.
5. **AI bisa salah tafsir pesan pendek** (kasus "3" pada pukul 07.58). Koreksi singkat sudah cukup untuk meluruskannya.
