// DIHASILKAN OTOMATIS oleh migrator (spike) - jangan diedit manual.
package com.p2p.infrastructure;

import java.util.*;

import com.p2p.domain.Lender;
import com.p2p.repository.LenderRepository;
import com.p2p.support.*;

/** Repository berbasis Map (gaya semester lalu) + snapshot untuk rollback. */
public class InMemoryLenderRepository implements LenderRepository, Snapshotable {
    private Map<String, Lender> data = new HashMap<>();
    private Map<String, Lender> snapshot;

    @Override public void save(Lender e) {
        if (e.getId() == null) throw new ProcedureException("23502", "null value in column \"id\" of relation \"lender\" violates not-null constraint");
        if (e.getBalance() == null) throw new ProcedureException("23502", "null value in column \"balance\" of relation \"lender\" violates not-null constraint");
        if (e.getBalance() != null) e.setBalance(Sql.scale(e.getBalance(), 2));
        data.put(e.getId(), e.copy());
    }
    @Override public Optional<Lender> findById(String id) {
        return Optional.ofNullable(data.get(id)).map(Lender::copy);
    }
    @Override public List<Lender> findAll() {
        List<Lender> out = new ArrayList<>();
        data.values().forEach(e -> out.add(e.copy()));
        return out;
    }
    @Override public void takeSnapshot() {
        snapshot = new HashMap<>();
        data.forEach((k, v) -> snapshot.put(k, v.copy()));
    }
    @Override public void restoreSnapshot() { data = snapshot; }
}
