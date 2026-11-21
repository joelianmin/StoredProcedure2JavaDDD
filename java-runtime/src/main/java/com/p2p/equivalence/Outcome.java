package com.p2p.equivalence;

import java.util.Objects;

import com.p2p.support.ProcedureException;

/** Hasil satu pemanggilan: sukses, atau gagal dengan SQLSTATE dan pesan. */
public record Outcome(boolean success, String sqlState, String message) {
    public static Outcome ok() {
        return new Outcome(true, null, null);
    }

    public static Outcome failed(ProcedureException e) {
        return new Outcome(false, e.getSqlState(), e.getMessage());
    }

    /** Setara bila sukses/gagal sama dan SQLSTATE sama; pesan hanya dibandingkan untuk RAISE (P0001). */
    public boolean equivalentTo(Outcome other) {
        if (success != other.success) return false;
        if (success) return true;
        if (!Objects.equals(sqlState, other.sqlState)) return false;
        return !"P0001".equals(sqlState) || Objects.equals(message, other.message);
    }

    @Override
    public String toString() {
        return success ? "SUKSES" : "GAGAL " + sqlState + " \"" + message + "\"";
    }
}
