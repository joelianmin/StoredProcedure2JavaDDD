// DIHASILKAN OTOMATIS oleh migrator dari mapping.yaml (batalkan_pinjaman) - jangan diedit manual.
package com.p2p.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.p2p.service.BatalkanPinjamanService;
import com.p2p.snap.ErrorRule;
import com.p2p.snap.SnapErrors;
import com.p2p.snap.SnapFields;
import com.p2p.snap.SnapRequestException;
import com.p2p.snap.SnapResponse;
import com.p2p.support.ProcedureException;

/** Endpoint SNAP untuk procedure {@code batalkan_pinjaman} (kode layanan 94). */
@RestController
public class LoanCancellationController {
    private static final String SERVICE_CODE = "94";
    private static final List<ErrorRule> ERRORS = List.of(
            new ErrorRule("Loan tidak ditemukan", 404, "01", "Loan Not Found"),
            new ErrorRule("Pinjaman tidak dapat dibatalkan setelah dicairkan", 403, "02", "Invalid Loan Status"));

    private final BatalkanPinjamanService service;

    public LoanCancellationController(BatalkanPinjamanService service) {
        this.service = service;
    }

    @PostMapping("/v1.0/loan-cancellation")
    public ResponseEntity<SnapResponse> handle(@RequestBody LoanCancellationRequest _req) {
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
