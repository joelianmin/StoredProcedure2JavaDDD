package com.p2p.equivalence;

import java.util.List;

/** Disediakan kode hasil generate (GeneratedBackends) dan ditemukan lewat ServiceLoader. */
public interface BackendFactory {
    SchemaInfo schema();

    /** Urutan: legacy dulu (acuan kebenaran), lalu varian kode baru. */
    List<Backend> create();
}
