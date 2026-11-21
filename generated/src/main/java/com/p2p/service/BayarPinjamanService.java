// DIHASILKAN OTOMATIS oleh migrator dari bayar_pinjaman.sql - jangan diedit manual.
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
 * Terjemahan stored procedure {@code bayar_pinjaman(p_loan_id text)}.
 * Satu pemanggilan {@link #execute} = satu transaksi (exception membatalkan semua perubahan).
 */
@Service
@SuppressWarnings({"unused", "UnusedAssignment"})
public class BayarPinjamanService {
    private final BorrowerRepository borrowerRepository;
    private final LoanRepository loanRepository;
    private final UnitOfWork unitOfWork;

    public BayarPinjamanService(BorrowerRepository borrowerRepository, LoanRepository loanRepository, UnitOfWork unitOfWork) {
        this.borrowerRepository = borrowerRepository;
        this.loanRepository = loanRepository;
        this.unitOfWork = unitOfWork;
    }

    public void execute(String loanId) {
        unitOfWork.run(() -> {
            boolean found = false;
            BigDecimal amount = null;
            Integer tenor = null;
            BigDecimal rate = null;
            String status = null;
            String borrowerId = null;
            BigDecimal bunga = null;
            BigDecimal total = null;
            BigDecimal saldo = null;

            // bayar_pinjaman.sql:14
            Optional<Loan> _loan1 = loanRepository.findById(loanId);
            found = _loan1.isPresent();
            amount = _loan1.map(Loan::getAmount).orElse(null);
            tenor = _loan1.map(Loan::getTenorMonths).orElse(null);
            rate = _loan1.map(Loan::getInterestRate).orElse(null);
            status = _loan1.map(Loan::getStatus).orElse(null);
            borrowerId = _loan1.map(Loan::getBorrowerId).orElse(null);
            // bayar_pinjaman.sql:17
            if (!found) {
                // bayar_pinjaman.sql:18
                throw new ProcedureException("P0001", Sql.format("Loan tidak ditemukan: %", loanId));
            }
            // bayar_pinjaman.sql:21
            if (!status.equals("DISBURSED")) {
                // bayar_pinjaman.sql:22
                throw new ProcedureException("P0001", "Loan belum dicairkan");
            }
            // bayar_pinjaman.sql:25
            bunga = Sql.round(Sql.div(amount.multiply(rate).multiply(Sql.num(tenor)), Sql.num(Integer.valueOf(12))), Integer.valueOf(2));
            // bayar_pinjaman.sql:26
            total = amount.add(bunga);
            // bayar_pinjaman.sql:28
            Optional<Borrower> _borrower2 = borrowerRepository.findById(borrowerId);
            found = _borrower2.isPresent();
            saldo = _borrower2.map(Borrower::getBalance).orElse(null);
            // bayar_pinjaman.sql:29
            if (!found) {
                // bayar_pinjaman.sql:30
                throw new ProcedureException("P0001", Sql.format("Borrower tidak ditemukan: %", borrowerId));
            }
            // bayar_pinjaman.sql:33
            if (saldo.compareTo(total) < 0) {
                // bayar_pinjaman.sql:34
                throw new ProcedureException("P0001", "Saldo borrower tidak mencukupi");
            }
            // bayar_pinjaman.sql:37
            Optional<Borrower> _hit3 = borrowerRepository.findByIdForUpdate(borrowerId);
            found = _hit3.isPresent();
            if (_hit3.isPresent()) {
                Borrower _row = _hit3.get();
                _row.setBalance(_row.getBalance().subtract(total));
                borrowerRepository.save(_row);
            }
            // bayar_pinjaman.sql:38
            Optional<Loan> _hit4 = loanRepository.findByIdForUpdate(loanId);
            found = _hit4.isPresent();
            if (_hit4.isPresent()) {
                Loan _row = _hit4.get();
                _row.setStatus("REPAID");
                loanRepository.save(_row);
            }
        });
    }
}
