# Laporan Kelayakan Pensiun Stored Procedure

Dihasilkan oleh `migrator report` dari `migrator-analysis.json` (analisis statis) dan
`target/equivalence-results.jsonl` (uji kesetaraan: procedure asli di PostgreSQL vs kode Java baru).
Acuan kebenaran = procedure lama yang dijalankan dengan `CALL` di PostgreSQL.

## Ringkasan

| Procedure | Putusan |
|---|---|
| ajukan_pinjaman | LAYAK |
| batalkan_pinjaman | LAYAK |
| bayar_pinjaman | LAYAK |
| cairkan_pinjaman | LAYAK |
| danai_pinjaman | LAYAK |
| uji_and_pendek | LAYAK |
| uji_found_for | LAYAK |
| uji_null_cabang | LAYAK |

## Metrik (kriteria dosen)

### Keefektifan

- Procedure diterjemahkan tanpa konstruksi tak didukung: 8/8 (100%)
- Skenario setara varian jdbc: 40/40 (100%)
- Skenario setara varian memory: 40/40 (100%)
- Jebakan terdeteksi analyzer: J1, J2, J3, J4, J5, J6, J10, J11, J12
- Jebakan yang punya skenario setara (disebut di nama skenario): J1, J2, J3, J4, J5, J6, J10, J11, J12 (9/12)
- Uji mutasi: ditunda (mode spike)

### Efisiensi

- Waktu jalan `migrator generate` (5 procedure): 0.438 detik
- Eksperimen migrasi manual: belum diisi (`experiments/manual/waktu.csv`)

### Kebermanfaatan

- Bug transaksi repo semester lalu (debit lender sebelum validasi) tidak terulang: ya, skenario J4 setara di jdbc, memory
- Skenario yang lulus tanpa database (repository Map): 40
- Pernyataan hasil terjemahan yang bisa dilacak ke baris SQL asal: 75
- Seluruh proses lokal dan deterministik (tanpa LLM, tanpa layanan luar)

## ajukan_pinjaman — LAYAK

- Sumber: `ajukan_pinjaman.sql` (27 baris SQL) → `AjukanPinjamanService` (44 baris Java logika)
- Tabel dibaca: borrower · ditulis: borrower, loan
- Konstruksi: If, Insert, Raise, SelectInto, Update · cabang IF/ELSIF/ELSE: 4
- Endpoint REST SNAP: ya
- Jebakan semantik terdeteksi: J1 (NULL di kondisi IF), J2 (aritmetika dengan NULL), J3 (SELECT INTO tanpa baris), J10 (format pesan RAISE), J11 (simpan ke numeric(p,s)), J12 (error bawaan database)
  - J1 baris 21: (v_income <= 3000000): v_income bisa NULL
  - J1 baris 25: (p_jumlah > v_limit): p_jumlah bisa NULL
  - J2 baris 32: (loan_limit - p_jumlah): p_jumlah bisa NULL
- Kesetaraan varian **jdbc**: 8/8 skenario setara
- Kesetaraan varian **memory**: 8/8 skenario setara

## batalkan_pinjaman — LAYAK

- Sumber: `batalkan_pinjaman.sql` (25 baris SQL) → `BatalkanPinjamanService` (61 baris Java logika)
- Tabel dibaca: funding, loan · ditulis: borrower, funding, lender, loan
- Konstruksi: Delete, ForQuery, If, Raise, SelectInto, Update · cabang IF/ELSIF/ELSE: 2
- Endpoint REST SNAP: ya
- Jebakan semantik terdeteksi: J3 (SELECT INTO tanpa baris), J10 (format pesan RAISE), J11 (simpan ke numeric(p,s)), J12 (error bawaan database)
- Kesetaraan varian **jdbc**: 4/4 skenario setara
- Kesetaraan varian **memory**: 4/4 skenario setara

## bayar_pinjaman — LAYAK

- Sumber: `bayar_pinjaman.sql` (34 baris SQL) → `BayarPinjamanService` (61 baris Java logika)
- Tabel dibaca: borrower, loan · ditulis: borrower, loan
- Konstruksi: Assign, If, Raise, SelectInto, Update · cabang IF/ELSIF/ELSE: 4
- Endpoint REST SNAP: ya
- Jebakan semantik terdeteksi: J3 (SELECT INTO tanpa baris), J5 (pembulatan round()), J6 (pembagian numeric), J10 (format pesan RAISE), J11 (simpan ke numeric(p,s)), J12 (error bawaan database)
- Kesetaraan varian **jdbc**: 6/6 skenario setara
- Kesetaraan varian **memory**: 6/6 skenario setara

## cairkan_pinjaman — LAYAK

