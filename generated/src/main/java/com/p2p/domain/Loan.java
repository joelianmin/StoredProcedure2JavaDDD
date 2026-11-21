// DIHASILKAN OTOMATIS oleh migrator dari tabel `loan` - jangan diedit manual.
package com.p2p.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Entity dari tabel `loan`. */
public class Loan {
    private String id;   // PK
    private String borrowerId;
    private BigDecimal amount;   // numeric(15,2)
    private BigDecimal collectedAmount;   // numeric(15,2)
    private Integer tenorMonths;
    private BigDecimal interestRate;   // numeric(5,4)
    private String status;

    public Loan(String id, String borrowerId, BigDecimal amount, BigDecimal collectedAmount, Integer tenorMonths, BigDecimal interestRate, String status) {
        this.id = id;
        this.borrowerId = borrowerId;
        this.amount = amount;
        this.collectedAmount = collectedAmount;
        this.tenorMonths = tenorMonths;
        this.interestRate = interestRate;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBorrowerId() {
        return borrowerId;
    }

    public void setBorrowerId(String borrowerId) {
        this.borrowerId = borrowerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public void setCollectedAmount(BigDecimal collectedAmount) {
        this.collectedAmount = collectedAmount;
    }

    public Integer getTenorMonths() {
        return tenorMonths;
    }

    public void setTenorMonths(Integer tenorMonths) {
        this.tenorMonths = tenorMonths;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Loan copy() {
        return new Loan(id, borrowerId, amount, collectedAmount, tenorMonths, interestRate, status);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Loan other)) return false;
        return Objects.equals(id, other.id)
            && Objects.equals(borrowerId, other.borrowerId)
            && Objects.equals(amount, other.amount)
            && Objects.equals(collectedAmount, other.collectedAmount)
            && Objects.equals(tenorMonths, other.tenorMonths)
            && Objects.equals(interestRate, other.interestRate)
            && Objects.equals(status, other.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, borrowerId, amount, collectedAmount, tenorMonths, interestRate, status);
    }

    @Override
    public String toString() {
        return "Loan[" + "id=" + id + ", " + "borrower_id=" + borrowerId + ", " + "amount=" + amount + ", " + "collected_amount=" + collectedAmount + ", " + "tenor_months=" + tenorMonths + ", " + "interest_rate=" + interestRate + ", " + "status=" + status + "]";
    }
}
