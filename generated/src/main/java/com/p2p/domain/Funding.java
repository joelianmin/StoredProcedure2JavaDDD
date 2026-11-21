// DIHASILKAN OTOMATIS oleh migrator dari tabel `funding` - jangan diedit manual.
package com.p2p.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Entity dari tabel `funding`. */
public class Funding {
    private String id;   // PK
    private String loanId;
    private String lenderId;
    private BigDecimal amount;   // numeric(15,2)

    public Funding(String id, String loanId, String lenderId, BigDecimal amount) {
        this.id = id;
        this.loanId = loanId;
        this.lenderId = lenderId;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public String getLenderId() {
        return lenderId;
    }

    public void setLenderId(String lenderId) {
        this.lenderId = lenderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Funding copy() {
        return new Funding(id, loanId, lenderId, amount);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Funding other)) return false;
        return Objects.equals(id, other.id)
            && Objects.equals(loanId, other.loanId)
            && Objects.equals(lenderId, other.lenderId)
            && Objects.equals(amount, other.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, loanId, lenderId, amount);
    }

    @Override
    public String toString() {
        return "Funding[" + "id=" + id + ", " + "loan_id=" + loanId + ", " + "lender_id=" + lenderId + ", " + "amount=" + amount + "]";
    }
}
