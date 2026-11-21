package com.p2p.support;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Transaksi database sungguhan (varian PostgreSQL). Error database diterjemahkan ke ProcedureException. */
public class SpringTxUnitOfWork implements UnitOfWork {
    private final TransactionTemplate tx;

    public SpringTxUnitOfWork(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(Runnable work) {
        try {
            tx.executeWithoutResult(status -> work.run());
        } catch (DataAccessException e) {
            throw SqlStates.toProcedureException(e);
        }
    }
}
