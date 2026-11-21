package com.p2p.support;

/** Satu pemanggilan procedure = satu transaksi. Exception di dalam work membatalkan semua perubahan. */
public interface UnitOfWork {
    void run(Runnable work);
}
