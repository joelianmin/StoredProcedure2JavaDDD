// DIHASILKAN OTOMATIS oleh migrator - jangan diedit manual.
package com.p2p.equivalence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import com.p2p.infrastructure.InMemoryBorrowerRepository;
import com.p2p.infrastructure.JdbcBorrowerRepository;
import com.p2p.repository.BorrowerRepository;
import com.p2p.infrastructure.InMemoryLenderRepository;
import com.p2p.infrastructure.JdbcLenderRepository;
import com.p2p.repository.LenderRepository;
import com.p2p.infrastructure.InMemoryLoanRepository;
import com.p2p.infrastructure.JdbcLoanRepository;
import com.p2p.repository.LoanRepository;
import com.p2p.infrastructure.InMemoryFundingRepository;
import com.p2p.infrastructure.JdbcFundingRepository;
import com.p2p.repository.FundingRepository;
import com.p2p.service.AjukanPinjamanService;
import com.p2p.service.BatalkanPinjamanService;
import com.p2p.service.BayarPinjamanService;
import com.p2p.service.CairkanPinjamanService;
import com.p2p.service.DanaiPinjamanService;
import com.p2p.service.UjiAndPendekService;
import com.p2p.service.UjiFoundForService;
import com.p2p.service.UjiNullCabangService;
import com.p2p.support.SnapshotUnitOfWork;
import com.p2p.support.SpringTxUnitOfWork;
import com.p2p.support.UnitOfWork;

/** Tiga backend uji kesetaraan: legacy (CALL), jdbc (kode baru + PostgreSQL), memory (kode baru + Map). */
public class GeneratedBackends implements BackendFactory {
    private static final GeneratedSchema SCHEMA = new GeneratedSchema();

    record Repos(BorrowerRepository borrower, LenderRepository lender, LoanRepository loan, FundingRepository funding) {}

    @Override
    public SchemaInfo schema() {
        return SCHEMA;
    }

    @Override
    public List<Backend> create() {
        return List.of(new LegacyBackend(SCHEMA, TestDatabases.legacy()), jdbc(), memory());
    }

    private Backend jdbc() {
        DataSource ds = TestDatabases.baru();
        JdbcTemplate jt = new JdbcTemplate(ds);
        Repos r = new Repos(new JdbcBorrowerRepository(jt), new JdbcLenderRepository(jt), new JdbcLoanRepository(jt), new JdbcFundingRepository(jt));
        UnitOfWork uow = new SpringTxUnitOfWork(new DataSourceTransactionManager(ds));
        LegacyBackend tables = new LegacyBackend(SCHEMA, ds);   // hanya dipakai untuk reset/insert/dump p2p_baru
        return new LambdaBackend("jdbc", tables::reset, (t, row) -> tables.insert(t, List.of(row)), tables::dump,
                (p, a) -> invoke(p, a, r, uow));
    }

    private Backend memory() {
        InMemoryBorrowerRepository borrower = new InMemoryBorrowerRepository();
        InMemoryLenderRepository lender = new InMemoryLenderRepository();
        InMemoryLoanRepository loan = new InMemoryLoanRepository();
        InMemoryFundingRepository funding = new InMemoryFundingRepository();
        Repos r = new Repos(borrower, lender, loan, funding);
        UnitOfWork uow = new SnapshotUnitOfWork(List.of(borrower, lender, loan, funding));
        return new LambdaBackend("memory", () -> {
            borrower.clear();
            lender.clear();
            loan.clear();
            funding.clear();
        }, (t, row) -> save(r, t, row), () -> dump(r), (p, a) -> invoke(p, a, r, uow));
    }

    static void save(Repos r, String table, Map<String, String> row) {
        switch (table) {
            case "borrower" -> r.borrower().insert(RowConverters.borrower(row));
            case "lender" -> r.lender().insert(RowConverters.lender(row));
            case "loan" -> r.loan().insert(RowConverters.loan(row));
            case "funding" -> r.funding().insert(RowConverters.funding(row));
            default -> throw new IllegalArgumentException("tabel tidak dikenal: " + table);
        }
    }

    static Map<String, List<Map<String, Object>>> dump(Repos r) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        out.put("borrower", r.borrower().findAll().stream().map(RowConverters::row).toList());
        out.put("lender", r.lender().findAll().stream().map(RowConverters::row).toList());
        out.put("loan", r.loan().findAll().stream().map(RowConverters::row).toList());
        out.put("funding", r.funding().findAll().stream().map(RowConverters::row).toList());
        return out;
    }

    static void invoke(String procedure, List<String> a, Repos r, UnitOfWork uow) {
        switch (procedure) {
            case "ajukan_pinjaman" -> new AjukanPinjamanService(r.borrower(), r.loan(), uow)
                    .execute(RowConverters.text(a.get(0)), RowConverters.text(a.get(1)), RowConverters.numeric(a.get(2)), RowConverters.integer(a.get(3)));
            case "batalkan_pinjaman" -> new BatalkanPinjamanService(r.borrower(), r.funding(), r.lender(), r.loan(), uow)
                    .execute(RowConverters.text(a.get(0)));
            case "bayar_pinjaman" -> new BayarPinjamanService(r.borrower(), r.loan(), uow)
                    .execute(RowConverters.text(a.get(0)));
            case "cairkan_pinjaman" -> new CairkanPinjamanService(r.borrower(), r.loan(), uow)
                    .execute(RowConverters.text(a.get(0)));
            case "danai_pinjaman" -> new DanaiPinjamanService(r.funding(), r.lender(), r.loan(), uow)
                    .execute(RowConverters.text(a.get(0)), RowConverters.text(a.get(1)), RowConverters.text(a.get(2)), RowConverters.numeric(a.get(3)));
            case "uji_and_pendek" -> new UjiAndPendekService(uow)
                    .execute(RowConverters.numeric(a.get(0)), RowConverters.integer(a.get(1)));
            case "uji_found_for" -> new UjiFoundForService(r.funding(), r.lender(), uow)
                    .execute(RowConverters.text(a.get(0)));
            case "uji_null_cabang" -> new UjiNullCabangService(uow)
                    .execute(RowConverters.bool(a.get(0)));
            default -> throw new IllegalArgumentException("procedure tidak dikenal: " + procedure);
        }
    }
}
