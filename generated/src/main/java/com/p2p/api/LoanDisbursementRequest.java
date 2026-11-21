// DIHASILKAN OTOMATIS oleh migrator dari mapping.yaml (cairkan_pinjaman) - jangan diedit manual.
package com.p2p.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.p2p.snap.SnapAmount;

/** Body request POST /v1.0/loan-disbursement. */
public record LoanDisbursementRequest(
        String partnerReferenceNo,
        String loanId) {
}
