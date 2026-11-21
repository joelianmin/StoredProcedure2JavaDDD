// DIHASILKAN OTOMATIS oleh migrator dari batalkan_pinjaman.sql - jangan diedit manual.
package com.p2p.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.p2p.domain.Borrower;
import com.p2p.repository.BorrowerRepository;
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
 * Terjemahan stored procedure {@code batalkan_pinjaman(p_loan_id text)}.
 * Satu pemanggilan {@link #execute} = satu transaksi (exception membatalkan semua perubahan).
 */
@Service
@SuppressWarnings({"unused", "UnusedAssignment"})
public class BatalkanPinjamanService {
    private final BorrowerRepository borrowerRepository;
    private final FundingRepository fundingRepository;
    private final LenderRepository lenderRepository;
    private final LoanRepository loanRepository;
    private final UnitOfWork unitOfWork;

    public BatalkanPinjamanService(BorrowerRepository borrowerRepository, FundingRepository fundingRepository, LenderRepository lenderRepository, LoanRepository loanRepository, UnitOfWork unitOfWork) {
        this.borrowerRepository = borrowerRepository;
        this.fundingRepository = fundingRepository;
        this.lenderRepository = lenderRepository;
        this.loanRepository = loanRepository;
        this.unitOfWork = unitOfWork;
    }

    public void execute(String loanId) {
        unitOfWork.run(() -> {
            boolean found = false;
            BigDecimal amount = null;
            String status = null;
            String borrowerId = null;

            // batalkan_pinjaman.sql:10
            Optional<Loan> _loan1 = loanRepository.findById(loanId);
            found = _loan1.isPresent();
            amount = _loan1.map(Loan::getAmount).orElse(null);
            status = _loan1.map(Loan::getStatus).orElse(null);
            borrowerId = _loan1.map(Loan::getBorrowerId).orElse(null);
            // batalkan_pinjaman.sql:13
            if (!found) {
                // batalkan_pinjaman.sql:14
                throw new ProcedureException("P0001", Sql.format("Loan tidak ditemukan: %", loanId));
            }
            // batalkan_pinjaman.sql:17
            if (status.equals("DISBURSED") || status.equals("REPAID")) {
                // batalkan_pinjaman.sql:18
                throw new ProcedureException("P0001", "Pinjaman tidak dapat dibatalkan setelah dicairkan");
            }
            // batalkan_pinjaman.sql:21
            boolean _iterated2 = false;
            for (Funding r : fundingRepository.findByLoanId(loanId)) {
                _iterated2 = true;
                // batalkan_pinjaman.sql:22
                Optional<Lender> _hit3 = lenderRepository.findByIdForUpdate(r.getLenderId());
                found = _hit3.isPresent();
                if (_hit3.isPresent()) {
                    Lender _row = _hit3.get();
                    _row.setBalance(_row.getBalance().add(r.getAmount()));
                    lenderRepository.save(_row);
                }
            }
            found = _iterated2;
            // batalkan_pinjaman.sql:25
            found = fundingRepository.deleteByLoanId(loanId) > 0;
            // batalkan_pinjaman.sql:27
            Optional<Borrower> _hit4 = borrowerRepository.findByIdForUpdate(borrowerId);
            found = _hit4.isPresent();
            if (_hit4.isPresent()) {
                Borrower _row = _hit4.get();
                _row.setLoanLimit(_row.getLoanLimit().add(amount));
                borrowerRepository.save(_row);
            }
            // batalkan_pinjaman.sql:28
            Optional<Loan> _hit5 = loanRepository.findByIdForUpdate(loanId);
            found = _hit5.isPresent();
            if (_hit5.isPresent()) {
                Loan _row = _hit5.get();
                _row.setStatus("CANCELLED");
                _row.setCollectedAmount(Sql.num(Integer.valueOf(0)));
                loanRepository.save(_row);
            }
        });
    }
}