- Sumber: `cairkan_pinjaman.sql` (20 baris SQL) → `CairkanPinjamanService` (43 baris Java logika)
- Tabel dibaca: loan · ditulis: borrower, loan
- Konstruksi: If, Raise, SelectInto, Update · cabang IF/ELSIF/ELSE: 2
- Endpoint REST SNAP: ya
- Jebakan semantik terdeteksi: J3 (SELECT INTO tanpa baris), J10 (format pesan RAISE), J11 (simpan ke numeric(p,s)), J12 (error bawaan database)
- Kesetaraan varian **jdbc**: 5/5 skenario setara
- Kesetaraan varian **memory**: 5/5 skenario setara

## danai_pinjaman — LAYAK

- Sumber: `danai_pinjaman.sql` (36 baris SQL) → `DanaiPinjamanService` (69 baris Java logika)
- Tabel dibaca: lender, loan · ditulis: funding, lender, loan
- Konstruksi: If, Insert, Raise, SelectInto, Update · cabang IF/ELSIF/ELSE: 6
- Endpoint REST SNAP: ya
- Jebakan semantik terdeteksi: J1 (NULL di kondisi IF), J2 (aritmetika dengan NULL), J3 (SELECT INTO tanpa baris), J4 (rollback saat RAISE), J10 (format pesan RAISE), J11 (simpan ke numeric(p,s)), J12 (error bawaan database)
  - J1 baris 27: (v_saldo < p_jumlah): p_jumlah bisa NULL
  - J2 baris 31: (balance - p_jumlah): p_jumlah bisa NULL
  - J1 baris 33: (p_jumlah > (v_target - v_terkumpul)): p_jumlah bisa NULL
  - J4 baris 34: RAISE setelah penulisan: semua perubahan harus di-rollback
  - J2 baris 40: (collected_amount + p_jumlah): p_jumlah bisa NULL
  - J1 baris 42: ((v_terkumpul + p_jumlah) >= v_target): (v_terkumpul + p_jumlah) bisa NULL
  - J2 baris 42: (v_terkumpul + p_jumlah): p_jumlah bisa NULL
- Kesetaraan varian **jdbc**: 11/11 skenario setara
- Kesetaraan varian **memory**: 11/11 skenario setara

## uji_and_pendek — LAYAK

- Sumber: `uji_and_pendek.sql` (14 baris SQL) → `UjiAndPendekService` (18 baris Java logika)
- Tabel dibaca: - · ditulis: -
- Konstruksi: Assign, If, Raise · cabang IF/ELSIF/ELSE: 2
- Endpoint REST SNAP: tidak (tidak ada di mapping.yaml)
- Jebakan semantik terdeteksi: J1 (NULL di kondisi IF), J2 (aritmetika dengan NULL), J6 (pembagian numeric)
  - J1 baris 7: ((p_jumlah / p_tenor) > 1000): (p_jumlah / p_tenor) bisa NULL
  - J1 baris 7: (p_tenor > 0): p_tenor bisa NULL
  - J2 baris 7: (p_jumlah / p_tenor): p_jumlah, p_tenor bisa NULL
  - J2 baris 10: (p_jumlah / p_tenor): p_jumlah, p_tenor bisa NULL
- Kesetaraan varian **jdbc**: 2/2 skenario setara
- Kesetaraan varian **memory**: 2/2 skenario setara

## uji_found_for — LAYAK

- Sumber: `uji_found_for.sql` (13 baris SQL) → `UjiFoundForService` (30 baris Java logika)
- Tabel dibaca: funding · ditulis: lender
- Konstruksi: ForQuery, If, Raise, Update · cabang IF/ELSIF/ELSE: 1
- Endpoint REST SNAP: tidak (tidak ada di mapping.yaml)
- Jebakan semantik terdeteksi: J4 (rollback saat RAISE), J11 (simpan ke numeric(p,s)), J12 (error bawaan database)
  - J4 baris 11: RAISE setelah penulisan: semua perubahan harus di-rollback
- Kesetaraan varian **jdbc**: 2/2 skenario setara
- Kesetaraan varian **memory**: 2/2 skenario setara

## uji_null_cabang — LAYAK

- Sumber: `uji_null_cabang.sql` (18 baris SQL) → `UjiNullCabangService` (22 baris Java logika)
- Tabel dibaca: - · ditulis: -
- Konstruksi: Assign, If, Raise · cabang IF/ELSIF/ELSE: 3
- Endpoint REST SNAP: tidak (tidak ada di mapping.yaml)
- Jebakan semantik terdeteksi: J1 (NULL di kondisi IF)
  - J1 baris 12: (v_extra > 50): v_extra bisa NULL
  - J1 baris 15: ('APPROVED' <> v_s): v_s bisa NULL
- Kesetaraan varian **jdbc**: 2/2 skenario setara
- Kesetaraan varian **memory**: 2/2 skenario setara
