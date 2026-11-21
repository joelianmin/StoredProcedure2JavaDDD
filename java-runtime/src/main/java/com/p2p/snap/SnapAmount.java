package com.p2p.snap;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Nominal uang format SNAP: {"value": "4000000.00", "currency": "IDR"}. */
public record SnapAmount(String value, String currency) {
    private static final Pattern VALUE = Pattern.compile("^\\d{1,13}\\.\\d{2}$");

    public BigDecimal toBigDecimal(String field) {
        if (value == null || currency == null) {
            throw new SnapRequestException(400, "02", "Invalid Mandatory Field {" + field + "}");
        }
        if (!VALUE.matcher(value).matches() || !"IDR".equals(currency)) {
            throw new SnapRequestException(400, "01", "Invalid Field Format {" + field + "}");
        }
        return new BigDecimal(value);
    }
}
