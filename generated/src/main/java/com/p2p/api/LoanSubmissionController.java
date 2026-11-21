// DIHASILKAN OTOMATIS oleh migrator dari mapping.yaml (ajukan_pinjaman) - jangan diedit manual.
package com.p2p.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.p2p.service.AjukanPinjamanService;
import com.p2p.snap.ErrorRule;
import com.p2p.snap.SnapErrors;
import com.p2p.snap.SnapFields;
import com.p2p.snap.SnapRequestException;
import com.p2p.snap.SnapResponse;
import com.p2p.support.ProcedureException;

/** Endpoint SNAP untuk procedure {@code ajukan_pinjaman} (kode layanan 90). */
@RestController
public class LoanSubmissionController {
    private static final String SERVICE_CODE = "90";
    private static final List<ErrorRule> ERRORS = List.of(
            new ErrorRule("Borrower tidak ditemukan", 404, "01", "Borrower Not Found"),
            new ErrorRule("Borrower belum verifikasi KYC", 403, "02", "KYC Not Verified"),
            new ErrorRule("Penghasilan borrower tidak memenuhi syarat", 403, "03", "Income Not Eligible"),
            new ErrorRule("Limit pinjaman borrower tidak mencukupi", 403, "04", "Exceeds Loan Limit"));

    private final AjukanPinjamanService service;

    public LoanSubmissionController(AjukanPinjamanService service) {
        this.service = service;
    }

    @PostMapping("/v1.0/loan-submission")
    public ResponseEntity<SnapResponse> handle(@RequestBody LoanSubmissionRequest _req) {
        String _ref = _req.partnerReferenceNo();
        try {
            SnapFields.require(_ref, "partnerReferenceNo");
            String loanId = SnapFields.require(_req.loanId(), "loanId");
            String borrowerId = SnapFields.require(_req.borrowerId(), "borrowerId");
            BigDecimal jumlah = SnapFields.amount(_req.jumlah(), "jumlah");
            Integer tenor = SnapFields.require(_req.tenor(), "tenor");
            this.service.execute(loanId, borrowerId, jumlah, tenor);
            return SnapErrors.success(SERVICE_CODE, _ref);
        } catch (SnapRequestException e) {
            return SnapErrors.fromRequest(e, SERVICE_CODE, _ref);
        } catch (ProcedureException e) {
            return SnapErrors.fromProcedure(e, SERVICE_CODE, ERRORS, _ref);
        }
    }
}
