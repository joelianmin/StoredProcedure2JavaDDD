package com.p2p.snap;

import java.time.LocalDate;

/** Idempotensi X-EXTERNAL-ID: unik per partner per hari. */
public interface ExternalIdRegistry {
    /** @return true bila baru tercatat; false bila sudah pernah dipakai partner ini di hari yang sama. */
    boolean register(String partnerId, String externalId, LocalDate day);
}
