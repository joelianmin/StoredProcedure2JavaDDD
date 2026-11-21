// DIHASILKAN OTOMATIS oleh migrator dari tabel `borrower` - jangan diedit manual.
package com.p2p.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.p2p.domain.Borrower;
import com.p2p.repository.BorrowerRepository;

/** Repository JDBC ke PostgreSQL (tabel saja, tanpa stored procedure). */
@Repository
@Profile("postgres")
public class JdbcBorrowerRepository implements BorrowerRepository {
    private static final String COLS = "id, name, kyc_verified, income, balance, loan_limit";
    private static final RowMapper<Borrower> MAPPER = (rs, i) -> new Borrower(
            rs.getString("id"),
            rs.getString("name"),
            rs.getObject("kyc_verified", Boolean.class),
            rs.getBigDecimal("income"),
            rs.getBigDecimal("balance"),
            rs.getBigDecimal("loan_limit"));

    private final JdbcTemplate jdbc;

    public JdbcBorrowerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Object[] values(Borrower e) {
        return new Object[] {e.getId(), e.getName(), e.getKycVerified(), e.getIncome(), e.getBalance(), e.getLoanLimit()};
    }

    @Override
    public void save(Borrower e) {
        jdbc.update("INSERT INTO borrower (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, kyc_verified = EXCLUDED.kyc_verified, income = EXCLUDED.income, balance = EXCLUDED.balance, loan_limit = EXCLUDED.loan_limit", values(e));
    }

    @Override
    public void insert(Borrower e) {
        jdbc.update("INSERT INTO borrower (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?)", values(e));
    }

    @Override
    public Optional<Borrower> findById(String id) {
        return jdbc.query("SELECT " + COLS + " FROM borrower WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<Borrower> findByIdForUpdate(String id) {
        return jdbc.query("SELECT " + COLS + " FROM borrower WHERE id = ? FOR UPDATE", MAPPER, id).stream().findFirst();
    }

    @Override
    public List<Borrower> findAll() {
        return jdbc.query("SELECT " + COLS + " FROM borrower ORDER BY id", MAPPER);
    }

    @Override
    public int deleteById(String id) {
        return jdbc.update("DELETE FROM borrower WHERE id = ?", id);
    }
}
