// DIHASILKAN OTOMATIS oleh migrator dari tabel `loan` - jangan diedit manual.
package com.p2p.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.p2p.domain.Loan;
import com.p2p.repository.LoanRepository;
import com.p2p.support.ProcedureException;
import com.p2p.support.Snapshotable;
import com.p2p.support.Sql;

/** Repository berbasis Map (gaya semester lalu) yang meniru aturan kolom PostgreSQL dan bisa di-rollback. */
@Repository
@Profile("memory")
public class InMemoryLoanRepository implements LoanRepository, Snapshotable {
    private TreeMap<String, Loan> data = new TreeMap<>();
    private TreeMap<String, Loan> snapshot;

    /** Meniru kolom: NOT NULL (23502) dan numeric(p,s) (pembulatan / 22003). */
    private Loan checked(Loan e) {
        Loan c = e.copy();
        if (c.getId() == null) {
            throw new ProcedureException("23502", "null value in column \"id\" of relation \"loan\" violates not-null constraint");
        }
        if (c.getBorrowerId() == null) {
            throw new ProcedureException("23502", "null value in column \"borrower_id\" of relation \"loan\" violates not-null constraint");
        }
        if (c.getAmount() == null) {
            throw new ProcedureException("23502", "null value in column \"amount\" of relation \"loan\" violates not-null constraint");
        }
        if (c.getCollectedAmount() == null) {
            throw new ProcedureException("23502", "null value in column \"collected_amount\" of relation \"loan\" violates not-null constraint");
        }
        if (c.getTenorMonths() == null) {
            throw new ProcedureException("23502", "null value in column \"tenor_months\" of relation \"loan\" violates not-null constraint");
        }
        if (c.getInterestRate() == null) {
            throw new ProcedureException("23502", "null value in column \"interest_rate\" of relation \"loan\" violates not-null constraint");
        }
        if (c.getStatus() == null) {
            throw new ProcedureException("23502", "null value in column \"status\" of relation \"loan\" violates not-null constraint");
        }
        c.setAmount(Sql.fitNumeric(c.getAmount(), 15, 2));
        c.setCollectedAmount(Sql.fitNumeric(c.getCollectedAmount(), 15, 2));
        c.setInterestRate(Sql.fitNumeric(c.getInterestRate(), 5, 4));
        return c;
    }

    @Override
    public void save(Loan e) {
        Loan c = checked(e);
        data.put(c.getId(), c);
    }

    @Override
    public void insert(Loan e) {
        Loan c = checked(e);
        if (data.containsKey(c.getId())) {
            throw new ProcedureException("23505", "duplicate key value violates unique constraint \"loan_pkey\"");
        }
        data.put(c.getId(), c);
    }

    @Override
    public Optional<Loan> findById(String id) {
        if (id == null) return Optional.empty();       // WHERE pk = NULL tidak cocok apa pun
        return Optional.ofNullable(data.get(id)).map(Loan::copy);
    }

    @Override
    public Optional<Loan> findByIdForUpdate(String id) {
        return findById(id);
    }

    @Override
    public List<Loan> findAll() {
        List<Loan> out = new ArrayList<>();
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
