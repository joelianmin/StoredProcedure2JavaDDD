package com.p2p.snap;

import java.math.BigDecimal;

/** Validasi field body SNAP yang dipakai controller hasil generate. */
public final class SnapFields {
    private SnapFields() {}

    /** Field wajib: null/kosong -> 400xx02 "Invalid Mandatory Field {field}". */
    public static <T> T require(T value, String field) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new SnapRequestException(400, "02", "Invalid Mandatory Field {" + field + "}");
        }
        return value;
    }

    /** Nominal SNAP wajib -> BigDecimal (format salah -> 400xx01). */
    public static BigDecimal amount(SnapAmount amount, String field) {
        return require(amount, field).toBigDecimal(field);
    }
}
