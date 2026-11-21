# Desain: Migrator Stored Procedure PL/pgSQL → Kode DDD Java (P2P Lending)

- **Tanggal:** 2026-11-21
- **Konteks:** Tugas besar "pemanfaatan parsing library dan code generator untuk usecase nyata"
- **Tim:** 2 mahasiswa semester 3 · **Durasi:** 4 minggu · **AI:** boleh dipakai penuh untuk membangun alat
- **Kriteria penilaian dosen:** kebermanfaatan, keefektifan, efisiensi

Penanda keputusan di dokumen ini:
- **[Tim]** = diputuskan tim dalam diskusi
- **[Usulan]** = usulan perancang (Claude) yang belum dikonfirmasi tim, boleh dikoreksi
- **[Pembangun]** = diserahkan ke pembangun

---

## 1. Maksud dan alasan

Sebuah platform P2P lending (domain BFSI) versi lama menaruh logika bisnis (pengajuan, pendanaan, pencairan, pembayaran, pembatalan pinjaman) di **stored procedure PostgreSQL (PL/pgSQL)**. Logika di dalam database sulit di-unit test, sulit dirawat, dan tidak memanfaatkan model domain. Platform ingin **memindahkan logika itu ke kode aplikasi Java bergaya DDD**, dengan PostgreSQL tetap sebagai penyimpan data, lalu **mempensiunkan stored procedure**.

Migrasi manual itu lambat, dan yang lebih berbahaya, dapat **mengubah perilaku tanpa disadari**: NULL, transaksi, pembulatan uang. Alat ini mengotomatiskan penerjemahan secara **deterministik** dan **membuktikan kesetaraan perilaku** sebelum sebuah procedure boleh dipensiunkan.

Lensa ISO 25010:
- *Product quality*: migrasi meningkatkan **maintainability**, terutama *testability* dan *modifiability*. Syaratnya, **functional suitability** (*functional correctness*) tidak boleh turun.
- *Quality in use*: **effectiveness** dan **efficiency** sekaligus menjadi kriteria dosen (lihat §9).

### Prinsip yang tidak bisa ditawar [Tim]
1. **Alat murni deterministik.** Tidak ada LLM atau AI saat alat dijalankan. Input yang sama selalu menghasilkan output yang sama, dan setiap transformasi bisa ditelusuri ke aturan pemetaan.
2. **Source code tidak boleh keluar.** Semua berjalan lokal, tanpa panggilan ke layanan luar. Ini sesuai kenyataan di BFSI (rahasia bank, UU PDP, aturan OJK).
3. **AI hanya membangun alatnya.** Tim berperan sebagai penentu "benar": aturan pemetaan, korpus, pembuktian, dan pengukuran.
4. **Sumber PL/pgSQL, bukan Oracle.** Tidak ada Oracle RDBMS di mana pun.
5. **Kondisi akhir:** stored procedure tidak dipakai lagi. PostgreSQL hanya berisi tabel.

## 2. Goals, non-goals, anti-goals

**Goals**
- G1. Mem-parse korpus 5 stored procedure P2P lending beserta DDL skemanya.
- G2. Meng-generate kode Java 17 **bergaya repo tim semester lalu** (lihat §4): entity, interface repository, implementasi Map, implementasi PostgreSQL (JDBC), service per procedure, dan **endpoint REST Spring Boot per procedure** (§6.7).
- G3. Membuktikan kesetaraan perilaku procedure lama dan kode baru di PostgreSQL asli.
- G4. Menghasilkan **laporan kelayakan pensiun** per procedure.
- G5. Mengukur keefektifan, efisiensi, dan kebermanfaatan dengan angka (§9).

**Non-goals**
- Cakupan penuh PL/pgSQL. Hanya konstruksi di §6.3 yang didukung.
- Oracle dan dialek lain.
- Migrasi data. Data sudah di PostgreSQL dan tetap di sana.
- UI web untuk alat migrator. CLI dan laporan Markdown/HTML sudah cukup. (Kode **hasil generate** memang menyertakan REST API, lihat §6.7.)
- Keamanan SNAP BI yang sesungguhnya (token B2B, verifikasi tanda tangan RSA/HMAC), otorisasi, dan dokumentasi OpenAPI untuk REST API hasil generate.
- Kualitas siap produksi (performa, keamanan, multi-user).

