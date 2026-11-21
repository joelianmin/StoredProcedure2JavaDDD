// DIHASILKAN OTOMATIS oleh migrator dari tabel `loan` - jangan diedit manual.
package com.p2p.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.p2p.domain.Loan;
import com.p2p.repository.LoanRepository;

/** Repository JDBC ke PostgreSQL (tabel saja, tanpa stored procedure). */
@Repository
@Profile("postgres")
public class JdbcLoanRepository implements LoanRepository {
    private static final String COLS = "id, borrower_id, amount, collected_amount, tenor_months, interest_rate, status";
    private static final RowMapper<Loan> MAPPER = (rs, i) -> new Loan(
            rs.getString("id"),
            rs.getString("borrower_id"),
            rs.getBigDecimal("amount"),
            rs.getBigDecimal("collected_amount"),
            rs.getObject("tenor_months", Integer.class),
            rs.getBigDecimal("interest_rate"),
            rs.getString("status"));

    private final JdbcTemplate jdbc;

    public JdbcLoanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Object[] values(Loan e) {
        return new Object[] {e.getId(), e.getBorrowerId(), e.getAmount(), e.getCollectedAmount(), e.getTenorMonths(), e.getInterestRate(), e.getStatus()};
    }

    @Override
    public void save(Loan e) {
        jdbc.update("INSERT INTO loan (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (id) DO UPDATE SET borrower_id = EXCLUDED.borrower_id, amount = EXCLUDED.amount, collected_amount = EXCLUDED.collected_amount, tenor_months = EXCLUDED.tenor_months, interest_rate = EXCLUDED.interest_rate, status = EXCLUDED.status", values(e));
    }

    @Override
    public void insert(Loan e) {
        jdbc.update("INSERT INTO loan (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?)", values(e));
    }

    @Override
    public Optional<Loan> findById(String id) {
        return jdbc.query("SELECT " + COLS + " FROM loan WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<Loan> findByIdForUpdate(String id) {
        return jdbc.query("SELECT " + COLS + " FROM loan WHERE id = ? FOR UPDATE", MAPPER, id).stream().findFirst();
    }

    @Override
    public List<Loan> findAll() {
        return jdbc.query("SELECT " + COLS + " FROM loan ORDER BY id", MAPPER);
    }

    @Override
    public int deleteById(String id) {
        return jdbc.update("DELETE FROM loan WHERE id = ?", id);
    }
}
