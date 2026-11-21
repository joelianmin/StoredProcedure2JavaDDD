package com.p2p.support;

/** Padanan error PostgreSQL: membawa SQLSTATE (P0001 = RAISE EXCEPTION, 23502 NOT NULL, 23505 PK ganda, ...). */
public class ProcedureException extends RuntimeException {
    private final String sqlState;

    public ProcedureException(String sqlState, String message) {
        super(message);
        this.sqlState = sqlState;
    }

    public String getSqlState() {
        return sqlState;
    }
}
