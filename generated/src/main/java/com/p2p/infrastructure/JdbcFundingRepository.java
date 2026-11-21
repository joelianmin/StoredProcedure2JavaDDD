// DIHASILKAN OTOMATIS oleh migrator dari tabel `funding` - jangan diedit manual.
package com.p2p.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.p2p.domain.Funding;
import com.p2p.repository.FundingRepository;

/** Repository JDBC ke PostgreSQL (tabel saja, tanpa stored procedure). */
@Repository
@Profile("postgres")
public class JdbcFundingRepository implements FundingRepository {
    private static final String COLS = "id, loan_id, lender_id, amount";
    private static final RowMapper<Funding> MAPPER = (rs, i) -> new Funding(
            rs.getString("id"),
            rs.getString("loan_id"),
            rs.getString("lender_id"),
            rs.getBigDecimal("amount"));

    private final JdbcTemplate jdbc;

    public JdbcFundingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Object[] values(Funding e) {
        return new Object[] {e.getId(), e.getLoanId(), e.getLenderId(), e.getAmount()};
    }

    @Override
    public void save(Funding e) {
        jdbc.update("INSERT INTO funding (" + COLS + ") VALUES (?, ?, ?, ?)"
                + " ON CONFLICT (id) DO UPDATE SET loan_id = EXCLUDED.loan_id, lender_id = EXCLUDED.lender_id, amount = EXCLUDED.amount", values(e));
    }

    @Override
    public void insert(Funding e) {
        jdbc.update("INSERT INTO funding (" + COLS + ") VALUES (?, ?, ?, ?)", values(e));
    }

    @Override
    public Optional<Funding> findById(String id) {
        return jdbc.query("SELECT " + COLS + " FROM funding WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<Funding> findByIdForUpdate(String id) {
        return jdbc.query("SELECT " + COLS + " FROM funding WHERE id = ? FOR UPDATE", MAPPER, id).stream().findFirst();
    }

    @Override
    public List<Funding> findAll() {
        return jdbc.query("SELECT " + COLS + " FROM funding ORDER BY id", MAPPER);
    }

    @Override
    public int deleteById(String id) {
        return jdbc.update("DELETE FROM funding WHERE id = ?", id);
    }

    @Override
    public List<Funding> findByLoanId(String loanId) {
        return jdbc.query("SELECT " + COLS + " FROM funding WHERE loan_id = ? ORDER BY id FOR UPDATE", MAPPER, loanId);
    }

    @Override
    public int deleteByLoanId(String loanId) {
        return jdbc.update("DELETE FROM funding WHERE loan_id = ?", loanId);
    }
}
