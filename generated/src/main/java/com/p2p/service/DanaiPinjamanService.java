// DIHASILKAN OTOMATIS oleh migrator dari danai_pinjaman.sql - jangan diedit manual.
package com.p2p.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.p2p.domain.Funding;
import com.p2p.repository.FundingRepository;
import com.p2p.domain.Lender;
import com.p2p.repository.LenderRepository;
import com.p2p.domain.Loan;
import com.p2p.repository.LoanRepository;
import com.p2p.support.ProcedureException;
import com.p2p.support.Sql;
import com.p2p.support.UnitOfWork;

/**
 * Terjemahan stored procedure {@code danai_pinjaman(p_funding_id text, p_loan_id text, p_lender_id text, p_jumlah numeric)}.
 * Satu pemanggilan {@link #execute} = satu transaksi (exception membatalkan semua perubahan).
 */
@Service
@SuppressWarnings({"unused", "UnusedAssignment"})
public class DanaiPinjamanService {
    private final FundingRepository fundingRepository;
    private final LenderRepository lenderRepository;
    private final LoanRepository loanRepository;
    private final UnitOfWork unitOfWork;

    public DanaiPinjamanService(FundingRepository fundingRepository, LenderRepository lenderRepository, LoanRepository loanRepository, UnitOfWork unitOfWork) {
        this.fundingRepository = fundingRepository;
        this.lenderRepository = lenderRepository;
        this.loanRepository = loanRepository;
        this.unitOfWork = unitOfWork;
    }

    public void execute(String fundingId, String loanId, String lenderId, BigDecimal jumlah) {
        unitOfWork.run(() -> {
            boolean found = false;
            BigDecimal target = null;
            BigDecimal terkumpul = null;
            String status = null;
            BigDecimal saldo = null;

            // danai_pinjaman.sql:11
            Optional<Loan> _loan1 = loanRepository.findById(loanId);
            found = _loan1.isPresent();
            target = _loan1.map(Loan::getAmount).orElse(null);
            terkumpul = _loan1.map(Loan::getCollectedAmount).orElse(null);
            status = _loan1.map(Loan::getStatus).orElse(null);
            // danai_pinjaman.sql:14
            if (!found) {
                // danai_pinjaman.sql:15
                throw new ProcedureException("P0001", Sql.format("Loan tidak ditemukan: %", loanId));
            }
            // danai_pinjaman.sql:18
            Optional<Lender> _lender2 = lenderRepository.findById(lenderId);
            found = _lender2.isPresent();
            saldo = _lender2.map(Lender::getBalance).orElse(null);
            // danai_pinjaman.sql:19
            if (!found) {
                // danai_pinjaman.sql:20
                throw new ProcedureException("P0001", Sql.format("Lender tidak ditemukan: %", lenderId));
            }
            // danai_pinjaman.sql:23
            if (!status.equals("PENDING")) {
                // danai_pinjaman.sql:24
                throw new ProcedureException("P0001", Sql.format("Loan % tidak dalam status PENDING", loanId));
            }
            // danai_pinjaman.sql:27
            if (Sql.isTrue(Sql.lt(saldo, jumlah))) {
                // danai_pinjaman.sql:28
                throw new ProcedureException("P0001", "Saldo lender tidak mencukupi");
            }
            // danai_pinjaman.sql:31
            Optional<Lender> _hit3 = lenderRepository.findByIdForUpdate(lenderId);
            found = _hit3.isPresent();
            if (_hit3.isPresent()) {
                Lender _row = _hit3.get();
                _row.setBalance(Sql.sub(_row.getBalance(), jumlah));
                lenderRepository.save(_row);
            }
            // danai_pinjaman.sql:33
            if (Sql.isTrue(Sql.gt(jumlah, target.subtract(terkumpul)))) {
                // danai_pinjaman.sql:34
                throw new ProcedureException("P0001", "Pendanaan melebihi sisa target pinjaman");
            }
            // danai_pinjaman.sql:37
            fundingRepository.insert(new Funding(fundingId, loanId, lenderId, jumlah));
            found = true;
            // danai_pinjaman.sql:40
            Optional<Loan> _hit4 = loanRepository.findByIdForUpdate(loanId);
            found = _hit4.isPresent();
            if (_hit4.isPresent()) {
                Loan _row = _hit4.get();
                _row.setCollectedAmount(Sql.add(_row.getCollectedAmount(), jumlah));
                loanRepository.save(_row);
            }
            // danai_pinjaman.sql:42
            if (Sql.isTrue(Sql.ge(Sql.add(terkumpul, jumlah), target))) {
                // danai_pinjaman.sql:43
                Optional<Loan> _hit5 = loanRepository.findByIdForUpdate(loanId);
                found = _hit5.isPresent();
                if (_hit5.isPresent()) {
                    Loan _row = _hit5.get();
                    _row.setStatus("FUNDED");
                    loanRepository.save(_row);
                }
            }
        });
    }
}
