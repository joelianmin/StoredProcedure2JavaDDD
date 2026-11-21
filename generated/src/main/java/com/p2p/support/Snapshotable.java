package com.p2p.support;

/** Repository yang bisa di-rollback oleh SnapshotUnitOfWork (varian Map). */
public interface Snapshotable {
    void takeSnapshot();

    void restoreSnapshot();

    void discardSnapshot();
}
