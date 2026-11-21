package com.p2p.equivalence;

import java.util.List;
import java.util.Map;

/** Satu cara menjalankan procedure: legacy (CALL di PostgreSQL), jdbc, atau memory. */
public interface Backend {
    String name();

    /** Kosongkan semua tabel. */
    void reset();

    /** Isi baris awal; nilai null = NULL. */
    void insert(String table, List<Map<String, String>> rows);

    /** Jalankan procedure; error ditangkap menjadi Outcome. */
    Outcome call(String procedure, List<String> args);

    /** Isi seluruh tabel: nama tabel -> baris (kolom -> nilai), urut PK. */
    Map<String, List<Map<String, Object>>> dump();
}
