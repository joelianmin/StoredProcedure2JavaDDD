Feature: Kesetaraan danai_pinjaman (meniru FundingService semester lalu)

  Background:
    Given tabel "loan" berisi:
      | id   | borrower_id | amount      | collected_amount | tenor_months | interest_rate | status  |
      | L001 | B001        | 10000000.00 | 0.00             | 12           | 0.1000        | PENDING |
      | L002 | B001        | 10000000.00 | 8000000.00       | 12           | 0.1000        | PENDING |
      | L003 | B001        | 10000000.00 | 10000000.00      | 12           | 0.1000        | FUNDED  |
    And tabel "lender" berisi:
      | id    | name  | balance          |
      | LD001 | Ani   | 20000000.00      |
      | LD002 | Budi  | 1000000.00       |
      | LD003 | Citra | 9999999999999.99 |
    And tabel "funding" berisi:
      | id   | loan_id | lender_id | amount     |
      | F000 | L002    | LD001     | 8000000.00 |

  Scenario: Lender mendanai sebagian pinjaman
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F001         | L001      | LD001       | 4000000  |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Pendanaan mencapai target sehingga status FUNDED
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F002         | L002      | LD001       | 2000000  |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Melebihi sisa target ditolak dan debit saldo lender dibatalkan (bug transaksi repo lama, J4)
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F003         | L002      | LD001       | 5000000  |
    Then procedure lama gagal dengan "P0001" "Pendanaan melebihi sisa target pinjaman"
    And hasil lama dan baru setara

  Scenario: Saldo lender tidak mencukupi
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F004         | L001      | LD002       | 5000000  |
    Then procedure lama gagal dengan "P0001" "Saldo lender tidak mencukupi"
    And hasil lama dan baru setara

  Scenario: Loan tidak ditemukan: SELECT INTO tanpa baris lalu RAISE dengan argumen (J3, J10)
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F005         | L999      | LD001       | 1000000  |
    Then procedure lama gagal dengan "P0001" "Loan tidak ditemukan: L999"
    And hasil lama dan baru setara

  Scenario: Lender tidak ditemukan
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F006         | L001      | LD999       | 1000000  |
    Then procedure lama gagal dengan "P0001" "Lender tidak ditemukan: LD999"
    And hasil lama dan baru setara

  Scenario: Loan bukan PENDING ditolak
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F007         | L003      | LD001       | 1000000  |
    Then procedure lama gagal dengan "P0001" "Loan L003 tidak dalam status PENDING"
    And hasil lama dan baru setara

  Scenario: ID funding ganda melanggar primary key (J12)
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F000         | L001      | LD001       | 1000000  |
    Then procedure lama gagal dengan "23505" 'duplicate key value violates unique constraint "funding_pkey"'
    And hasil lama dan baru setara

  Scenario: Jumlah NULL lolos validasi lalu gagal NOT NULL saat debit (J1, J2)
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F008         | L001      | LD001       | <NULL>   |
    Then procedure lama gagal dengan "23502" 'null value in column "balance" of relation "lender" violates not-null constraint'
    And hasil lama dan baru setara

  Scenario: Jumlah negatif membuat saldo lender overflow numeric(15,2) (J11)
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F009         | L001      | LD003       | -1       |
    Then procedure lama gagal dengan "22003" "numeric field overflow"
    And hasil lama dan baru setara

  Scenario: Jumlah dengan 3 desimal dibulatkan saat disimpan (J11)
    When procedure "danai_pinjaman" dipanggil dengan:
      | p_funding_id | p_loan_id | p_lender_id | p_jumlah |
      | F010         | L001      | LD001       | 1000.005 |
    Then procedure lama berhasil
    And hasil lama dan baru setara
