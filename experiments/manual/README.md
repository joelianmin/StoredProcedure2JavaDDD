# Eksperimen migrasi manual (pembanding efisiensi)

Dikerjakan **tim (Orang B)**, bukan AI-agent alat ini.

## Protokol
1. Pilih `bayar_pinjaman` (dan bila sempat `batalkan_pinjaman`).
2. **Manual:** tulis ulang procedure menjadi service Java + repository + test di folder
   `experiments/manual/<procedure>_manual/` tanpa memakai migrator (AI boleh dipakai seperti biasa).
   Catat menit per tahap: `pahami`, `tulis_kode`, `tulis_test`, `debug`.
3. **Dengan alat:** jalankan `scripts/run_all.sh`, review service hasil generate, baca laporan.
   Catat menit per tahap: `generate`, `review`, `verifikasi`.
4. Isi `waktu.csv` (satu baris per tahap), lalu jalankan ulang `scripts/run_all.sh`
   supaya tabel efisiensi di `laporan/laporan-kelayakan.md` terisi.

## Format `waktu.csv`
```
procedure,cara,tahap,menit
bayar_pinjaman,manual,pahami,15
bayar_pinjaman,alat,review,5
```
`cara` ∈ {`manual`, `alat`}.

Catat juga (kualitatif): apakah versi manual menangani pembulatan di .005, saldo yang tidak cukup,
dan rollback dengan benar? Jalankan skenario `corpus/features/bayar_pinjaman.feature` terhadapnya bila bisa.
