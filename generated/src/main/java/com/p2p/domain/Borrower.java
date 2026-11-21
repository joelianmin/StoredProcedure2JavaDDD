// DIHASILKAN OTOMATIS oleh migrator dari tabel `borrower` - jangan diedit manual.
package com.p2p.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Entity dari tabel `borrower`. */
public class Borrower {
    private String id;   // PK
    private String name;
    private Boolean kycVerified;
    private BigDecimal income;   // numeric(15,2)
    private BigDecimal balance;   // numeric(15,2)
    private BigDecimal loanLimit;   // numeric(15,2)

    public Borrower(String id, String name, Boolean kycVerified, BigDecimal income, BigDecimal balance, BigDecimal loanLimit) {
        this.id = id;
        this.name = name;
        this.kycVerified = kycVerified;
        this.income = income;
        this.balance = balance;
        this.loanLimit = loanLimit;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getKycVerified() {
        return kycVerified;
    }

    public void setKycVerified(Boolean kycVerified) {
        this.kycVerified = kycVerified;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public void setIncome(BigDecimal income) {
        this.income = income;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getLoanLimit() {
        return loanLimit;
    }

    public void setLoanLimit(BigDecimal loanLimit) {
        this.loanLimit = loanLimit;
    }

    public Borrower copy() {
        return new Borrower(id, name, kycVerified, income, balance, loanLimit);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Borrower other)) return false;
        return Objects.equals(id, other.id)
            && Objects.equals(name, other.name)
            && Objects.equals(kycVerified, other.kycVerified)
            && Objects.equals(income, other.income)
            && Objects.equals(balance, other.balance)
            && Objects.equals(loanLimit, other.loanLimit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, kycVerified, income, balance, loanLimit);
    }

    @Override
    public String toString() {
        return "Borrower[" + "id=" + id + ", " + "name=" + name + ", " + "kyc_verified=" + kycVerified + ", " + "income=" + income + ", " + "balance=" + balance + ", " + "loan_limit=" + loanLimit + "]";
    }
}
