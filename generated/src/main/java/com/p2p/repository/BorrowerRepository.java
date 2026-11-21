// DIHASILKAN OTOMATIS oleh migrator dari tabel `borrower` - jangan diedit manual.
package com.p2p.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.p2p.domain.Borrower;

public interface BorrowerRepository {
    /** Simpan (insert atau update) berdasarkan primary key. */
    void save(Borrower entity);

    /** Insert baris baru; PK ganda -> ProcedureException 23505. */
    void insert(Borrower entity);

    Optional<Borrower> findById(String id);

    /** Baca untuk diubah (JDBC: SELECT ... FOR UPDATE, agar read-modify-write atomik seperti UPDATE). */
    Optional<Borrower> findByIdForUpdate(String id);

    /** Semua baris, urut primary key. */
    List<Borrower> findAll();

    int deleteById(String id);
}
