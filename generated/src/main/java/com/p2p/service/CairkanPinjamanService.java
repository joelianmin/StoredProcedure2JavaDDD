// DIHASILKAN OTOMATIS oleh migrator dari cairkan_pinjaman.sql - jangan diedit manual.
package com.p2p.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.p2p.domain.Borrower;
import com.p2p.repository.BorrowerRepository;
import com.p2p.domain.Loan;
import com.p2p.repository.LoanRepository;
import com.p2p.support.ProcedureException;
import com.p2p.support.Sql;
import com.p2p.support.UnitOfWork;

/**
 * Terjemahan stored procedure {@code cairkan_pinjaman(p_loan_id text)}.
 * Satu pemanggilan {@link #execute} = satu transaksi (exception membatalkan semua perubahan).
 */
@Service
@SuppressWarnings({"unused", "UnusedAssignment"})
public class CairkanPinjamanService {
    private final BorrowerRepository borrowerRepository;
    private final LoanRepository loanRepository;
    private final UnitOfWork unitOfWork;

    public CairkanPinjamanService(BorrowerRepository borrowerRepository, LoanRepository loanRepository, UnitOfWork unitOfWork) {
        this.borrowerRepository = borrowerRepository;
        this.loanRepository = loanRepository;
        this.unitOfWork = unitOfWork;
    }

    public void execute(String loanId) {
        unitOfWork.run(() -> {
            boolean found = false;
            BigDecimal amount = null;
            String status = null;
            String borrowerId = null;

            // cairkan_pinjaman.sql:9
            Optional<Loan> _loan1 = loanRepository.findById(loanId);
            found = _loan1.isPresent();
            amount = _loan1.map(Loan::getAmount).orElse(null);
            status = _loan1.map(Loan::getStatus).orElse(null);
            borrowerId = _loan1.map(Loan::getBorrowerId).orElse(null);
            // cairkan_pinjaman.sql:12
            if (!found) {
                // cairkan_pinjaman.sql:13
                throw new ProcedureException("P0001", Sql.format("Loan tidak ditemukan: %", loanId));
            }
            // cairkan_pinjaman.sql:16
            if (!status.equals("FUNDED")) {
                // cairkan_pinjaman.sql:17
                throw new ProcedureException("P0001", "Hanya pinjaman berstatus FUNDED yang bisa dicairkan");
            }
            // cairkan_pinjaman.sql:20
            Optional<Borrower> _hit2 = borrowerRepository.findByIdForUpdate(borrowerId);
            found = _hit2.isPresent();
            if (_hit2.isPresent()) {
                Borrower _row = _hit2.get();
                _row.setBalance(_row.getBalance().add(amount));
                borrowerRepository.save(_row);
            }
            // cairkan_pinjaman.sql:21
            Optional<Loan> _hit3 = loanRepository.findByIdForUpdate(loanId);
            found = _hit3.isPresent();
            if (_hit3.isPresent()) {
                Loan _row = _hit3.get();
                _row.setStatus("DISBURSED");
                loanRepository.save(_row);
            }
        });
    }
}
