# Laporan migrasi: hitung_denda_kredit

- Parameter: 3 (p_no_rekening IN, p_tgl_proses IN, p_denda OUT)
- Variabel lokal: 5
- Tabel disentuh: pinjaman
- Jumlah cabang IF/ELSIF/ELSE: 4 (kandidat jumlah minimum test case)

## Catatan transformasi
- SELECT INTO dari `pinjaman` -> method repository `findPinjaman`; NO_DATA_FOUND -> EmptyResultDataAccessException.
- UPDATE `pinjaman` -> method repository `updatePinjaman` (SQL tetap, hanya dipindah).
- COMMIT eksplisit dihapus -> diganti @Transactional pada method (batas transaksi pindah ke service).
