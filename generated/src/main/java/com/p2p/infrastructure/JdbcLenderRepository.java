// DIHASILKAN OTOMATIS oleh migrator dari tabel `lender` - jangan diedit manual.
package com.p2p.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.p2p.domain.Lender;
import com.p2p.repository.LenderRepository;

/** Repository JDBC ke PostgreSQL (tabel saja, tanpa stored procedure). */
@Repository
@Profile("postgres")
public class JdbcLenderRepository implements LenderRepository {
    private static final String COLS = "id, name, balance";
    private static final RowMapper<Lender> MAPPER = (rs, i) -> new Lender(
            rs.getString("id"),
            rs.getString("name"),
            rs.getBigDecimal("balance"));

    private final JdbcTemplate jdbc;

    public JdbcLenderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Object[] values(Lender e) {
        return new Object[] {e.getId(), e.getName(), e.getBalance()};
    }

    @Override
    public void save(Lender e) {
        jdbc.update("INSERT INTO lender (" + COLS + ") VALUES (?, ?, ?)"
                + " ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, balance = EXCLUDED.balance", values(e));
    }

    @Override
    public void insert(Lender e) {
        jdbc.update("INSERT INTO lender (" + COLS + ") VALUES (?, ?, ?)", values(e));
    }

    @Override
    public Optional<Lender> findById(String id) {
        return jdbc.query("SELECT " + COLS + " FROM lender WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<Lender> findByIdForUpdate(String id) {
        return jdbc.query("SELECT " + COLS + " FROM lender WHERE id = ? FOR UPDATE", MAPPER, id).stream().findFirst();
    }

    @Override
    public List<Lender> findAll() {
        return jdbc.query("SELECT " + COLS + " FROM lender ORDER BY id", MAPPER);
    }

    @Override
    public int deleteById(String id) {
        return jdbc.update("DELETE FROM lender WHERE id = ?", id);
    }
}
