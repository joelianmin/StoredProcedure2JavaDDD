// DIHASILKAN OTOMATIS oleh migrator dari mapping.yaml (bayar_pinjaman) - jangan diedit manual.
package com.p2p.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.p2p.service.BayarPinjamanService;
import com.p2p.snap.ErrorRule;
import com.p2p.snap.SnapErrors;
import com.p2p.snap.SnapFields;
import com.p2p.snap.SnapRequestException;
import com.p2p.snap.SnapResponse;
import com.p2p.support.ProcedureException;

/** Endpoint SNAP untuk procedure {@code bayar_pinjaman} (kode layanan 93). */
@RestController
public class LoanRepaymentController {
    private static final String SERVICE_CODE = "93";
    private static final List<ErrorRule> ERRORS = List.of(
            new ErrorRule("Loan tidak ditemukan", 404, "01", "Loan Not Found"),
            new ErrorRule("Borrower tidak ditemukan", 404, "03", "Borrower Not Found"),
            new ErrorRule("Loan belum dicairkan", 403, "02", "Invalid Loan Status"),
            new ErrorRule("Saldo borrower tidak mencukupi", 403, "14", "Insufficient Funds"));

    private final BayarPinjamanService service;

    public LoanRepaymentController(BayarPinjamanService service) {
        this.service = service;
    }

    @PostMapping("/v1.0/loan-repayment")
    public ResponseEntity<SnapResponse> handle(@RequestBody LoanRepaymentRequest _req) {
        String _ref = _req.partnerReferenceNo();
        try {
            SnapFields.require(_ref, "partnerReferenceNo");
            String loanId = SnapFields.require(_req.loanId(), "loanId");
            this.service.execute(loanId);
            return SnapErrors.success(SERVICE_CODE, _ref);
        } catch (SnapRequestException e) {
            return SnapErrors.fromRequest(e, SERVICE_CODE, _ref);
        } catch (ProcedureException e) {
            return SnapErrors.fromProcedure(e, SERVICE_CODE, ERRORS, _ref);
        }
    }
}