**Anti-goals** (dianggap gagal walaupun "jalan")
- Kode hasil generate *tampak* benar tapi tidak terbukti setara.
- Alat diam-diam mengubah perilaku: transaksi tidak di-rollback, NULL diperlakukan berbeda, pembulatan berbeda.
- Alat berubah menjadi pembungkus LLM.
- Ada bagian kode (alat atau hasil generate) yang tidak bisa dijelaskan tim saat demo.
- Procedure yang memakai konstruksi tak didukung tetap "diterjemahkan" dengan tebakan. Alat harus **gagal secara eksplisit** dan menandainya *tidak layak pensiun*.

## 3. Kendala

- 4 minggu, 2 orang, level semester 3. Mereka sudah belajar DDD (semester lalu) dan sedang belajar database (semester ini).
- Laptop pengembangan: Intel i5 2015, RAM 8 GB, **disk kosong ±4 GB**, macOS 12. Tidak ada Docker. PostgreSQL dipasang native lewat Homebrew (±200 MB).
- Alat ditulis dalam **Python**, sedangkan kode hasil generate dan test harness dalam **Java**. Tim harus bisa menjelaskan keduanya.

## 4. Referensi gaya: repo semester lalu

Repo: `https://github.com/miabwi086/c6-core-engine-p2p-lending`

- Java 17, Maven, JUnit 5, Mockito, Cucumber 7 (Gherkin), Log4j2. **Tanpa Spring**: service dibuat dengan *constructor injection* biasa.
- Paket: `com.p2p.domain` (entity), `com.p2p.repository` (interface), `com.p2p.infrastructure` (`InMemory*Repository`, singleton berisi `Map<String, X>`), `com.p2p.service`, dan `com.p2p.pattern.{state,strategy,observer}`.
- Interface repository: `void save(X)`, `Optional<X> findById(String)`, `List<X> findAll()`.
- Service melempar `IllegalArgumentException` ("... tidak ditemukan") dan `IllegalStateException` untuk aturan bisnis.
- Status `Loan` memakai pola State: `PENDING → FUNDED → DISBURSED → REPAID`, serta `CANCELLED`.
- Skenario Gherkin tersedia di `src/test/resources/features/*.feature` (submission, funding, disbursement, repayment, cancellation).

Dua temuan yang menjadi materi tubes:
- **Bug transaksi:** `FundingService.fund()` mendebit dan menyimpan saldo lender *sebelum* validasi "Pendanaan melebihi sisa target". Ketika validasi gagal, saldo lender sudah terpotong. Map tidak punya rollback, sedangkan PostgreSQL otomatis me-rollback. Kode hasil generate **wajib** mempertahankan semantik rollback (§6.5).
- **Uang dengan `double`** (`FixedInterestStrategy`, `Borrower.income`). Kode hasil generate **wajib** memakai `BigDecimal` untuk `numeric`.

## 5. Pendekatan

| # | Pendekatan | Kelebihan | Kekurangan |
|---|---|---|---|
| 1 | Java + ANTLR4, grammar subset PL/pgSQL ditulis sendiri | Satu bahasa dengan output, pemakaian library parsing paling terlihat | Grammar harus ditulis dan dirawat, setup ANTLR di Maven |
| 2 | Python + Lark (melanjutkan spike) | Cepat dimulai | Grammar tetap ditulis sendiri, ada dua bahasa, tidak ada kelebihan dibanding 1 atau 3 |
| **3** | **Python + pglast (libpg_query, parser asli PostgreSQL)** | **Parsing 100% akurat terhadap PostgreSQL asli (versi parser 18)**, tidak perlu memelihara grammar | Alat dalam Python, output dalam Java. AST mentah berupa JSON yang rumit sehingga perlu IR sendiri |

**Dipilih: 3 [Tim].** Recon menunjukkan bahwa parsing tetap terjadi dua tingkat. `pglast.parse_plpgsql` mengurai struktur procedure, tetapi SQL dan ekspresi di dalamnya masih string yang harus di-parse ulang dengan `pglast.parse_sql`. Pekerjaan parsing alat ini adalah **menggabungkan dua parser menjadi satu IR milik sendiri**.

## 6. Desain alat

