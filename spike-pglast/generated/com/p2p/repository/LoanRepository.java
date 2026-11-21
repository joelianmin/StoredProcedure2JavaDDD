// DIHASILKAN OTOMATIS oleh migrator (spike) - jangan diedit manual.
package com.p2p.repository;

import java.util.List;
import java.util.Optional;

import com.p2p.domain.Loan;

public interface LoanRepository {
    void save(Loan entity);
    Optional<Loan> findById(String id);
    List<Loan> findAll();
}
