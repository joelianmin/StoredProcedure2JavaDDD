// DIHASILKAN OTOMATIS oleh migrator (spike) - jangan diedit manual.
package com.p2p.domain;

import java.math.BigDecimal;

/** Entity dari tabel `lender`. */
public class Lender {
    private String id;
    private String name;
    private BigDecimal balance;

    public Lender(String id, String name, BigDecimal balance) {
        this.id = id;
        this.name = name;
        this.balance = balance;
    }

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal v) { this.balance = v; }

    public Lender copy() { return new Lender(id, name, balance); }

    @Override public String toString() { return "Lender[" + "id=" + id + ", " + "name=" + name + ", " + "balance=" + balance + "]"; }
}