### 6.1 Alur

```
schema.sql ─┐
            ├─▶ [1] Parse (pglast) ─▶ [2] IR ─▶ [3] Analyzer ─▶ [4] Generator (template) ─▶ proyek Java
procs/*.sql ┘                                      │                                         │
                                                   └────────▶ laporan ◀── [5] Verifier ◀────┘
                                                                              ▲
                                                              tests/*.feature (kasus bisnis tim)
```

1. **Parse.** `parse_sql` untuk DDL (`CREATE TABLE`) dan untuk setiap SQL/ekspresi tertanam. `parse_plpgsql` untuk struktur procedure.
2. **IR (Intermediate Representation).** AST ternormalisasi milik alat, independen dari bentuk JSON pglast. Isinya: `Procedure(name, params, vars, body)`, `Table(name, columns[name, type, nullable, pk])`, dan pernyataan (`Assign`, `If(branches, else)`, `SelectInto(table, columns, targets, where)`, `Update(table, set, where)`, `Insert`, `Delete`, `Raise(message, params)`, `ForQuery(var, select, body)`, `Return`), serta ekspresi (`Binary`, `Unary`, `Literal`, `VarRef`, `ColumnRef`, `FuncCall`, `IsNull`, `Found`).
3. **Analyzer.** Menghitung tabel yang disentuh, jumlah cabang, konstruksi tak didukung, dan jebakan semantik yang terdeteksi (§7).
4. **Generator.** Template **Jinja2** [Usulan] menghasilkan proyek Maven Java.
5. **Verifier.** Menjalankan test kesetaraan (§8) dan mengisi laporan.

**Antarmuka CLI** [Usulan]: `migrator generate --schema schema.sql --procs procs/ --mapping mapping.yaml --out generated/`, lalu `migrator report`. Verifier dijalankan dengan `mvn test` di proyek hasil generate.

### 6.2 Input
- `schema.sql`: DDL tabel `borrower`, `lender`, `loan`, `funding` (dan `repayment` bila diperlukan) [Usulan]. Kolom memakai tipe `text`, `numeric(…)`, `integer`, `boolean`, `date`/`timestamp`, dan nullable sesuai kebutuhan.
- `procs/*.sql`: korpus 5 procedure [Usulan], ditulis tim dengan meniru perilaku service di repo semester lalu:

| Procedure | Meniru | Konstruksi utama |
|---|---|---|
| `ajukan_pinjaman` | `LoanSubmissionService` | SELECT INTO + `NOT FOUND`, IF, INSERT, RAISE, update limit |
| `danai_pinjaman` | `FundingService` | 2× SELECT INTO, validasi sisa target, UPDATE saldo dan collected, transisi ke `FUNDED` |
| `cairkan_pinjaman` | `DisbursementService` | validasi status, UPDATE saldo borrower, status `DISBURSED` |
| `bayar_pinjaman` | `RepaymentService` | perhitungan bunga `numeric` + `round`, validasi saldo, status `REPAID` |
| `batalkan_pinjaman` | `LoanCancellationService` (+ refund lender dari skenario Gherkin) | `FOR r IN SELECT … LOOP` refund tiap lender, kembalikan limit, status `CANCELLED` |

- `mapping.yaml` (opsional, petunjuk DDD) [Usulan]: memetakan tabel ke nama entity dan `UPDATE <tabel> SET status = '<X>'` ke pemanggilan transisi domain (misalnya `loan.fund()`). **Wajib di MVP:** pemetaan nama tabel/kolom ke kelas/field. **Opsional (minggu 3–4):** pemetaan transisi status ke pola State. Tanpa petunjuk transisi, generator menghasilkan `setStatus("FUNDED")` bergaya *transaction script*.

### 6.3 Cakupan konstruksi PL/pgSQL

