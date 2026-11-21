package com.p2p.equivalence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Membandingkan isi tabel lama vs baru per primary key; numeric dibandingkan nilainya (10.0 = 10.00). */
public final class TableDiff {
    private TableDiff() {}

    public static List<String> compare(SchemaInfo schema, Map<String, List<Map<String, Object>>> lama,
                                       Map<String, List<Map<String, Object>>> baru) {
        List<String> diffs = new ArrayList<>();
        for (SchemaInfo.TableInfo t : schema.tables()) {
            Map<Object, Map<String, Object>> a = byPk(t, lama.getOrDefault(t.name(), List.of()));
            Map<Object, Map<String, Object>> b = byPk(t, baru.getOrDefault(t.name(), List.of()));
            for (var e : a.entrySet()) {
                Map<String, Object> rb = b.get(e.getKey());
                if (rb == null) {
                    diffs.add(t.name() + "[" + e.getKey() + "]: ada di lama, hilang di baru");
                    continue;
                }
                for (String col : t.columnTypes().keySet()) {
                    Object va = e.getValue().get(col), vb = rb.get(col);
                    if (!same(va, vb)) diffs.add(t.name() + "[" + e.getKey() + "]." + col + ": lama=" + va + " baru=" + vb);
                }
            }
            for (Object k : b.keySet()) {
                if (!a.containsKey(k)) diffs.add(t.name() + "[" + k + "]: tidak ada di lama, muncul di baru");
            }
        }
        return diffs;
    }

    private static Map<Object, Map<String, Object>> byPk(SchemaInfo.TableInfo t, List<Map<String, Object>> rows) {
        Map<Object, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) out.put(r.get(t.pk()), r);
        return out;
    }

    static boolean same(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y) == 0;
        return Objects.equals(a, b);
    }
}
