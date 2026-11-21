// DIHASILKAN OTOMATIS oleh migrator dari tabel `loan` - jangan diedit manual.
package com.p2p.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.p2p.domain.Loan;

public interface LoanRepository {
    /** Simpan (insert atau update) berdasarkan primary key. */
    void save(Loan entity);

    /** Insert baris baru; PK ganda -> ProcedureException 23505. */
    void insert(Loan entity);

    Optional<Loan> findById(String id);

    /** Baca untuk diubah (JDBC: SELECT ... FOR UPDATE, agar read-modify-write atomik seperti UPDATE). */
    Optional<Loan> findByIdForUpdate(String id);

    /** Semua baris, urut primary key. */
    List<Loan> findAll();

    int deleteById(String id);
}
