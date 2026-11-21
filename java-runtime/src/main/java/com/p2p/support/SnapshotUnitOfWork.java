package com.p2p.support;

import java.util.List;

/** Transaksi untuk repository Map: snapshot sebelum, pulihkan bila exception (meniru ROLLBACK PostgreSQL). */
public class SnapshotUnitOfWork implements UnitOfWork {
    private final List<Snapshotable> repositories;

    public SnapshotUnitOfWork(List<Snapshotable> repositories) {
        this.repositories = List.copyOf(repositories);
    }

    @Override
    public void run(Runnable work) {
        repositories.forEach(Snapshotable::takeSnapshot);
        try {
            work.run();
            repositories.forEach(Snapshotable::discardSnapshot);
        } catch (RuntimeException e) {
            repositories.forEach(Snapshotable::restoreSnapshot);
            throw e;
        }
    }
}
