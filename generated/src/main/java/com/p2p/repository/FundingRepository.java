// DIHASILKAN OTOMATIS oleh migrator dari tabel `funding` - jangan diedit manual.
package com.p2p.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.p2p.domain.Funding;

public interface FundingRepository {
    /** Simpan (insert atau update) berdasarkan primary key. */
    void save(Funding entity);

    /** Insert baris baru; PK ganda -> ProcedureException 23505. */
    void insert(Funding entity);

    Optional<Funding> findById(String id);

    /** Baca untuk diubah (JDBC: SELECT ... FOR UPDATE, agar read-modify-write atomik seperti UPDATE). */
    Optional<Funding> findByIdForUpdate(String id);

    /** Semua baris, urut primary key. */
    List<Funding> findAll();

    int deleteById(String id);

    /** WHERE loan_id = ? ORDER BY id */
    List<Funding> findByLoanId(String loanId);

    /** DELETE ... WHERE loan_id = ? */
    int deleteByLoanId(String loanId);
}
