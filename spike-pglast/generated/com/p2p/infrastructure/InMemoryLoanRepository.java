// DIHASILKAN OTOMATIS oleh migrator (spike) - jangan diedit manual.
package com.p2p.infrastructure;

import java.util.*;

import com.p2p.domain.Loan;
import com.p2p.repository.LoanRepository;
import com.p2p.support.*;

/** Repository berbasis Map (gaya semester lalu) + snapshot untuk rollback. */
public class InMemoryLoanRepository implements LoanRepository, Snapshotable {
    private Map<String, Loan> data = new HashMap<>();
    private Map<String, Loan> snapshot;

    @Override public void save(Loan e) {
        if (e.getId() == null) throw new ProcedureException("23502", "null value in column \"id\" of relation \"loan\" violates not-null constraint");
        if (e.getBorrowerId() == null) throw new ProcedureException("23502", "null value in column \"borrower_id\" of relation \"loan\" violates not-null constraint");
        if (e.getAmount() == null) throw new ProcedureException("23502", "null value in column \"amount\" of relation \"loan\" violates not-null constraint");
        if (e.getCollectedAmount() == null) throw new ProcedureException("23502", "null value in column \"collected_amount\" of relation \"loan\" violates not-null constraint");
        if (e.getStatus() == null) throw new ProcedureException("23502", "null value in column \"status\" of relation \"loan\" violates not-null constraint");
        if (e.getAmount() != null) e.setAmount(Sql.scale(e.getAmount(), 2));
        if (e.getCollectedAmount() != null) e.setCollectedAmount(Sql.scale(e.getCollectedAmount(), 2));
        data.put(e.getId(), e.copy());
    }
    @Override public Optional<Loan> findById(String id) {
        return Optional.ofNullable(data.get(id)).map(Loan::copy);
    }
    @Override public List<Loan> findAll() {
        List<Loan> out = new ArrayList<>();
        data.values().forEach(e -> out.add(e.copy()));
        return out;
    }
    @Override public void takeSnapshot() {
        snapshot = new HashMap<>();
        data.forEach((k, v) -> snapshot.put(k, v.copy()));
    }
    @Override public void restoreSnapshot() { data = snapshot; }
}
