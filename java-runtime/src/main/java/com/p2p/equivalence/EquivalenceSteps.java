package com.p2p.equivalence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Step Gherkin uji kesetaraan. Setiap langkah dijalankan di semua backend; legacy = acuan kebenaran. */
public class EquivalenceSteps {
    private static final String NULL = "<NULL>";
    private static BackendFactory factory;
    private static List<Backend> backends;

    private String scenario;
    private String procedure;
    private final Map<String, Outcome> outcomes = new HashMap<>();

    @Before
    public void before(Scenario sc) {
        if (backends == null) {
            factory = ServiceLoader.load(BackendFactory.class).findFirst()
                    .orElseThrow(() -> new IllegalStateException("GeneratedBackends tidak ditemukan"));
            backends = factory.create();
        }
        backends.forEach(Backend::reset);
        scenario = sc.getName();
    }

    private static String value(String v) {
        return v == null || NULL.equals(v) ? null : v;
    }

    @Given("tabel {string} berisi:")
    public void tabelBerisi(String table, DataTable dt) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, String> r : dt.asMaps()) {
            Map<String, String> row = new LinkedHashMap<>();
            r.forEach((k, v) -> row.put(k, value(v)));
            rows.add(row);
        }
        backends.forEach(b -> b.insert(table, rows));
    }

    @When("procedure {string} dipanggil dengan:")
    public void procedureDipanggil(String name, DataTable dt) {
        List<List<String>> cells = dt.cells();
        List<String> header = cells.get(0);
        List<String> expected = factory.schema().procedure(name).paramNames();
        if (!header.equals(expected)) {
            throw new AssertionError("parameter harus persis " + expected + " (urutan signature), bukan " + header);
        }
        List<String> args = cells.get(1).stream().map(EquivalenceSteps::value).toList();
        procedure = name;
        for (Backend b : backends) outcomes.put(b.name(), b.call(name, args));
    }

    @Then("procedure lama berhasil")
    public void lamaBerhasil() {
        Outcome o = outcomes.get("legacy");
        if (!o.success()) throw new AssertionError("procedure lama diharapkan berhasil, ternyata " + o);
    }

    @Then("procedure lama gagal dengan {string} {string}")
    public void lamaGagal(String sqlState, String message) {
        Outcome o = outcomes.get("legacy");
        if (o.success() || !sqlState.equals(o.sqlState()) || !message.equals(o.message())) {
            throw new AssertionError("procedure lama diharapkan GAGAL " + sqlState + " \"" + message + "\", ternyata " + o);
        }
    }

    @Then("hasil lama dan baru setara")
    public void setara() {
        Backend legacy = backends.get(0);
        Outcome lamaOutcome = outcomes.get(legacy.name());
        var lamaDump = legacy.dump();
        List<String> all = new ArrayList<>();
        for (Backend b : backends.subList(1, backends.size())) {
            List<String> diffs = new ArrayList<>();
            Outcome o = outcomes.get(b.name());
            if (!o.equivalentTo(lamaOutcome)) diffs.add("hasil: lama=" + lamaOutcome + " baru=" + o);
            diffs.addAll(TableDiff.compare(factory.schema(), lamaDump, b.dump()));
            EquivalenceRecorder.record(scenario, procedure, b.name(), diffs.isEmpty(), diffs);
            diffs.forEach(d -> all.add("[" + b.name() + "] " + d));
        }
        if (!all.isEmpty()) throw new AssertionError("TIDAK SETARA:\n  " + String.join("\n  ", all));
    }
}
