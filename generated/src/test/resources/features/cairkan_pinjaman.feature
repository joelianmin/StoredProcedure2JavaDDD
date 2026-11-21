Feature: Kesetaraan cairkan_pinjaman (meniru DisbursementService semester lalu)

  Background:
    Given tabel "borrower" berisi:
      | id   | name | kyc_verified | income     | balance | loan_limit  |
      | B001 | Budi | true         | 5000000.00 | 0.00    | 40000000.00 |
    And tabel "loan" berisi:
      | id   | borrower_id | amount      | collected_amount | tenor_months | interest_rate | status  |
      | L001 | B001        | 10000000.00 | 10000000.00      | 12           | 0.1000        | FUNDED  |
      | L002 | B001        | 10000000.00 | 0.00             | 12           | 0.1000        | PENDING |
      | L003 | B999        | 5000000.00  | 5000000.00       | 12           | 0.1000        | FUNDED  |

  Scenario: Pencairan berhasil menambah saldo borrower
    When procedure "cairkan_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L001      |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Hanya pinjaman FUNDED yang bisa dicairkan
    When procedure "cairkan_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L002      |
    Then procedure lama gagal dengan "P0001" "Hanya pinjaman berstatus FUNDED yang bisa dicairkan"
    And hasil lama dan baru setara

  Scenario: Loan tidak ditemukan
    When procedure "cairkan_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L999      |
    Then procedure lama gagal dengan "P0001" "Loan tidak ditemukan: L999"
    And hasil lama dan baru setara

  Scenario: Borrower tidak ada, UPDATE tidak mengenai baris tapi status tetap berubah
    When procedure "cairkan_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L003      |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Argumen NULL
    When procedure "cairkan_pinjaman" dipanggil dengan:
      | p_loan_id |
      | <NULL>    |
    Then procedure lama gagal dengan "P0001" "Loan tidak ditemukan: <NULL>"
    And hasil lama dan baru setara