| Konstruksi | Status |
|---|---|
| Parameter `IN` (procedure); `OUT`/`INOUT` | wajib; OUT/INOUT [Pembangun] kalau dibutuhkan korpus |
| `DECLARE` variabel skalar dengan atau tanpa default | wajib |
| `:=` dengan ekspresi aritmetika, perbandingan, `AND/OR/NOT`, `IS [NOT] NULL`, `COALESCE`, `round()` | wajib |
| `IF / ELSIF / ELSE` | wajib |
| `SELECT … INTO` satu baris dari **satu tabel**, WHERE berupa kesetaraan kolom = ekspresi (gabungan AND) | wajib |
| `FOUND` / `NOT FOUND` | wajib |
| `UPDATE` / `INSERT` / `DELETE` satu tabel, WHERE kesetaraan (gabungan AND) | wajib |
| `RAISE EXCEPTION 'pesan %', arg` | wajib |
| `FOR rec IN SELECT … LOOP … END LOOP` (satu tabel) | wajib (dibutuhkan `batalkan_pinjaman`) |
| `RETURN` tanpa nilai | wajib |
| JOIN, subquery, `UPDATE … FROM`, CTE, cursor eksplisit, dynamic SQL (`EXECUTE`), `EXCEPTION WHEN` handler, `COMMIT`/`ROLLBACK` di dalam procedure | **tidak didukung**: gagal eksplisit, *tidak layak pensiun* |

**Aturan korpus tambahan** [Usulan, hasil review pembangun]:
- **Tidak ada nilai yang dibuat database sendiri.** ID dan tanggal selalu masuk sebagai parameter procedure. `serial`/sequence, `gen_random_uuid()`, `now()`, `CURRENT_DATE`, dan fungsi non-deterministik lain **tidak didukung** (gagal eksplisit). Dengan begitu kedua database bisa dibandingkan baris per baris tanpa normalisasi.
- **`FOR … IN SELECT` wajib memakai `ORDER BY` kolom primary key.** Tanpa itu, urutan PostgreSQL tidak terdefinisi dan berbeda dengan urutan Map. Loop tanpa `ORDER BY` dianggap tidak didukung. Repository Map mengurutkan hasil dengan kunci yang sama.

Batasan "satu tabel dan WHERE berupa kesetaraan" dipilih supaya **implementasi repository Map bisa di-generate secara deterministik** tanpa harus menulis mesin query.

### 6.4 Output yang di-generate (proyek Maven Java 17, paket `com.p2p`) [Usulan, mengikuti §4]

**Proyek baru yang berdiri sendiri dan bergaya sama dengan repo semester lalu, bukan pengganti kelas di repo lama [Tim].** Nama method dan signature ditentukan generator. Kode hasil generate tidak harus lolos test lama, dan tidak mewarisi pola State atau field `double` dari repo lama. Beberapa skenario Gherkin lama boleh diadaptasi menjadi kasus kesetaraan (§8).

- `domain/<Entity>.java` per tabel: field bertipe sesuai §7.1, konstruktor, getter/setter. Field status bisa disambungkan ke pola State bila ada petunjuk di `mapping.yaml`.
- `repository/<Entity>Repository.java`: `save`, `findById`, `findAll`, ditambah method yang dibutuhkan procedure (misalnya `findByLoanId` untuk FOR-loop atas `funding`).
- `infrastructure/InMemory<Entity>Repository.java`: berbasis `Map`, sesuai gaya semester lalu.
- `infrastructure/Jdbc<Entity>Repository.java`: JDBC ke PostgreSQL.
- `service/<NamaProcedure>Service.java`: satu method publik per procedure. Logika diterjemahkan pernyataan per pernyataan. `RAISE EXCEPTION` diterjemahkan menjadi exception dengan **pesan identik** [Usulan: satu kelas `ProcedureException extends RuntimeException`, supaya pesan bisa dibandingkan 1:1].
- `support/`: helper semantik SQL (perbandingan null-safe, pembulatan) dan `UnitOfWork`.
- Setiap file hasil generate diberi header komentar berisi nama procedure sumber dan baris asal, untuk keterlacakan.

### 6.5 Transaksi [Usulan]
Satu pemanggilan procedure sama dengan satu transaksi. Kalau terjadi exception, **semua perubahan batal**.
- **JDBC:** `UnitOfWork` memakai satu `Connection` dengan `autoCommit=false`, lalu commit di akhir atau rollback saat exception.
- **Map:** `UnitOfWork` mengambil snapshot (salinan dalam) dari setiap repository yang terlibat sebelum eksekusi dan memulihkannya saat exception.
- Service menerima `UnitOfWork` lewat konstruktor. Detail API-nya [Pembangun].

