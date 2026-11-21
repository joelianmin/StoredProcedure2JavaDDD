# language: en
Feature: Kesetaraan danai_pinjaman (meniru FundingService semester lalu)

  Background:
    Given tabel "loan" berisi:
      | id   | borrower_id | amount      | collected_amount | tenor_months | interest_rate | status  |
      | L001 | B001        | 10000000.00 | 8000000.00       | 12           | 0.1000        | PENDING |
    And tabel "lender" berisi:
      | id    | name | balance     |
      | LD001 | Ani  | 6000000.00  |

  Scenario: Pendanaan melebihi sisa target ditolak dan saldo lender dikembalikan (bug transaksi repo lama, J4)
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F001         | L001      | LD001       | 5000000  |
    Then procedure lama gagal dengan "P0001" "Pendanaan melebihi sisa target pinjaman"
    And hasil lama dan baru setara
