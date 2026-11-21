// DIHASILKAN OTOMATIS oleh migrator - jangan diedit manual.
package com.p2p.equivalence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.p2p.domain.Borrower;
import com.p2p.domain.Lender;
import com.p2p.domain.Loan;
import com.p2p.domain.Funding;

/** Konversi baris teks (Gherkin / mapping) <-> entity. */
public final class RowConverters {
    private RowConverters() {}

    public static String text(String v) { return v; }

    public static BigDecimal numeric(String v) { return v == null ? null : new BigDecimal(v); }

    public static Integer integer(String v) { return v == null ? null : Integer.valueOf(v); }

    public static Boolean bool(String v) {
        if (v == null) return null;
        return switch (v.toLowerCase()) {
            case "true", "t", "yes", "1" -> true;
            case "false", "f", "no", "0" -> false;
            default -> throw new IllegalArgumentException("boolean tidak valid: " + v);
        };
    }

    public static LocalDate date(String v) { return v == null ? null : LocalDate.parse(v); }

    public static Borrower borrower(Map<String, String> row) {
        return new Borrower(text(row.get("id")), text(row.get("name")), bool(row.get("kyc_verified")), numeric(row.get("income")), numeric(row.get("balance")), numeric(row.get("loan_limit")));
    }

    public static Map<String, Object> row(Borrower e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("kyc_verified", e.getKycVerified());
        m.put("income", e.getIncome());
        m.put("balance", e.getBalance());
        m.put("loan_limit", e.getLoanLimit());
        return m;
    }

    public static Lender lender(Map<String, String> row) {
        return new Lender(text(row.get("id")), text(row.get("name")), numeric(row.get("balance")));
    }

    public static Map<String, Object> row(Lender e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("balance", e.getBalance());
        return m;
    }

    public static Loan loan(Map<String, String> row) {
        return new Loan(text(row.get("id")), text(row.get("borrower_id")), numeric(row.get("amount")), numeric(row.get("collected_amount")), integer(row.get("tenor_months")), numeric(row.get("interest_rate")), text(row.get("status")));
    }

    public static Map<String, Object> row(Loan e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("borrower_id", e.getBorrowerId());
        m.put("amount", e.getAmount());
        m.put("collected_amount", e.getCollectedAmount());
        m.put("tenor_months", e.getTenorMonths());
        m.put("interest_rate", e.getInterestRate());
        m.put("status", e.getStatus());
        return m;
    }

    public static Funding funding(Map<String, String> row) {
        return new Funding(text(row.get("id")), text(row.get("loan_id")), text(row.get("lender_id")), numeric(row.get("amount")));
    }

    public static Map<String, Object> row(Funding e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("loan_id", e.getLoanId());
        m.put("lender_id", e.getLenderId());
        m.put("amount", e.getAmount());
        return m;
    }
}
