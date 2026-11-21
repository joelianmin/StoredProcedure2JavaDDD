package com.p2p.equivalence;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.p2p.support.ProcedureException;

/** Backend untuk kode hasil generate (jdbc / memory); perilakunya dirakit oleh GeneratedBackends. */
public class LambdaBackend implements Backend {
    private final String name;
    private final Runnable reset;
    private final BiConsumer<String, Map<String, String>> insertRow;
    private final Supplier<Map<String, List<Map<String, Object>>>> dump;
    private final BiConsumer<String, List<String>> invoke;

    public LambdaBackend(String name, Runnable reset, BiConsumer<String, Map<String, String>> insertRow,
                         Supplier<Map<String, List<Map<String, Object>>>> dump, BiConsumer<String, List<String>> invoke) {
        this.name = name;
        this.reset = reset;
        this.insertRow = insertRow;
        this.dump = dump;
        this.invoke = invoke;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void reset() {
        reset.run();
    }

    @Override
    public void insert(String table, List<Map<String, String>> rows) {
        rows.forEach(r -> insertRow.accept(table, r));
    }

    @Override
    public Outcome call(String procedure, List<String> args) {
        try {
            invoke.accept(procedure, args);
            return Outcome.ok();
        } catch (ProcedureException e) {
            return Outcome.failed(e);
        } catch (RuntimeException e) {                 // bug di kode baru: dilaporkan sebagai perbedaan
            return new Outcome(false, "JAVA", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public Map<String, List<Map<String, Object>>> dump() {
        return dump.get();
    }
}
