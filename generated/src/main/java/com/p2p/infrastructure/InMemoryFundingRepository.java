// DIHASILKAN OTOMATIS oleh migrator dari tabel `funding` - jangan diedit manual.
package com.p2p.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.p2p.domain.Funding;
import com.p2p.repository.FundingRepository;
import com.p2p.support.ProcedureException;
import com.p2p.support.Snapshotable;
import com.p2p.support.Sql;

/** Repository berbasis Map (gaya semester lalu) yang meniru aturan kolom PostgreSQL dan bisa di-rollback. */
@Repository
@Profile("memory")
public class InMemoryFundingRepository implements FundingRepository, Snapshotable {
    private TreeMap<String, Funding> data = new TreeMap<>();
    private TreeMap<String, Funding> snapshot;

    /** Meniru kolom: NOT NULL (23502) dan numeric(p,s) (pembulatan / 22003). */
    private Funding checked(Funding e) {
        Funding c = e.copy();
        if (c.getId() == null) {
            throw new ProcedureException("23502", "null value in column \"id\" of relation \"funding\" violates not-null constraint");
        }
        if (c.getLoanId() == null) {
            throw new ProcedureException("23502", "null value in column \"loan_id\" of relation \"funding\" violates not-null constraint");
        }
        if (c.getLenderId() == null) {
            throw new ProcedureException("23502", "null value in column \"lender_id\" of relation \"funding\" violates not-null constraint");
        }
        if (c.getAmount() == null) {
            throw new ProcedureException("23502", "null value in column \"amount\" of relation \"funding\" violates not-null constraint");
        }
        c.setAmount(Sql.fitNumeric(c.getAmount(), 15, 2));
        return c;
    }

    @Override
    public void save(Funding e) {
        Funding c = checked(e);
        data.put(c.getId(), c);
    }

    @Override
    public void insert(Funding e) {
        Funding c = checked(e);
        if (data.containsKey(c.getId())) {
            throw new ProcedureException("23505", "duplicate key value violates unique constraint \"funding_pkey\"");
        }
        data.put(c.getId(), c);
    }

    @Override
    public Optional<Funding> findById(String id) {
        if (id == null) return Optional.empty();       // WHERE pk = NULL tidak cocok apa pun
        return Optional.ofNullable(data.get(id)).map(Funding::copy);
    }

    @Override
    public Optional<Funding> findByIdForUpdate(String id) {
        return findById(id);
    }

    @Override
    public List<Funding> findAll() {
        List<Funding> out = new ArrayList<>();
        data.values().forEach(e -> out.add(e.copy()));
        return out;
    }

    @Override
    public int deleteById(String id) {
        if (id == null) return 0;
        return data.remove(id) != null ? 1 : 0;
    }

    @Override
    public List<Funding> findByLoanId(String loanId) {
        List<Funding> out = new ArrayList<>();
        for (Funding e : data.values()) {
            if (Sql.isTrue(Sql.eq(e.getLoanId(), loanId))) {
                out.add(e.copy());
            }
        }
        return out;
    }

    @Override
    public int deleteByLoanId(String loanId) {
        int before = data.size();
        data.values().removeIf(e -> Sql.isTrue(Sql.eq(e.getLoanId(), loanId)));
        return before - data.size();
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
