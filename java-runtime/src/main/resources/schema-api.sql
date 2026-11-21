-- Tabel milik lapisan API SNAP (idempotensi X-EXTERNAL-ID). Tidak di-parse migrator, tidak ikut uji kesetaraan.
CREATE TABLE snap_external_id (
    partner_id  text NOT NULL,
    external_id text NOT NULL,
    day         date NOT NULL,
    PRIMARY KEY (partner_id, external_id, day)
);
