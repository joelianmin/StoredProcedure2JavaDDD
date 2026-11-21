package com.p2p.equivalence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Menulis satu baris JSON per (skenario, varian) ke target/equivalence-results.jsonl untuk laporan. */
public final class EquivalenceRecorder {
    private static final Path FILE = Path.of("target", "equivalence-results.jsonl");
    private static boolean started;

    private EquivalenceRecorder() {}

    public static synchronized void record(String scenario, String procedure, String variant, boolean equal,
                                           List<String> diffs) {
        try {
            Files.createDirectories(FILE.getParent());
            if (!started) {
                Files.deleteIfExists(FILE);
                started = true;
            }
            String json = "{\"scenario\":" + q(scenario) + ",\"procedure\":" + q(procedure) + ",\"variant\":"
                    + q(variant) + ",\"equal\":" + equal + ",\"diffs\":["
                    + String.join(",", diffs.stream().map(EquivalenceRecorder::q).toList()) + "]}\n";
            Files.writeString(FILE, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return sb.append('"').toString();
    }
}
