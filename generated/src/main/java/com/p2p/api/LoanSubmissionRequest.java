// DIHASILKAN OTOMATIS oleh migrator dari mapping.yaml (ajukan_pinjaman) - jangan diedit manual.
package com.p2p.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.p2p.snap.SnapAmount;

/** Body request POST /v1.0/loan-submission. */
public record LoanSubmissionRequest(
        String partnerReferenceNo,
        String loanId,
        String borrowerId,
        SnapAmount jumlah,
        Integer tenor) {
}
