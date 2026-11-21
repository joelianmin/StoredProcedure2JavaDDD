Feature: Kesetaraan batalkan_pinjaman (meniru LoanCancellationService + refund ke tiap lender)

  Background:
    Given tabel "borrower" berisi:
      | id   | name | kyc_verified | income     | balance | loan_limit  |
      | B001 | Budi | true         | 5000000.00 | 0.00    | 40000000.00 |
    And tabel "lender" berisi:
      | id  | name | balance |
      | LD1 | Ani  | 0.00    |
      | LD2 | Budi | 0.00    |
    And tabel "loan" berisi:
      | id   | borrower_id | amount      | collected_amount | tenor_months | interest_rate | status    |
      | L001 | B001        | 10000000.00 | 6000000.00       | 12           | 0.1000        | PENDING   |
      | L002 | B001        | 5000000.00  | 5000000.00       | 12           | 0.1000        | DISBURSED |
      | L003 | B001        | 2000000.00  | 0.00             | 12           | 0.1000        | PENDING   |
    And tabel "funding" berisi:
      | id | loan_id | lender_id | amount     |
      | F1 | L001    | LD1       | 2000000.00 |
      | F2 | L001    | LD2       | 3000000.00 |
      | F3 | L001    | LD1       | 1000000.00 |
      | F9 | L002    | LD1       | 5000000.00 |

  Scenario: Pembatalan mengembalikan dana tiap lender lewat FOR ... ORDER BY id
    When procedure "batalkan_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L001      |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Pinjaman yang sudah dicairkan tidak bisa dibatalkan
    When procedure "batalkan_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L002      |
    Then procedure lama gagal dengan "P0001" "Pinjaman tidak dapat dibatalkan setelah dicairkan"
    And hasil lama dan baru setara

  Scenario: Pembatalan tanpa funding
    When procedure "batalkan_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L003      |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Loan tidak ditemukan
    When procedure "batalkan_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L999      |
    Then procedure lama gagal dengan "P0001" "Loan tidak ditemukan: L999"
    And hasil lama dan baru setara
