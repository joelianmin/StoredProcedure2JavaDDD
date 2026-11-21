// DIHASILKAN OTOMATIS oleh migrator dari tabel `lender` - jangan diedit manual.
package com.p2p.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.p2p.domain.Lender;
import com.p2p.repository.LenderRepository;
import com.p2p.support.ProcedureException;
import com.p2p.support.Snapshotable;
import com.p2p.support.Sql;

/** Repository berbasis Map (gaya semester lalu) yang meniru aturan kolom PostgreSQL dan bisa di-rollback. */
@Repository
@Profile("memory")
public class InMemoryLenderRepository implements LenderRepository, Snapshotable {
    private TreeMap<String, Lender> data = new TreeMap<>();
    private TreeMap<String, Lender> snapshot;

    /** Meniru kolom: NOT NULL (23502) dan numeric(p,s) (pembulatan / 22003). */
    private Lender checked(Lender e) {
        Lender c = e.copy();
        if (c.getId() == null) {
            throw new ProcedureException("23502", "null value in column \"id\" of relation \"lender\" violates not-null constraint");
        }
        if (c.getName() == null) {
            throw new ProcedureException("23502", "null value in column \"name\" of relation \"lender\" violates not-null constraint");
        }
        if (c.getBalance() == null) {
            throw new ProcedureException("23502", "null value in column \"balance\" of relation \"lender\" violates not-null constraint");
        }
        c.setBalance(Sql.fitNumeric(c.getBalance(), 15, 2));
        return c;
    }

    @Override
    public void save(Lender e) {
        Lender c = checked(e);
        data.put(c.getId(), c);
    }

    @Override
    public void insert(Lender e) {
        Lender c = checked(e);
        if (data.containsKey(c.getId())) {
            throw new ProcedureException("23505", "duplicate key value violates unique constraint \"lender_pkey\"");
        }
        data.put(c.getId(), c);
    }

    @Override
    public Optional<Lender> findById(String id) {
        if (id == null) return Optional.empty();       // WHERE pk = NULL tidak cocok apa pun
        return Optional.ofNullable(data.get(id)).map(Lender::copy);
    }

    @Override
    public Optional<Lender> findByIdForUpdate(String id) {
        return findById(id);
    }

    @Override
    public List<Lender> findAll() {
        List<Lender> out = new ArrayList<>();
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
