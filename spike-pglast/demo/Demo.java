import java.math.BigDecimal;

import com.p2p.domain.*;
import com.p2p.infrastructure.*;
import com.p2p.service.DanaiPinjamanService;
import com.p2p.support.*;

/** Ditulis tangan: menjalankan skenario loan_funding.feature terhadap kode HASIL GENERATE. */
public class Demo {
    static InMemoryLoanRepository loans;
    static InMemoryLenderRepository lenders;
    static DanaiPinjamanService service;
    static int lulus = 0, total = 0;

    static void setup(String loanId, String target, String terkumpul, String lenderId, String saldo) {
        loans = new InMemoryLoanRepository();
        lenders = new InMemoryLenderRepository();
        loans.save(new Loan(loanId, "B001", new BigDecimal(target), new BigDecimal(terkumpul), "PENDING"));
        lenders.save(new Lender(lenderId, "Lender " + lenderId, new BigDecimal(saldo)));
        service = new DanaiPinjamanService(lenders, loans, new UnitOfWork(lenders, loans));
    }

    static void cek(String apa, Object harapan, Object aktual) {
        total++;
        boolean ok = harapan instanceof BigDecimal h ? h.compareTo((BigDecimal) aktual) == 0 : harapan.equals(aktual);
        if (ok) lulus++;
        System.out.printf("    %s %-32s harapan=%-26s aktual=%s%n", ok ? "LULUS" : "GAGAL", apa, harapan, aktual);
    }

    static String jalankan(String loanId, String lenderId, String jumlah) {
        try { service.execute(loanId, lenderId, new BigDecimal(jumlah)); return "OK"; }
        catch (ProcedureException e) { return e.getSqlState() + ": " + e.getMessage(); }
    }

    public static void main(String[] a) {
        System.out.println("Skenario 1: Lender mendanai sebagian pinjaman");
        setup("L001", "10000000", "0", "LD001", "20000000");
        cek("hasil", "OK", jalankan("L001", "LD001", "4000000"));
        cek("collected_amount", new BigDecimal("4000000"), loans.findById("L001").get().getCollectedAmount());
        cek("status", "PENDING", loans.findById("L001").get().getStatus());
        cek("saldo lender", new BigDecimal("16000000"), lenders.findById("LD001").get().getBalance());

        System.out.println("Skenario 2: Pinjaman fully funded");
        setup("L002", "10000000", "8000000", "LD002", "5000000");
        cek("hasil", "OK", jalankan("L002", "LD002", "2000000"));
        cek("collected_amount", new BigDecimal("10000000"), loans.findById("L002").get().getCollectedAmount());
        cek("status", "FUNDED", loans.findById("L002").get().getStatus());

        System.out.println("Skenario 3: Lender mendanai melebihi sisa target  (bug transaksi repo lama)");
        setup("L004", "10000000", "8000000", "LD003", "6000000");
        cek("hasil", "P0001: Pendanaan melebihi sisa target pinjaman", jalankan("L004", "LD003", "5000000"));
        cek("collected_amount tetap", new BigDecimal("8000000"), loans.findById("L004").get().getCollectedAmount());
        cek("saldo lender TIDAK terpotong", new BigDecimal("6000000"), lenders.findById("LD003").get().getBalance());

        System.out.println("Skenario 4: Loan tidak ditemukan");
        setup("L001", "10000000", "0", "LD001", "20000000");
        cek("hasil", "P0001: Loan tidak ditemukan: L999", jalankan("L999", "LD001", "1000000"));

        System.out.printf("%nTotal: %d/%d lulus%n", lulus, total);
    }
}
