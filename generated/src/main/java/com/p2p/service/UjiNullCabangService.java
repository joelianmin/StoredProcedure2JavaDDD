// DIHASILKAN OTOMATIS oleh migrator dari uji_null_cabang.sql - jangan diedit manual.
package com.p2p.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.p2p.support.ProcedureException;
import com.p2p.support.Sql;
import com.p2p.support.UnitOfWork;

/**
 * Terjemahan stored procedure {@code uji_null_cabang(p_flag boolean)}.
 * Satu pemanggilan {@link #execute} = satu transaksi (exception membatalkan semua perubahan).
 */
@Service
@SuppressWarnings({"unused", "UnusedAssignment"})
public class UjiNullCabangService {
    private final UnitOfWork unitOfWork;

    public UjiNullCabangService(UnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
    }

    public void execute(Boolean flag) {
        unitOfWork.run(() -> {
            BigDecimal extra = null;
            String s = null;

            // uji_null_cabang.sql:8
            if (Sql.isTrue(flag)) {
                // uji_null_cabang.sql:9
                extra = Sql.num(Integer.valueOf(100));
                // uji_null_cabang.sql:10
                s = "X";
            }
            // uji_null_cabang.sql:12
            if (Sql.isTrue(Sql.gt(extra, Sql.num(Integer.valueOf(50))))) {
                // uji_null_cabang.sql:13
                throw new ProcedureException("P0001", "extra besar");
            }
            // uji_null_cabang.sql:15
            if (Sql.isTrue(Sql.ne("APPROVED", s))) {
                // uji_null_cabang.sql:16
                throw new ProcedureException("P0001", "status beda");
            }
        });
    }
}
