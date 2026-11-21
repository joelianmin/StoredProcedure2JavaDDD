Feature: Kesetaraan ajukan_pinjaman (meniru LoanSubmissionService semester lalu)

  Background:
    Given tabel "borrower" berisi:
      | id   | name  | kyc_verified | income     | balance | loan_limit  |
      | B001 | Budi  | true         | 5000000.00 | 0.00    | 50000000.00 |
      | B002 | Dewi  | true         | <NULL>     | 0.00    | 50000000.00 |
      | B003 | Eko   | true         | 3000000.00 | 0.00    | 50000000.00 |
      | B004 | Fajar | false        | 9000000.00 | 0.00    | 50000000.00 |
    And tabel "loan" berisi:
      | id   | borrower_id | amount     | collected_amount | tenor_months | interest_rate | status  |
      | L900 | B001        | 1000000.00 | 0.00             | 6            | 0.1000        | PENDING |

  Scenario: Pengajuan berhasil dan limit borrower berkurang
    When procedure "ajukan_pinjaman" dipanggil dengan:
      | p_loan_id | p_borrower_id | p_jumlah | p_tenor |
      | L001      | B001          | 10000000 | 12      |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Income NULL lolos pemeriksaan penghasilan secara diam-diam (J1, perilaku lama dipertahankan)
    When procedure "ajukan_pinjaman" dipanggil dengan:
      | p_loan_id | p_borrower_id | p_jumlah | p_tenor |
      | L002      | B002          | 10000000 | 12      |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Income tepat 3 juta ditolak
    When procedure "ajukan_pinjaman" dipanggil dengan:
      | p_loan_id | p_borrower_id | p_jumlah | p_tenor |
      | L003      | B003          | 10000000 | 12      |
    Then procedure lama gagal dengan "P0001" "Penghasilan borrower tidak memenuhi syarat"
    And hasil lama dan baru setara

  Scenario: Borrower belum KYC
    When procedure "ajukan_pinjaman" dipanggil dengan:
      | p_loan_id | p_borrower_id | p_jumlah | p_tenor |
      | L004      | B004          | 10000000 | 12      |
    Then procedure lama gagal dengan "P0001" "Borrower belum verifikasi KYC"
    And hasil lama dan baru setara

  Scenario: Limit tidak mencukupi
    When procedure "ajukan_pinjaman" dipanggil dengan:
      | p_loan_id | p_borrower_id | p_jumlah | p_tenor |
      | L005      | B001          | 60000000 | 12      |
    Then procedure lama gagal dengan "P0001" "Limit pinjaman borrower tidak mencukupi"
    And hasil lama dan baru setara

  Scenario: Borrower tidak ditemukan
    When procedure "ajukan_pinjaman" dipanggil dengan:
      | p_loan_id | p_borrower_id | p_jumlah | p_tenor |
      | L006      | B999          | 10000000 | 12      |
    Then procedure lama gagal dengan "P0001" "Borrower tidak ditemukan: B999"
    And hasil lama dan baru setara

  Scenario: ID loan ganda (J12)
    When procedure "ajukan_pinjaman" dipanggil dengan:
      | p_loan_id | p_borrower_id | p_jumlah | p_tenor |
      | L900      | B001          | 10000000 | 12      |
    Then procedure lama gagal dengan "23505" 'duplicate key value violates unique constraint "loan_pkey"'
    And hasil lama dan baru setara

  Scenario: Tenor NULL melanggar NOT NULL saat insert (J12)
    When procedure "ajukan_pinjaman" dipanggil dengan:
      | p_loan_id | p_borrower_id | p_jumlah | p_tenor |
      | L007      | B001          | 10000000 | <NULL>  |
    Then procedure lama gagal dengan "23502" 'null value in column "tenor_months" of relation "loan" violates not-null constraint'
    And hasil lama dan baru setara
