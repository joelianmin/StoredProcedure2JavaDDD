// DIHASILKAN OTOMATIS oleh migrator dari tabel `lender` - jangan diedit manual.
package com.p2p.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Entity dari tabel `lender`. */
public class Lender {
    private String id;   // PK
    private String name;
    private BigDecimal balance;   // numeric(15,2)

    public Lender(String id, String name, BigDecimal balance) {
        this.id = id;
        this.name = name;
        this.balance = balance;
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

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Lender copy() {
        return new Lender(id, name, balance);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Lender other)) return false;
        return Objects.equals(id, other.id)
            && Objects.equals(name, other.name)
            && Objects.equals(balance, other.balance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, balance);
    }

    @Override
    public String toString() {
        return "Lender[" + "id=" + id + ", " + "name=" + name + ", " + "balance=" + balance + "]";
    }
}
