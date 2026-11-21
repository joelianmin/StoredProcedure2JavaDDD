package com.p2p.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Meniru semantik SQL PostgreSQL: NULL merambat, logika tiga nilai (TRUE/FALSE/NULL). */
public final class Sql {
    private Sql() {}

    // --- aritmetika: NULL merambat (J2) ---
    public static BigDecimal add(BigDecimal a, BigDecimal b) { return a == null || b == null ? null : a.add(b); }
    public static BigDecimal sub(BigDecimal a, BigDecimal b) { return a == null || b == null ? null : a.subtract(b); }
    public static BigDecimal mul(BigDecimal a, BigDecimal b) { return a == null || b == null ? null : a.multiply(b); }
    public static BigDecimal div(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return null;
        if (b.signum() == 0) throw new ProcedureException("22012", "division by zero");
        return a.divide(b, MathContext.DECIMAL128);
    }
    /** numeric(p,s): dibulatkan half away from zero (J5, J11). */
    public static BigDecimal scale(BigDecimal v, int s) { return v.setScale(s, RoundingMode.HALF_UP); }

    // --- perbandingan: NULL jika salah satu NULL (J1); numeric pakai compareTo (J9) ---
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Integer cmp(Object a, Object b) {
        if (a == null || b == null) return null;
        return ((Comparable) a).compareTo(b);
    }
    public static Boolean eq(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c == 0; }
    public static Boolean ne(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c != 0; }
    public static Boolean lt(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c < 0; }
    public static Boolean le(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c <= 0; }
    public static Boolean gt(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c > 0; }
    public static Boolean ge(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c >= 0; }

    // --- logika tiga nilai ---
    public static Boolean not(Boolean a) { return a == null ? null : !a; }
    public static Boolean and(Boolean a, Boolean b) {
        if (Boolean.FALSE.equals(a) || Boolean.FALSE.equals(b)) return false;
        return a == null || b == null ? null : true;
    }
    public static Boolean or(Boolean a, Boolean b) {
        if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) return true;
        return a == null || b == null ? null : false;
    }
    public static Boolean isNull(Object a, boolean negated) { return negated ? a != null : a == null; }
    /** IF di PL/pgSQL hanya masuk jika kondisi TRUE; NULL diperlakukan seperti FALSE. */
    public static boolean isTrue(Boolean b) { return Boolean.TRUE.equals(b); }

    /** Format RAISE: setiap % diganti argumen berikutnya, NULL -> <NULL> (J10). */
    public static String format(String msg, Object... args) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (char ch : msg.toCharArray()) {
            if (ch == '%' && i < args.length) {
                Object a = args[i++];
                sb.append(a == null ? "<NULL>" : a instanceof BigDecimal bd ? bd.toPlainString() : a.toString());
            } else sb.append(ch);
        }
        return sb.toString();
    }
}
