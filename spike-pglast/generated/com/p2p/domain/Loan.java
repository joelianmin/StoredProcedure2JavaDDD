// DIHASILKAN OTOMATIS oleh migrator (spike) - jangan diedit manual.
package com.p2p.domain;

import java.math.BigDecimal;

/** Entity dari tabel `loan`. */
public class Loan {
    private String id;
    private String borrowerId;
    private BigDecimal amount;
    private BigDecimal collectedAmount;
    private String status;

    public Loan(String id, String borrowerId, BigDecimal amount, BigDecimal collectedAmount, String status) {
        this.id = id;
        this.borrowerId = borrowerId;
        this.amount = amount;
        this.collectedAmount = collectedAmount;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getBorrowerId() { return borrowerId; }
    public void setBorrowerId(String v) { this.borrowerId = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public BigDecimal getCollectedAmount() { return collectedAmount; }
    public void setCollectedAmount(BigDecimal v) { this.collectedAmount = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public Loan copy() { return new Loan(id, borrowerId, amount, collectedAmount, status); }

    @Override public String toString() { return "Loan[" + "id=" + id + ", " + "borrower_id=" + borrowerId + ", " + "amount=" + amount + ", " + "collected_amount=" + collectedAmount + ", " + "status=" + status + "]"; }
}
