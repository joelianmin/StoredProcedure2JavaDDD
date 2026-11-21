package com.p2p.support;

import java.sql.SQLException;

import org.postgresql.util.PSQLException;

/** Mengubah error JDBC/PostgreSQL menjadi ProcedureException dengan SQLSTATE dan pesan server yang bersih. */
public final class SqlStates {
    private SqlStates() {}

    public static ProcedureException toProcedureException(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ProcedureException pe) return pe;
            if (c instanceof PSQLException pe && pe.getServerErrorMessage() != null) {
                return new ProcedureException(pe.getSQLState(), pe.getServerErrorMessage().getMessage());
            }
            if (c instanceof SQLException se && se.getSQLState() != null) {
                return new ProcedureException(se.getSQLState(), se.getMessage());
            }
        }
        return new ProcedureException("XX000", String.valueOf(t.getMessage()));
    }
}
