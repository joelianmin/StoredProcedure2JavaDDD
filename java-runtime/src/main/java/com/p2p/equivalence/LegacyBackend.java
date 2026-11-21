package com.p2p.equivalence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/** Acuan kebenaran: stored procedure asli dijalankan dengan CALL di p2p_legacy. */
public class LegacyBackend implements Backend {
    private final SchemaInfo schema;
    private final DataSource ds;

    public LegacyBackend(SchemaInfo schema, DataSource ds) {
        this.schema = schema;
        this.ds = ds;
    }

    @Override
    public String name() {
        return "legacy";
    }

    @Override
    public void reset() {
        withConn(c -> PgSql.truncateAll(c, schema));
    }

    @Override
    public void insert(String table, List<Map<String, String>> rows) {
        withConn(c -> {
            for (Map<String, String> r : rows) PgSql.insert(c, schema.table(table), r);
        });
    }

    @Override
    public Outcome call(String procedure, List<String> args) {
        try (Connection c = ds.getConnection()) {
            return PgSql.call(c, schema.procedure(procedure), args);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Map<String, List<Map<String, Object>>> dump() {
        try (Connection c = ds.getConnection()) {
            return PgSql.dump(c, schema);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    interface SqlWork {
        void run(Connection c) throws SQLException;
    }

    void withConn(SqlWork w) {
        try (Connection c = ds.getConnection()) {
            w.run(c);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
