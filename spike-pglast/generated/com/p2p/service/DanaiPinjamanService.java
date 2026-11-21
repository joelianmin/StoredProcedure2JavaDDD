// DIHASILKAN OTOMATIS oleh migrator (spike) - jangan diedit manual.
package com.p2p.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.p2p.domain.Lender;
import com.p2p.repository.LenderRepository;
import com.p2p.domain.Loan;
import com.p2p.repository.LoanRepository;
import com.p2p.support.*;

/** Diterjemahkan dari stored procedure `danai_pinjaman`. Satu pemanggilan = satu transaksi. */
public class DanaiPinjamanService {
    private final LenderRepository lenderRepository;
    private final LoanRepository loanRepository;
    private final UnitOfWork unitOfWork;

    public DanaiPinjamanService(LenderRepository lenderRepository, LoanRepository loanRepository, UnitOfWork unitOfWork) {
        this.lenderRepository = lenderRepository;
        this.loanRepository = loanRepository;
        this.unitOfWork = unitOfWork;
    }

    public void execute(String loanId, String lenderId, BigDecimal jumlah) {
        unitOfWork.run(() -> {
            boolean found = false;
            BigDecimal target = null;
            BigDecimal terkumpul = null;
            String status = null;
            BigDecimal saldo = null;

            Optional<Loan> r1 = loanRepository.findById(loanId);
            found = r1.isPresent();
            target = r1.map(Loan::getAmount).orElse(null);
            terkumpul = r1.map(Loan::getCollectedAmount).orElse(null);
            status = r1.map(Loan::getStatus).orElse(null);
            if (Sql.isTrue(Sql.not(found))) {
                throw new ProcedureException("P0001", Sql.format("Loan tidak ditemukan: %", loanId));
            }
            Optional<Lender> r2 = lenderRepository.findById(lenderId);
            found = r2.isPresent();
            saldo = r2.map(Lender::getBalance).orElse(null);
            if (Sql.isTrue(Sql.not(found))) {
                throw new ProcedureException("P0001", Sql.format("Lender tidak ditemukan: %", lenderId));
            }
            if (Sql.isTrue(Sql.ne(status, "PENDING"))) {
                throw new ProcedureException("P0001", Sql.format("Loan % tidak dalam status PENDING", loanId));
            }
            { // UPDATE lender ... WHERE id = ...
                Optional<Lender> hit = lenderRepository.findById(lenderId);
                found = hit.isPresent();
                if (found) {
                    Lender row = hit.get();
                    var set0 = Sql.sub(row.getBalance(), jumlah);
                    row.setBalance(set0);
                    lenderRepository.save(row);
                }
            }
            if (Sql.isTrue(Sql.gt(jumlah, Sql.sub(target, terkumpul)))) {
                throw new ProcedureException("P0001", Sql.format("Pendanaan melebihi sisa target pinjaman"));
            }
            { // UPDATE loan ... WHERE id = ...
                Optional<Loan> hit = loanRepository.findById(loanId);
                found = hit.isPresent();
                if (found) {
                    Loan row = hit.get();
                    var set0 = Sql.add(row.getCollectedAmount(), jumlah);
                    row.setCollectedAmount(set0);
                    loanRepository.save(row);
                }
            }
            if (Sql.isTrue(Sql.ge(Sql.add(terkumpul, jumlah), target))) {
                { // UPDATE loan ... WHERE id = ...
                    Optional<Loan> hit = loanRepository.findById(loanId);
                    found = hit.isPresent();
                    if (found) {
                        Loan row = hit.get();
                        var set0 = "FUNDED";
                        row.setStatus(set0);
                        loanRepository.save(row);
                    }
                }
            }
        });
    }
}
