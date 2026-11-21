package com.p2p.equivalence;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;

/** Koneksi ke p2p_legacy (dengan stored procedure) dan p2p_baru (tabel saja). */
public final class TestDatabases {
    private TestDatabases() {}

    private static DataSource of(String db) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[] {env("P2P_PG_HOST", "localhost")});
        ds.setPortNumbers(new int[] {Integer.parseInt(env("P2P_PG_PORT", "5433"))});
        ds.setDatabaseName(db);
        ds.setUser(env("P2P_PG_USER", System.getProperty("user.name")));
        return ds;
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    public static DataSource legacy() {
        return of("p2p_legacy");
    }

    public static DataSource baru() {
        return of("p2p_baru");
    }
}
