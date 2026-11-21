package com.p2p.snap;

import java.time.LocalDate;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Disimpan di tabel snap_external_id (lihat schema-api.sql). */
@Component
@Profile("postgres")
public class JdbcExternalIdRegistry implements ExternalIdRegistry {
    private final JdbcTemplate jdbc;

    public JdbcExternalIdRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean register(String partnerId, String externalId, LocalDate day) {
        return jdbc.update("INSERT INTO snap_external_id (partner_id, external_id, day) VALUES (?, ?, ?) "
                + "ON CONFLICT DO NOTHING", partnerId, externalId, day) == 1;
    }
}
