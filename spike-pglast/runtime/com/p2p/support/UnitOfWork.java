package com.p2p.support;

import java.util.List;

/** Satu pemanggilan procedure = satu transaksi: exception -> semua repository dikembalikan. */
public class UnitOfWork {
    private final List<Snapshotable> repos;
    public UnitOfWork(Snapshotable... repos) { this.repos = List.of(repos); }

    public void run(Runnable work) {
        repos.forEach(Snapshotable::takeSnapshot);
        try {
            work.run();
        } catch (RuntimeException e) {
            repos.forEach(Snapshotable::restoreSnapshot);   // ROLLBACK
            throw e;
        }
    }
}
