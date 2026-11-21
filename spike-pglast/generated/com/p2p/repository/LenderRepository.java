// DIHASILKAN OTOMATIS oleh migrator (spike) - jangan diedit manual.
package com.p2p.repository;

import java.util.List;
import java.util.Optional;

import com.p2p.domain.Lender;

public interface LenderRepository {
    void save(Lender entity);
    Optional<Lender> findById(String id);
    List<Lender> findAll();
}
