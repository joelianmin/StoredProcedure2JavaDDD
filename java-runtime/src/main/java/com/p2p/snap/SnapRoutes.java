package com.p2p.snap;

import java.util.Optional;

/** Path endpoint -> kode layanan SNAP (di-generate dari mapping.yaml). */
public interface SnapRoutes {
    Optional<String> serviceCodeFor(String path);
}
