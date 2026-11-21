// DIHASILKAN OTOMATIS oleh migrator dari uji_and_pendek.sql - jangan diedit manual.
package com.p2p.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.p2p.support.ProcedureException;
import com.p2p.support.Sql;
import com.p2p.support.UnitOfWork;

/**
 * Terjemahan stored procedure {@code uji_and_pendek(p_jumlah numeric, p_tenor integer)}.
 * Satu pemanggilan {@link #execute} = satu transaksi (exception membatalkan semua perubahan).
 */
@Service
@SuppressWarnings({"unused", "UnusedAssignment"})
public class UjiAndPendekService {
    private final UnitOfWork unitOfWork;

    public UjiAndPendekService(UnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
    }

    public void execute(BigDecimal jumlah, Integer tenor) {
        unitOfWork.run(() -> {
            BigDecimal cicilan = null;

            // uji_and_pendek.sql:7
            if (Sql.isTrue((Sql.isFalse(Sql.gt(tenor, Integer.valueOf(0))) ? Boolean.FALSE : Sql.and(Sql.gt(tenor, Integer.valueOf(0)), Sql.gt(Sql.div(jumlah, Sql.num(tenor)), Sql.num(Integer.valueOf(1000))))))) {
                // uji_and_pendek.sql:8
                throw new ProcedureException("P0001", "cicilan besar");
            }
            // uji_and_pendek.sql:10
            cicilan = (jumlah != null ? jumlah : Sql.div(jumlah, Sql.num(tenor)));
            // uji_and_pendek.sql:11
            if (cicilan == null) {
                // uji_and_pendek.sql:12
                throw new ProcedureException("P0001", "cicilan kosong");
            }
        });
    }
}