### 6.6 Fitur skema dan kesetiaan varian Map [Usulan, hasil review pembangun]
Supaya repository Map tidak perlu menjadi "mesin database kecil", `schema.sql` dibatasi:
- **Boleh:** `PRIMARY KEY` (satu kolom), `NOT NULL`, tipe pada §7.1 termasuk `numeric(p,s)`.
- **Tidak boleh:** `FOREIGN KEY`, `CHECK`, `DEFAULT`, `UNIQUE` selain PK, trigger. Aturan bisnis semacam itu ditulis di procedure sebagai `IF … RAISE`.

Yang harus ditiru repository Map dan kode hasil generate:
- Pembulatan saat menyimpan ke kolom atau variabel `numeric(p,s)` (lihat J11), pelanggaran `NOT NULL`, PK ganda saat INSERT, dan pembagian dengan nol.
- **Error dari database dianggap hasil yang setara bila kelas SQLSTATE-nya sama**, bukan teks pesannya. `ProcedureException` membawa kode SQLSTATE: `P0001` untuk `RAISE EXCEPTION` (pesan juga harus identik), `23502` NOT NULL, `23505` PK ganda, `22012` pembagian nol, `22003` overflow numerik.

### 6.7 REST API dengan Spring Boot [Tim; rincian Usulan]
Kode hasil generate adalah **aplikasi Spring Boot** (Java 17, Spring Boot 3.x, Maven). Setiap procedure diekspos sebagai endpoint REST, sehingga aplikasi klien memanggil HTTP, bukan `CALL` ke database. Ini berbeda dari repo semester lalu yang tidak memakai Spring. Gaya paket `com.p2p.*` tetap dipertahankan.

- **Lapisan:** `domain` tetap Java biasa tanpa anotasi. Service di-generate dengan `@Service`, repository dengan `@Repository`, dan controller baru di paket `com.p2p.api` dengan `@RestController`.
- **Dua profil Spring:**
  - `memory`: bean repository Map ditambah `UnitOfWork` snapshot. Bisa jalan tanpa database dan dipakai untuk unit test cepat.
  - `postgres`: bean repository JDBC (`JdbcTemplate`) ditambah `UnitOfWork` berbasis `TransactionTemplate` Spring (satu transaksi database per pemanggilan).
  
  `UnitOfWork` menjadi interface dengan dua implementasi, dan generator tidak perlu tahu profil mana yang dipakai.
#### Konvensi endpoint: SNAP BI, tingkat "format tanpa kriptografi" [Tim]
SNAP BI (Standar Nasional Open API Pembayaran) hanya mendefinisikan layanan pembayaran, dan operasi P2P lending tidak termasuk di dalamnya. Karena itu endpoint **mengikuti konvensi SNAP** dengan **kode layanan ekstensi non-standar** yang ditandai jelas. Detail kode kasus dan format harus dicocokkan dengan spesifikasi resmi BI/ASPI saat implementasi. Rujukan sementara adalah dokumentasi vendor (Durianpay, Finpay, 2C2P).

