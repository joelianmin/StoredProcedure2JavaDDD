// DIHASILKAN OTOMATIS oleh migrator - jangan diedit manual.
package com.p2p.equivalence;

import java.util.LinkedHashMap;
import java.util.List;

/** Metadata skema dan signature procedure korpus. */
public class GeneratedSchema implements SchemaInfo {
    private static LinkedHashMap<String, String> cols(String... nameType) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < nameType.length; i += 2) m.put(nameType[i], nameType[i + 1]);
        return m;
    }

    private static final List<TableInfo> TABLES = List.of(
            new TableInfo("borrower", "id", cols("id", "text", "name", "text", "kyc_verified", "boolean", "income", "numeric", "balance", "numeric", "loan_limit", "numeric")),
            new TableInfo("lender", "id", cols("id", "text", "name", "text", "balance", "numeric")),
            new TableInfo("loan", "id", cols("id", "text", "borrower_id", "text", "amount", "numeric", "collected_amount", "numeric", "tenor_months", "integer", "interest_rate", "numeric", "status", "text")),
            new TableInfo("funding", "id", cols("id", "text", "loan_id", "text", "lender_id", "text", "amount", "numeric")));

    private static final List<ProcedureInfo> PROCEDURES = List.of(
            new ProcedureInfo("ajukan_pinjaman", List.of("p_loan_id", "p_borrower_id", "p_jumlah", "p_tenor"),
                    List.of("text", "text", "numeric", "integer")),
            new ProcedureInfo("batalkan_pinjaman", List.of("p_loan_id"),
                    List.of("text")),
            new ProcedureInfo("bayar_pinjaman", List.of("p_loan_id"),
                    List.of("text")),
            new ProcedureInfo("cairkan_pinjaman", List.of("p_loan_id"),
                    List.of("text")),
            new ProcedureInfo("danai_pinjaman", List.of("p_funding_id", "p_loan_id", "p_lender_id", "p_jumlah"),
                    List.of("text", "text", "text", "numeric")),
            new ProcedureInfo("uji_and_pendek", List.of("p_jumlah", "p_tenor"),
                    List.of("numeric", "integer")),
            new ProcedureInfo("uji_found_for", List.of("p_loan_id"),
                    List.of("text")),
            new ProcedureInfo("uji_null_cabang", List.of("p_flag"),
                    List.of("boolean")));

    @Override
    public List<TableInfo> tables() {
        return TABLES;
    }

    @Override
    public List<ProcedureInfo> procedures() {
        return PROCEDURES;
    }
}
