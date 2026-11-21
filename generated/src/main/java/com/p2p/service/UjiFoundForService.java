// DIHASILKAN OTOMATIS oleh migrator dari uji_found_for.sql - jangan diedit manual.
package com.p2p.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.p2p.domain.Funding;
import com.p2p.repository.FundingRepository;
import com.p2p.domain.Lender;
import com.p2p.repository.LenderRepository;
import com.p2p.support.ProcedureException;
import com.p2p.support.Sql;
import com.p2p.support.UnitOfWork;

/**
 * Terjemahan stored procedure {@code uji_found_for(p_loan_id text)}.
 * Satu pemanggilan {@link #execute} = satu transaksi (exception membatalkan semua perubahan).
 */
@Service
@SuppressWarnings({"unused", "UnusedAssignment"})
public class UjiFoundForService {
    private final FundingRepository fundingRepository;
    private final LenderRepository lenderRepository;
    private final UnitOfWork unitOfWork;

    public UjiFoundForService(FundingRepository fundingRepository, LenderRepository lenderRepository, UnitOfWork unitOfWork) {
        this.fundingRepository = fundingRepository;
        this.lenderRepository = lenderRepository;
        this.unitOfWork = unitOfWork;
    }

    public void execute(String loanId) {
        unitOfWork.run(() -> {
            boolean found = false;

            // uji_found_for.sql:7
            boolean _iterated1 = false;
            for (Funding r : fundingRepository.findByLoanId(loanId)) {
                _iterated1 = true;
                // uji_found_for.sql:8
                Optional<Lender> _hit2 = lenderRepository.findByIdForUpdate(r.getLenderId());
                found = _hit2.isPresent();
                if (_hit2.isPresent()) {
                    Lender _row = _hit2.get();
                    _row.setBalance(_row.getBalance().add(r.getAmount()));
                    lenderRepository.save(_row);
                }
            }
            found = _iterated1;
            // uji_found_for.sql:10
            if (!found) {
                // uji_found_for.sql:11
                throw new ProcedureException("P0001", "tidak ada funding");
            }
        });
    }
}
