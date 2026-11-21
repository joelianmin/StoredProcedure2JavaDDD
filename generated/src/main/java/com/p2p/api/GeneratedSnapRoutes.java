// DIHASILKAN OTOMATIS oleh migrator dari mapping.yaml - jangan diedit manual.
package com.p2p.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.p2p.snap.SnapRoutes;

/** Path endpoint SNAP -> kode layanan (90-94 adalah ekstensi non-standar untuk P2P lending). */
@Component
public class GeneratedSnapRoutes implements SnapRoutes {
    private static final Map<String, String> CODES = new LinkedHashMap<>();

    static {
        CODES.put("/v1.0/loan-submission", "90");   // ajukan_pinjaman
        CODES.put("/v1.0/loan-funding", "91");   // danai_pinjaman
        CODES.put("/v1.0/loan-disbursement", "92");   // cairkan_pinjaman
        CODES.put("/v1.0/loan-repayment", "93");   // bayar_pinjaman
        CODES.put("/v1.0/loan-cancellation", "94");   // batalkan_pinjaman
    }

    @Override
    public Optional<String> serviceCodeFor(String path) {
        return Optional.ofNullable(CODES.get(path));
    }
}
