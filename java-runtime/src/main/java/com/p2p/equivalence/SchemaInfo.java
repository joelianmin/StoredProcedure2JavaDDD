package com.p2p.equivalence;

import java.util.LinkedHashMap;
import java.util.List;

/** Metadata skema dan signature procedure (di-generate dari korpus). */
public interface SchemaInfo {
    List<TableInfo> tables();

    List<ProcedureInfo> procedures();

    default TableInfo table(String name) {
        return tables().stream().filter(t -> t.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tabel tidak dikenal: " + name));
    }

    default ProcedureInfo procedure(String name) {
        return procedures().stream().filter(p -> p.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("procedure tidak dikenal: " + name));
    }

    record TableInfo(String name, String pk, LinkedHashMap<String, String> columnTypes) {}

    record ProcedureInfo(String name, List<String> paramNames, List<String> paramTypes) {}
}