- **URL:** `POST /v1.0/<nama-layanan>` (kebab-case, bahasa Inggris), misalnya `/v1.0/loan-funding`. Semua parameter dikirim di body, tidak ada path variable.
- **Header wajib** (dicek keberadaan dan formatnya; **isi `Authorization` dan `X-SIGNATURE` tidak diverifikasi secara kriptografis**): `Content-Type: application/json`, `Authorization: Bearer …`, `X-TIMESTAMP` (ISO 8601 dengan zona waktu, misalnya `2026-11-21T10:15:00+07:00`), `X-SIGNATURE`, `X-PARTNER-ID`, `X-EXTERNAL-ID`, `CHANNEL-ID`. Header hilang → `400xx02`; format salah → `400xx01`.
- **Idempotensi `X-EXTERNAL-ID`:** harus unik per `X-PARTNER-ID` per hari. Request dengan ID yang sama di hari yang sama ditolak `409xx00` tanpa menjalankan service. Disimpan di tabel `snap_external_id` (PostgreSQL) atau di Map (profil `memory`). Tabel ini milik lapisan API, **bukan** bagian perbandingan kesetaraan §8.
- **Body request:** field camelCase dari nama parameter (prefix `p_` dibuang). Parameter `numeric` uang dikirim sebagai objek SNAP `{"value": "4000000.00", "currency": "IDR"}` (string, 2 desimal, `currency` wajib `IDR`). Body juga memuat `partnerReferenceNo` (wajib, dikembalikan apa adanya di respons).
- **Body respons:** `{"responseCode": "…", "responseMessage": "…", "partnerReferenceNo": "…"}`, ditambah nilai parameter `OUT`/`INOUT` bila ada.
- **`responseCode` = HTTP (3 digit) + kode layanan (2 digit) + kode kasus (2 digit).** Sukses: `200xx00` "Successful".
- **Kode layanan ekstensi** [Usulan]: `90` loan-submission, `91` loan-funding, `92` loan-disbursement, `93` loan-repayment, `94` loan-cancellation. Rentang ini harus dicek dulu terhadap daftar resmi supaya tidak bentrok; geser bila perlu.
- **Pemetaan `mapping.yaml`** (ditentukan tim per procedure):
  ```yaml
  danai_pinjaman:
    service: loan-funding
    serviceCode: "91"
    money: [p_jumlah]                     # parameter numeric yang dikirim sebagai objek amount SNAP
    errors:                               # pesan RAISE EXCEPTION (pola awal) -> HTTP + kode kasus SNAP
      "Loan tidak ditemukan":                    { http: 404, case: "01", message: "Loan Not Found" }
      "Lender tidak ditemukan":                  { http: 404, case: "02", message: "Lender Not Found" }
      "Pendanaan melebihi sisa target pinjaman": { http: 403, case: "01", message: "Funding Exceeds Remaining Target" }
  ```
  Nilai `http`/`case` di contoh di atas adalah ilustrasi. Tim memilih kode yang sesuai dari tabel resmi SNAP, dan inilah salah satu **aturan pemetaan yang menjadi keputusan tim**.
- **Bawaan bila tidak dipetakan:** `RAISE EXCEPTION` tanpa entri → `403xx00`; `23502`/`22003` → `400xx01`; `23505` → `409xx01`; lainnya → `500xx00` "General Error". Pesan asli `RAISE` dicatat di log, tidak dikirim ke klien.
- **Verifikasi:** kesetaraan tetap dibuktikan di level service (§8). Di level API, generator membuat **test MockMvc** per endpoint: satu sukses (`200xx00`), satu aturan bisnis ditolak, satu header wajib hilang (`400xx02`), dan satu `X-EXTERNAL-ID` ganda (`409xx00`). Ini tidak menggantikan test kesetaraan.
- **Di luar cakupan:** endpoint `/v1.0/access-token/b2b`, verifikasi tanda tangan asimetris (RSA) dan simetris (HMAC-SHA512), serta validasi `X-TIMESTAMP` terhadap jam server.
- **Lingkungan:** Maven 3.9 dan Java 21 sudah ada di laptop pengembangan. Dependensi Spring Boot akan diunduh ke `~/.m2` (perkiraan 150–250 MB; disk kosong ±4 GB).

## 7. Aturan pemetaan dan katalog jebakan semantik

### 7.1 Pemetaan tipe [Usulan]
| PostgreSQL | Java | Catatan |
|---|---|---|
| `numeric`, `numeric(p,s)` | `BigDecimal` | tidak boleh `double` |
| `text`, `varchar` | `String` | |
| `integer` | `Integer` (nullable) / `int` (NOT NULL) | |
| `boolean` | `Boolean` / `boolean` | |
| `date` / `timestamp` | `LocalDate` / `LocalDateTime` | |

