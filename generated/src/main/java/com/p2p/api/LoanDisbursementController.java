// DIHASILKAN OTOMATIS oleh migrator dari mapping.yaml (cairkan_pinjaman) - jangan diedit manual.
package com.p2p.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.p2p.service.CairkanPinjamanService;
import com.p2p.snap.ErrorRule;
import com.p2p.snap.SnapErrors;
import com.p2p.snap.SnapFields;
import com.p2p.snap.SnapRequestException;
import com.p2p.snap.SnapResponse;
import com.p2p.support.ProcedureException;

/** Endpoint SNAP untuk procedure {@code cairkan_pinjaman} (kode layanan 92). */
@RestController
public class LoanDisbursementController {
    private static final String SERVICE_CODE = "92";
    private static final List<ErrorRule> ERRORS = List.of(
            new ErrorRule("Loan tidak ditemukan", 404, "01", "Loan Not Found"),
            new ErrorRule("Hanya pinjaman berstatus FUNDED yang bisa dicairkan", 403, "02", "Invalid Loan Status"));

    private final CairkanPinjamanService service;

    public LoanDisbursementController(CairkanPinjamanService service) {
        this.service = service;
    }

    @PostMapping("/v1.0/loan-disbursement")
    public ResponseEntity<SnapResponse> handle(@RequestBody LoanDisbursementRequest _req) {
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
