package com.p2p.support;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Meniru semantik PostgreSQL di Java: NULL merambat, logika tiga nilai (TRUE/FALSE/NULL),
 * skala hasil numeric, overflow, dan format pesan RAISE. Dipakai kode hasil generate bila
 * analyzer tidak bisa memastikan sebuah nilai tidak NULL.
 */
public final class Sql {
    private Sql() {}

    private static final BigInteger NBASE = BigInteger.valueOf(10000);

    // ------------------------------------------------------------------ numeric (J2, J5, J6, J11)
    public static BigDecimal add(BigDecimal a, BigDecimal b) { return a == null || b == null ? null : a.add(b); }

    public static BigDecimal sub(BigDecimal a, BigDecimal b) { return a == null || b == null ? null : a.subtract(b); }

    public static BigDecimal mul(BigDecimal a, BigDecimal b) { return a == null || b == null ? null : a.multiply(b); }

    public static BigDecimal neg(BigDecimal a) { return a == null ? null : a.negate(); }

    /** Pembagian numeric dengan skala hasil seperti PostgreSQL (port select_div_scale), dibulatkan half away from zero. */
    public static BigDecimal div(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return null;
        if (b.signum() == 0) throw new ProcedureException("22012", "division by zero");
        int[] w1 = weightAndFirstDigit(a), w2 = weightAndFirstDigit(b);
        int qweight = w1[0] - w2[0];
        if (w1[1] <= w2[1]) qweight--;
        int rscale = 16 - qweight * 4;
        rscale = Math.max(rscale, Math.max(a.scale(), 0));
        rscale = Math.max(rscale, Math.max(b.scale(), 0));
        rscale = Math.max(rscale, 0);
        rscale = Math.min(rscale, 1000);
        return a.divide(b, rscale, RoundingMode.HALF_UP);
    }

    /** {bobot basis-10000 digit pertama yang bukan nol, nilai digit itu}; {0,0} untuk nol. */
    private static int[] weightAndFirstDigit(BigDecimal x) {
        BigDecimal abs = x.abs();
        if (abs.signum() == 0) return new int[] {0, 0};
        int exp10 = abs.precision() - abs.scale() - 1;           // posisi digit desimal paling signifikan
        int weight = Math.floorDiv(exp10, 4);
        BigInteger group = abs.movePointLeft(4 * weight).toBigInteger().mod(NBASE);
        return new int[] {weight, group.intValue()};
    }

    public static BigDecimal round(BigDecimal v, Integer scale) {
        if (v == null || scale == null) return null;
        return v.setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal round(BigDecimal v) { return round(v, 0); }

    /** Simpan ke kolom numeric(p,s): bulatkan ke s; melebihi presisi -> 22003. */
    public static BigDecimal fitNumeric(BigDecimal v, int precision, int scale) {
        if (v == null) return null;
        BigDecimal r = v.setScale(scale, RoundingMode.HALF_UP);
        if (r.abs().compareTo(BigDecimal.TEN.pow(precision - scale)) >= 0) {
            throw new ProcedureException("22003", "numeric field overflow");
        }
        return r;
    }

    public static BigDecimal num(Integer v) { return v == null ? null : BigDecimal.valueOf(v.longValue()); }

    // ------------------------------------------------------------------ integer (J7, overflow)
    private static Integer checked(long r) {
        if (r < Integer.MIN_VALUE || r > Integer.MAX_VALUE) throw new ProcedureException("22003", "integer out of range");
        return (int) r;
    }

    public static Integer iadd(Integer a, Integer b) { return a == null || b == null ? null : checked((long) a + b); }

    public static Integer isub(Integer a, Integer b) { return a == null || b == null ? null : checked((long) a - b); }

    public static Integer imul(Integer a, Integer b) { return a == null || b == null ? null : checked((long) a * b); }

    public static Integer idiv(Integer a, Integer b) {
        if (a == null || b == null) return null;
        if (b == 0) throw new ProcedureException("22012", "division by zero");
        return checked((long) a / b);                            // dibulatkan ke arah nol, sama dengan PostgreSQL
    }

    public static Integer ineg(Integer a) { return a == null ? null : checked(-(long) a); }

    // ------------------------------------------------------------------ teks (J8)
    public static String concat(Object a, Object b) { return a == null || b == null ? null : text(a) + text(b); }

    @SafeVarargs
    public static <T> T coalesce(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }

    // ------------------------------------------------------------------ perbandingan (J1, J9)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Integer cmp(Object a, Object b) {
        if (a == null || b == null) return null;
        if (a instanceof Integer i && b instanceof BigDecimal) a = num(i);
        if (b instanceof Integer i && a instanceof BigDecimal) b = num(i);
        return ((Comparable) a).compareTo(b);                    // BigDecimal: 10.0 = 10.00
    }

    public static Boolean eq(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c == 0; }

    public static Boolean ne(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c != 0; }

    public static Boolean lt(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c < 0; }

    public static Boolean le(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c <= 0; }

    public static Boolean gt(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c > 0; }

    public static Boolean ge(Object a, Object b) { Integer c = cmp(a, b); return c == null ? null : c >= 0; }

    // ------------------------------------------------------------------ logika tiga nilai
    public static Boolean not(Boolean a) { return a == null ? null : !a; }

    public static Boolean and(Boolean a, Boolean b) {
        if (Boolean.FALSE.equals(a) || Boolean.FALSE.equals(b)) return false;
        return a == null || b == null ? null : true;
    }

    public static Boolean or(Boolean a, Boolean b) {
        if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) return true;
        return a == null || b == null ? null : false;
    }

    /** IF di PL/pgSQL hanya masuk bila kondisi TRUE; NULL diperlakukan seperti FALSE. */
    public static boolean isTrue(Boolean b) { return Boolean.TRUE.equals(b); }

    // ------------------------------------------------------------------ RAISE (J10)
    /** Setiap % diganti argumen berikutnya, %% menjadi %, NULL menjadi &lt;NULL&gt;. */
    public static String format(String template, Object... args) {
        StringBuilder sb = new StringBuilder();
        int next = 0;
        for (int i = 0; i < template.length(); i++) {
            char ch = template.charAt(i);
            if (ch == '%' && i + 1 < template.length() && template.charAt(i + 1) == '%') {
                sb.append('%');
                i++;
            } else if (ch == '%' && next < args.length) {
                Object a = args[next++];
                sb.append(a == null ? "<NULL>" : text(a));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** Representasi teks seperti fungsi output tipe PostgreSQL. */
    public static String text(Object v) {
        if (v instanceof BigDecimal bd) return bd.toPlainString();
        if (v instanceof Boolean b) return b ? "t" : "f";
        return String.valueOf(v);
    }
}
