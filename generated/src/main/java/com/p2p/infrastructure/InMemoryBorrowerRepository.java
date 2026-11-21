// DIHASILKAN OTOMATIS oleh migrator dari tabel `borrower` - jangan diedit manual.
package com.p2p.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.p2p.domain.Borrower;
import com.p2p.repository.BorrowerRepository;
import com.p2p.support.ProcedureException;
import com.p2p.support.Snapshotable;
import com.p2p.support.Sql;

/** Repository berbasis Map (gaya semester lalu) yang meniru aturan kolom PostgreSQL dan bisa di-rollback. */
@Repository
@Profile("memory")
public class InMemoryBorrowerRepository implements BorrowerRepository, Snapshotable {
    private TreeMap<String, Borrower> data = new TreeMap<>();
    private TreeMap<String, Borrower> snapshot;

    /** Meniru kolom: NOT NULL (23502) dan numeric(p,s) (pembulatan / 22003). */
    private Borrower checked(Borrower e) {
        Borrower c = e.copy();
        if (c.getId() == null) {
            throw new ProcedureException("23502", "null value in column \"id\" of relation \"borrower\" violates not-null constraint");
        }
        if (c.getName() == null) {
            throw new ProcedureException("23502", "null value in column \"name\" of relation \"borrower\" violates not-null constraint");
        }
        if (c.getKycVerified() == null) {
            throw new ProcedureException("23502", "null value in column \"kyc_verified\" of relation \"borrower\" violates not-null constraint");
        }
        if (c.getBalance() == null) {
            throw new ProcedureException("23502", "null value in column \"balance\" of relation \"borrower\" violates not-null constraint");
        }
        if (c.getLoanLimit() == null) {
            throw new ProcedureException("23502", "null value in column \"loan_limit\" of relation \"borrower\" violates not-null constraint");
        }
        c.setIncome(Sql.fitNumeric(c.getIncome(), 15, 2));
        c.setBalance(Sql.fitNumeric(c.getBalance(), 15, 2));
        c.setLoanLimit(Sql.fitNumeric(c.getLoanLimit(), 15, 2));
        return c;
    }

    @Override
    public void save(Borrower e) {
        Borrower c = checked(e);
        data.put(c.getId(), c);
    }

    @Override
    public void insert(Borrower e) {
        Borrower c = checked(e);
        if (data.containsKey(c.getId())) {
            throw new ProcedureException("23505", "duplicate key value violates unique constraint \"borrower_pkey\"");
        }
        data.put(c.getId(), c);
    }

    @Override
    public Optional<Borrower> findById(String id) {
        if (id == null) return Optional.empty();       // WHERE pk = NULL tidak cocok apa pun
        return Optional.ofNullable(data.get(id)).map(Borrower::copy);
    }

    @Override
    public Optional<Borrower> findByIdForUpdate(String id) {
        return findById(id);
    }

    @Override
    public List<Borrower> findAll() {
        List<Borrower> out = new ArrayList<>();
        data.values().forEach(e -> out.add(e.copy()));
        return out;
    }

    @Override
    public int deleteById(String id) {
        if (id == null) return 0;
        return data.remove(id) != null ? 1 : 0;
    }

    /** Kosongkan isi (dipakai test). */
    public void clear() {
        data.clear();
    }

    @Override
    public void takeSnapshot() {
        snapshot = new TreeMap<>();
        data.forEach((k, v) -> snapshot.put(k, v.copy()));
    }

    @Override
    public void restoreSnapshot() {
        if (snapshot != null) data = snapshot;
        snapshot = null;
    }

    @Override
    public void discardSnapshot() {
        snapshot = null;
    }
}
