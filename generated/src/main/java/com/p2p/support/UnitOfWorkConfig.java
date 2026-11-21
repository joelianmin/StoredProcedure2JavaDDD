package com.p2p.support;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

/** Memilih UnitOfWork sesuai profil: memory (Map + snapshot) atau postgres (transaksi JDBC). */
@Configuration
public class UnitOfWorkConfig {

    @Bean
    @Profile("memory")
    public UnitOfWork snapshotUnitOfWork(List<Snapshotable> repositories) {
        return new SnapshotUnitOfWork(repositories);
    }

    @Bean
    @Profile("postgres")
    public UnitOfWork springTxUnitOfWork(PlatformTransactionManager transactionManager) {
        return new SpringTxUnitOfWork(transactionManager);
    }
}
