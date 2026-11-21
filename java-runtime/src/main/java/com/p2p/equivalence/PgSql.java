package com.p2p.equivalence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.postgresql.util.PSQLException;

/** Operasi JDBC generik untuk testkit: insert bertipe, CALL, dump, truncate. */
public final class PgSql {
    private PgSql() {}

    public static void insert(Connection c, SchemaInfo.TableInfo t, Map<String, String> row) throws SQLException {
        List<String> cols = new ArrayList<>(row.keySet());
        String sql = "INSERT INTO " + t.name() + " (" + String.join(", ", cols) + ") VALUES ("
                + cols.stream().map(k -> "?::" + type(t, k)).collect(Collectors.joining(", ")) + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < cols.size(); i++) bind(ps, i + 1, row.get(cols.get(i)));
            ps.executeUpdate();
        }
    }

    private static String type(SchemaInfo.TableInfo t, String col) {
        String ty = t.columnTypes().get(col);
        if (ty == null) throw new IllegalArgumentException("kolom tidak dikenal: " + t.name() + "." + col);
        return ty;
    }

    private static void bind(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null) ps.setNull(i, Types.VARCHAR);
        else ps.setString(i, v);
    }

    public static Outcome call(Connection c, SchemaInfo.ProcedureInfo p, List<String> args) throws SQLException {
        String sql = "CALL " + p.name() + "("
                + p.paramTypes().stream().map(t -> "?::" + t).collect(Collectors.joining(", ")) + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) bind(ps, i + 1, args.get(i));
            ps.execute();
            return Outcome.ok();
        } catch (PSQLException e) {
            String msg = e.getServerErrorMessage() != null ? e.getServerErrorMessage().getMessage() : e.getMessage();
            return new Outcome(false, e.getSQLState(), msg);
        }
    }

    public static Map<String, List<Map<String, Object>>> dump(Connection c, SchemaInfo schema) throws SQLException {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (SchemaInfo.TableInfo t : schema.tables()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM " + t.name() + " ORDER BY " + t.pk())) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String col : t.columnTypes().keySet()) row.put(col, normalize(rs.getObject(col)));
                    rows.add(row);
                }
            }
            out.put(t.name(), rows);
        }
        return out;
    }

    public static Object normalize(Object v) {
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        return v;
    }

    public static void truncateAll(Connection c, SchemaInfo schema) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("TRUNCATE " + schema.tables().stream().map(SchemaInfo.TableInfo::name)
                    .collect(Collectors.joining(", ")));
        }
    }
}
