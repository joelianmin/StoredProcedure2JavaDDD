// DIHASILKAN OTOMATIS oleh migrator dari mapping.yaml (danai_pinjaman) - jangan diedit manual.
package com.p2p.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.p2p.service.DanaiPinjamanService;
import com.p2p.snap.ErrorRule;
import com.p2p.snap.SnapErrors;
import com.p2p.snap.SnapFields;
import com.p2p.snap.SnapRequestException;
import com.p2p.snap.SnapResponse;
import com.p2p.support.ProcedureException;

/** Endpoint SNAP untuk procedure {@code danai_pinjaman} (kode layanan 91). */
@RestController
public class LoanFundingController {
    private static final String SERVICE_CODE = "91";
    private static final List<ErrorRule> ERRORS = List.of(
            new ErrorRule("Loan tidak ditemukan", 404, "01", "Loan Not Found"),
            new ErrorRule("Lender tidak ditemukan", 404, "02", "Lender Not Found"),
            new ErrorRule("Saldo lender tidak mencukupi", 403, "14", "Insufficient Funds"),
            new ErrorRule("Pendanaan melebihi sisa target pinjaman", 403, "01", "Funding Exceeds Remaining Target"),
            new ErrorRule("Loan ", 403, "02", "Invalid Loan Status"));

    private final DanaiPinjamanService service;

    public LoanFundingController(DanaiPinjamanService service) {
        this.service = service;
    }

    @PostMapping("/v1.0/loan-funding")
    public ResponseEntity<SnapResponse> handle(@RequestBody LoanFundingRequest _req) {
        String _ref = _req.partnerReferenceNo();
        try {
            SnapFields.require(_ref, "partnerReferenceNo");
            String fundingId = SnapFields.require(_req.fundingId(), "fundingId");
            String loanId = SnapFields.require(_req.loanId(), "loanId");
            String lenderId = SnapFields.require(_req.lenderId(), "lenderId");
            BigDecimal jumlah = SnapFields.amount(_req.jumlah(), "jumlah");
            this.service.execute(fundingId, loanId, lenderId, jumlah);
            return SnapErrors.success(SERVICE_CODE, _ref);
        } catch (SnapRequestException e) {
            return SnapErrors.fromRequest(e, SERVICE_CODE, _ref);
        } catch (ProcedureException e) {
            return SnapErrors.fromProcedure(e, SERVICE_CODE, ERRORS, _ref);
        }
    }
}