### 7.2 Katalog jebakan (awal; dikembangkan tim sebagai produk kerja utama)
| # | Jebakan | Perilaku PostgreSQL | Penanganan di kode hasil generate |
|---|---|---|---|
| J1 | NULL di kondisi IF | perbandingan dengan NULL menghasilkan NULL, diperlakukan seperti false (masuk ELSE) | helper perbandingan null-safe tiga nilai, bukan `compareTo` langsung (yang akan `NullPointerException`) |
| J2 | Aritmetika dengan NULL | `x + NULL` = NULL | helper aritmetika yang merambatkan NULL |
| J3 | `SELECT INTO` tanpa baris | variabel menjadi NULL dan `FOUND` false, **tanpa error** | `Optional` dari repository → variabel null dan flag `found` |
| J4 | Rollback saat `RAISE` | seluruh perubahan dalam pemanggilan batal | `UnitOfWork` (§6.5) |
| J5 | Pembulatan `round(numeric, s)` | *half away from zero* | `setScale(s, RoundingMode.HALF_UP)` |
| J6 | Pembagian `numeric / numeric` | presisi tinggi, bukan pembulatan ke skala input | `divide` dengan `MathContext` yang setara [Pembangun: tentukan dan buktikan dengan test] |
| J7 | Pembagian `integer / integer` | dibulatkan ke arah nol | sama dengan Java `int`, tetap dites |
| J8 | Konkatenasi `'a' \|\| NULL` | NULL | helper konkatenasi |
| J9 | Perbandingan `numeric` beda skala | `10.0 = 10.00` benar | `compareTo`, **bukan** `equals` |
| J10 | Format pesan `RAISE` dengan `%` | `%` diganti argumen; NULL menjadi teks `<NULL>` | formatter yang meniru aturan yang sama |
| J11 | Assign ke variabel atau kolom `numeric(p,s)` | nilai dibulatkan ke skala `s` (*half away from zero*); melebihi presisi `p` → error `22003` | `setScale(s, HALF_UP)` saat assign/simpan, cek presisi |
| J12 | Error bawaan database (NOT NULL, PK ganda, bagi nol) | transaksi batal dengan SQLSTATE tertentu | `ProcedureException` dengan SQLSTATE yang sama (§6.6) |

Setiap jebakan **harus punya minimal satu test kesetaraan** yang memicunya.

## 8. Verifikasi kesetaraan

- **PostgreSQL lokal** (Homebrew). Dua database: `p2p_legacy` (tabel dan stored procedure) serta `p2p_baru` (tabel saja, tanpa procedure).
- **Satu kasus uji:** (1) isi data awal identik ke kedua database, (2) `CALL <procedure>(…)` di `p2p_legacy`, (3) jalankan service hasil generate dengan repository JDBC ke `p2p_baru`, (4) bandingkan **isi lengkap semua tabel yang disentuh** dan **hasil** (sukses, atau exception dengan pesan yang sama). Perbandingan numerik memakai nilai, bukan teks.
- **Varian Map:** kasus yang sama juga dijalankan dengan repository Map (data awal dimuat ke Map, kondisi akhir dibandingkan dengan `p2p_legacy`). Ini membuktikan bahwa versi Map tanpa database juga setara.
- **Sumber kasus uji** [Usulan]: skenario **Gherkin** yang ditulis tim, diadaptasi dari `.feature` repo semester lalu, dalam bentuk step generik, misalnya:
  ```gherkin
  Given tabel "loan" berisi
    | id   | amount   | collected_amount | status  |
    | L001 | 10000000 | 8000000          | PENDING |
  When procedure "danai_pinjaman" dipanggil dengan "L001", "LD001", 5000000
  Then hasil lama dan baru setara
  ```
  Minimal: setiap cabang IF dari setiap procedure, ditambah setiap jebakan J1–J12 yang relevan. Hasil dianggap setara bila isi tabel identik (dibandingkan per primary key) dan hasilnya sama: sukses, atau gagal dengan SQLSTATE yang sama (ditambah pesan identik untuk `P0001`).
- **Uji kepekaan verifier (mutasi)** [Usulan]: tim sengaja menyuntikkan ≥10 perubahan perilaku kecil ke kode hasil generate (operator dibalik, rollback dihapus, `HALF_UP` diganti `HALF_EVEN`, dan sejenisnya). Verifier harus menangkapnya. Ini membuktikan bahwa test-nya benar-benar memeriksa sesuatu.
- **Laporan kelayakan pensiun** per procedure: konstruksi yang dipakai, jebakan yang terdeteksi dan ditangani, jumlah kasus lulus/gagal (JDBC dan Map), serta status **LAYAK** (semua lulus dan tidak ada konstruksi tak didukung) atau **TIDAK LAYAK** beserta alasannya.

## 9. Pengukuran sesuai kriteria dosen [Usulan]

