// DIHASILKAN OTOMATIS oleh migrator dari tabel `lender` - jangan diedit manual.
package com.p2p.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.p2p.domain.Lender;

public interface LenderRepository {
    /** Simpan (insert atau update) berdasarkan primary key. */
    void save(Lender entity);

    /** Insert baris baru; PK ganda -> ProcedureException 23505. */
    void insert(Lender entity);

    Optional<Lender> findById(String id);

    /** Baca untuk diubah (JDBC: SELECT ... FOR UPDATE, agar read-modify-write atomik seperti UPDATE). */
    Optional<Lender> findByIdForUpdate(String id);

    /** Semua baris, urut primary key. */
    List<Lender> findAll();

    int deleteById(String id);
}
