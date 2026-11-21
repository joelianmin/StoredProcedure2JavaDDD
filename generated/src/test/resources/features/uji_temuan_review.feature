Feature: Temuan review akhir — NULL lewat cabang, FOUND setelah FOR, AND/COALESCE pendek

  Scenario: Variabel yang tidak diisi karena IF dilewati tetap NULL (J1)
    When procedure "uji_null_cabang" dipanggil dengan:
      | p_flag |
      | false  |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Variabel diisi di dalam IF
    When procedure "uji_null_cabang" dipanggil dengan:
      | p_flag |
      | true   |
    Then procedure lama gagal dengan "P0001" "extra besar"
    And hasil lama dan baru setara

  Scenario: FOUND setelah FOR bernilai true walau UPDATE terakhir tidak mengenai baris
    Given tabel "funding" berisi:
      | id | loan_id | lender_id | amount     |
      | F1 | L001    | LD404     | 1000000.00 |
    When procedure "uji_found_for" dipanggil dengan:
      | p_loan_id |
      | L001      |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: FOR tanpa baris membuat FOUND false
    When procedure "uji_found_for" dipanggil dengan:
      | p_loan_id |
      | L999      |
    Then procedure lama gagal dengan "P0001" "tidak ada funding"
    And hasil lama dan baru setara

  Scenario: AND berhenti di sisi kiri yang false sehingga tidak ada pembagian nol (J6)
    When procedure "uji_and_pendek" dipanggil dengan:
      | p_jumlah | p_tenor |
      | 5000     | 0       |
    Then procedure lama berhasil
    And hasil lama dan baru setara

  Scenario: Tenor NULL membuat AND bernilai NULL (J1)
    When procedure "uji_and_pendek" dipanggil dengan:
      | p_jumlah | p_tenor |
      | <NULL>   | <NULL>  |
    Then procedure lama gagal dengan "P0001" "cicilan kosong"
    And hasil lama dan baru setara