| Kriteria | Metrik |
|---|---|
| **Keefektifan** | % procedure korpus yang diterjemahkan tanpa konstruksi tak didukung; % kasus kesetaraan lulus (JDBC dan Map); jumlah jebakan katalog yang tertangani; % mutasi tertangkap verifier |
| **Efisiensi** | Waktu migrasi per procedure: **manual** (tim menulis ulang 1–2 procedure tanpa alat dan mencatat waktu, termasuk menulis test) vs **dengan alat** (generate + review + verifikasi); waktu jalan alat |
| **Kebermanfaatan** | Bug transaksi repo lama (§4) tidak terulang di hasil generate; jumlah unit test yang bisa jalan **tanpa database** (repository Map) dibanding procedure yang hanya bisa dites di DB; keterlacakan setiap baris hasil generate ke sumber; seluruh proses lokal tanpa kode keluar |

## 10. Peran tim [Tim]
- **AI** menulis alat (parser, IR, generator, harness).
- **Orang A, "penjaga kebenaran":** katalog jebakan, aturan pemetaan, kasus uji kesetaraan, uji mutasi.
- **Orang B, "penjaga nilai":** korpus procedure dan skema yang realistis, pengukuran §9 (termasuk eksperimen migrasi manual), laporan, dan demo.
- Keduanya mereview setiap output AI dan harus bisa menjelaskan seluruh bagian.

## 11. Urutan kerja kasar [Usulan; jadwal rinci ditentukan saat perencanaan]
- **Minggu 1:** pasang PostgreSQL; tulis `schema.sql` dan 2 procedure (`danai`, `cairkan`); parse → IR; generate entity dan repository.
- **Minggu 2:** generator service untuk konstruksi wajib; UnitOfWork; verifier JDBC; kedua procedure lulus.
- **Minggu 3:** 3 procedure sisanya (termasuk FOR-loop); verifier Map; katalog jebakan; eksperimen migrasi manual; `mapping.yaml` transisi State (opsional).
- **Minggu 4:** uji mutasi, laporan kelayakan pensiun, metrik, demo, laporan tubes.

## 12. Diserahkan ke pembangun
- Bentuk persis kelas IR dan cara memetakan JSON pglast ke IR.
- Struktur folder proyek alat Python, struktur template, dan penamaan.
- Detail API `UnitOfWork` dan helper semantik SQL.
- Desain step Gherkin generik dan mekanisme pemuatan data awal.
- Format laporan (Markdown atau HTML).

## 13. Pelajaran dari spike pglast (`spike-pglast/`)
- Parse dua tingkat (struktur dengan `parse_plpgsql`, ekspresi/SQL dengan `parse_sql`) ke IR sendiri **berhasil** untuk `danai_pinjaman`. Kode Java hasil generate ter-compile dan lulus 11/11 cek dari skenario `loan_funding.feature`, termasuk **rollback saldo lender** (bug transaksi repo lama tidak terulang). Catatan: cek ini membandingkan dengan nilai harapan di skenario, belum dengan procedure yang benar-benar dijalankan di PostgreSQL.
- **Keterbacaan output adalah masalah nyata.** Kalau setiap ekspresi dibungkus helper null-safe (`Sql.isTrue(Sql.gt(...))`), kodenya setia tapi sulit dibaca, padahal *maintainability* adalah alasan migrasi. **Keputusan [Usulan]:** analyzer memakai info `NOT NULL` dari skema dan alur `FOUND` untuk menghasilkan Java biasa (`compareTo`, `!found`) bila nilainya pasti tidak NULL. Helper hanya dipakai bila NULL mungkin muncul. Keterbacaan diukur di §9 (misalnya baris kode dan kompleksitas dibanding versi tulisan tangan).
- **Pintasan spike yang tidak boleh terbawa:** skala `numeric` di-hardcode 2 (harus dibaca dari `numeric(p,s)`), WHERE hanya satu kesetaraan, string Java tidak di-escape, dan runtime `support/` ditulis tangan (di alat final disalin sebagai template tetap).

## Lampiran: artefak pendukung
- Spike sekali pakai (Oracle PL/SQL → Java dengan Lark): `spike/` di folder proyek. Bukan fondasi, hanya ilustrasi.
- Recon pglast: v8.9, parser PostgreSQL 18.6, `pglast.parse_plpgsql` jalan di laptop pengembangan.
