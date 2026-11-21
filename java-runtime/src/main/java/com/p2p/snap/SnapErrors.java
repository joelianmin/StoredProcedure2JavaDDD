package com.p2p.snap;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import com.p2p.support.ProcedureException;

/** Menyusun responseCode SNAP = HTTP (3 digit) + kode layanan (2 digit) + kode kasus (2 digit). */
public final class SnapErrors {
    private static final Logger LOG = LoggerFactory.getLogger(SnapErrors.class);

    private SnapErrors() {}

    public static String code(int http, String serviceCode, String caseCode) {
        return String.format("%03d", http) + serviceCode + caseCode;
    }

    public static ResponseEntity<SnapResponse> success(String serviceCode, String ref) {
        return ResponseEntity.ok(new SnapResponse(code(200, serviceCode, "00"), "Successful", ref));
    }

    public static ResponseEntity<SnapResponse> fromRequest(SnapRequestException e, String serviceCode, String ref) {
        return ResponseEntity.status(e.getHttp())
                .body(new SnapResponse(code(e.getHttp(), serviceCode, e.getCaseCode()), e.getMessage(), ref));
    }

    /** Error dari procedure. Pesan asli RAISE hanya masuk log, tidak dikirim ke klien. */
    public static ResponseEntity<SnapResponse> fromProcedure(ProcedureException e, String serviceCode,
                                                             List<ErrorRule> rules, String ref) {
        LOG.warn("procedure gagal: SQLSTATE={} pesan={}", e.getSqlState(), e.getMessage());
        int http;
        String caseCode;
        String message;
        switch (e.getSqlState()) {
            case "P0001" -> {
                ErrorRule rule = rules.stream().filter(r -> e.getMessage() != null && e.getMessage().startsWith(r.prefix()))
                        .findFirst().orElse(null);
                http = rule != null ? rule.http() : 403;
                caseCode = rule != null ? rule.caseCode() : "00";
                message = rule != null ? rule.message() : "Transaction Not Permitted";
            }
            case "23502", "22003" -> {
                http = 400;
                caseCode = "01";
                message = "Invalid Field Format";
            }
            case "23505" -> {
                http = 409;
                caseCode = "01";
                message = "Conflict";
            }
            default -> {
                http = 500;
                caseCode = "00";
                message = "General Error";
            }
        }
        return ResponseEntity.status(http).body(new SnapResponse(code(http, serviceCode, caseCode), message, ref));
    }
}
