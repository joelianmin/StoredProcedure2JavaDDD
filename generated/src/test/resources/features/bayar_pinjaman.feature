Feature: Kesetaraan bayar_pinjaman (meniru RepaymentService + FixedInterestStrategy, tanpa double)

  Background:
    Given tabel "borrower" berisi:
      | id   | name | kyc_verified | income     | balance  | loan_limit  |
      | B001 | Budi | true         | 5000000.00 | 5000.00  | 40000000.00 |
      | B002 | Dewi | true         | 5000000.00 | 10.00    | 40000000.00 |
    And tabel "loan" berisi:
      | id   | borrower_id | amount  | collected_amount | tenor_months | interest_rate | status    |
      | L001 | B001        | 1000.05 | 1000.05          | 7            | 0.1000        | DISBURSED |
      | L002 | B001        | 120.60  | 120.60           | 1            | 0.1000        | DISBURSED |
      | L003 | B001        | 1000.00 | 1000.00          | 1            | 0.1000        | DISBURSED |
      | L004 | B002        | 1000.00 | 1000.00          | 12           | 0.1000        | DISBURSED |
      | L005 | B001        | 1000.00 | 0.00             | 12           | 0.1000        | PENDING   |

  Scenario: Pembayaran dengan bunga round(1000.05 x 0.1 x 7 / 12, 2) (J5, J6)
    When procedure "bayar_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L001      |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Bunga tepat di x.xx5 dibulatkan menjauhi nol (1.005 -> 1.01, J5)
    When procedure "bayar_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L002      |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Pembagian tidak habis 100 / 12 (J6)
    When procedure "bayar_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L003      |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Saldo borrower tidak mencukupi
    When procedure "bayar_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L004      |
    Then procedure lama gagal dengan "P0001" "Saldo borrower tidak mencukupi"
    And hasil lama dan baru setara

  Scenario: Loan belum dicairkan
    When procedure "bayar_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L005      |
    Then procedure lama gagal dengan "P0001" "Loan belum dicairkan"
    And hasil lama dan baru setara

  Scenario: Loan tidak ditemukan
    When procedure "bayar_pinjaman" dipanggil dengan:
      | p_loan_id |
      | L999      |
    Then procedure lama gagal dengan "P0001" "Loan tidak ditemukan: L999"
    And hasil lama dan baru setara
