// DIHASILKAN OTOMATIS oleh migrator dari ajukan_pinjaman.sql - jangan diedit manual.
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
 * Terjemahan stored procedure {@code ajukan_pinjaman(p_loan_id text, p_borrower_id text, p_jumlah numeric, p_tenor integer)}.
 * Satu pemanggilan {@link #execute} = satu transaksi (exception membatalkan semua perubahan).
 */
@Service
@SuppressWarnings({"unused", "UnusedAssignment"})
public class AjukanPinjamanService {
    private final BorrowerRepository borrowerRepository;
    private final LoanRepository loanRepository;
    private final UnitOfWork unitOfWork;

    public AjukanPinjamanService(BorrowerRepository borrowerRepository, LoanRepository loanRepository, UnitOfWork unitOfWork) {
        this.borrowerRepository = borrowerRepository;
        this.loanRepository = loanRepository;
        this.unitOfWork = unitOfWork;
    }

    public void execute(String loanId, String borrowerId, BigDecimal jumlah, Integer tenor) {
        unitOfWork.run(() -> {
            boolean found = false;
            Boolean kyc = null;
            BigDecimal income = null;
            BigDecimal limit = null;

            // ajukan_pinjaman.sql:9
            Optional<Borrower> _borrower1 = borrowerRepository.findById(borrowerId);
            found = _borrower1.isPresent();
            kyc = _borrower1.map(Borrower::getKycVerified).orElse(null);
            income = _borrower1.map(Borrower::getIncome).orElse(null);
            limit = _borrower1.map(Borrower::getLoanLimit).orElse(null);
            // ajukan_pinjaman.sql:12
            if (!found) {
                // ajukan_pinjaman.sql:13
                throw new ProcedureException("P0001", Sql.format("Borrower tidak ditemukan: %", borrowerId));
            }
            // ajukan_pinjaman.sql:16
            if (!kyc) {
                // ajukan_pinjaman.sql:17
                throw new ProcedureException("P0001", "Borrower belum verifikasi KYC");
            }
            // ajukan_pinjaman.sql:21
            if (Sql.isTrue(Sql.le(income, Sql.num(Integer.valueOf(3000000))))) {
                // ajukan_pinjaman.sql:22
                throw new ProcedureException("P0001", "Penghasilan borrower tidak memenuhi syarat");
            }
            // ajukan_pinjaman.sql:25
            if (Sql.isTrue(Sql.gt(jumlah, limit))) {
                // ajukan_pinjaman.sql:26
                throw new ProcedureException("P0001", "Limit pinjaman borrower tidak mencukupi");
            }
            // ajukan_pinjaman.sql:29
            loanRepository.insert(new Loan(loanId, borrowerId, jumlah, Sql.num(Integer.valueOf(0)), tenor, new BigDecimal("0.1000"), "PENDING"));
            found = true;
            // ajukan_pinjaman.sql:32
            Optional<Borrower> _hit2 = borrowerRepository.findByIdForUpdate(borrowerId);
            found = _hit2.isPresent();
            if (_hit2.isPresent()) {
                Borrower _row = _hit2.get();
                _row.setLoanLimit(Sql.sub(_row.getLoanLimit(), jumlah));
                borrowerRepository.save(_row);
            }
        });
    }
}
