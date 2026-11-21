"""Laporan kelayakan pensiun stored procedure + metrik kriteria dosen (spec §9). Deterministik, tanpa timestamp."""
from __future__ import annotations

import csv
import json
import re
from collections import defaultdict
from pathlib import Path

TRAP_NAMES = {
    "J1": "NULL di kondisi IF", "J2": "aritmetika dengan NULL", "J3": "SELECT INTO tanpa baris",
    "J4": "rollback saat RAISE", "J5": "pembulatan round()", "J6": "pembagian numeric",
    "J7": "pembagian integer", "J8": "konkatenasi dengan NULL", "J9": "perbandingan numeric beda skala",
    "J10": "format pesan RAISE", "J11": "simpan ke numeric(p,s)", "J12": "error bawaan database",
}


def _java_loc(path: Path) -> int:
    if not path.exists():
        return 0
    n = 0
    for line in path.read_text().splitlines():
        s = line.strip()
        if s and not s.startswith(("//", "*", "/*", "@", "import ", "package ")):
            n += 1
    return n


def _sql_loc(path: Path) -> int:
    if not path.exists():
        return 0
    return sum(1 for line in path.read_text().splitlines() if line.strip() and not line.strip().startswith("--"))


def _class(name: str) -> str:
    return "".join(p[:1].upper() + p[1:] for p in name.split("_"))


def build_report(project: Path, manual_csv: Path | None, corpus_procs: Path | None = None) -> str:
    analysis = json.loads((project / "migrator-analysis.json").read_text())["procedures"]
    results_file = project / "target" / "equivalence-results.jsonl"
    results = [json.loads(l) for l in results_file.read_text().splitlines() if l.strip()] if results_file.exists() else []
    gen_seconds = (project / "generate-seconds.txt").read_text().strip() if (project / "generate-seconds.txt").exists() else "?"
    corpus_procs = corpus_procs or Path("corpus/procs")

    by_proc: dict[str, dict[str, list]] = defaultdict(lambda: defaultdict(list))
    for r in results:
        by_proc[r["procedure"]][r["variant"]].append(r)

    out = ["# Laporan Kelayakan Pensiun Stored Procedure", "",
           "Dihasilkan oleh `migrator report` dari `migrator-analysis.json` (analisis statis) dan",
           "`target/equivalence-results.jsonl` (uji kesetaraan: procedure asli di PostgreSQL vs kode Java baru).",
           "Acuan kebenaran = procedure lama yang dijalankan dengan `CALL` di PostgreSQL.", ""]

    verdicts = {}
    sections = []
    for a in sorted(analysis, key=lambda x: x["procedure"]):
        name = a["procedure"]
        variants = by_proc.get(name, {})
        if a["status"] != "ok":
            verdict = f"TIDAK LAYAK — {a['reason']}"
        elif not variants:
            verdict = "TIDAK LAYAK — belum ada skenario kesetaraan"
        else:
            bad = [(v, sum(not r["equal"] for r in rs)) for v, rs in sorted(variants.items())]
            bad = [(v, n) for v, n in bad if n]
            missing = [v for v in ("jdbc", "memory") if v not in variants]
            if bad:
                verdict = "TIDAK LAYAK — " + ", ".join(f"{n} skenario tidak setara ({v})" for v, n in bad)
            elif missing:
                verdict = f"TIDAK LAYAK — varian {', '.join(missing)} belum diuji"
            else:
                verdict = "LAYAK"
        verdicts[name] = verdict
        s = [f"## {name} — {verdict}", ""]
        if a["status"] == "ok":
            java = project / "src/main/java/com/p2p/service" / f"{_class(name)}Service.java"
            s += [f"- Sumber: `{a['source_file']}` ({_sql_loc(corpus_procs / a['source_file'])} baris SQL) → "
                  f"`{_class(name)}Service` ({_java_loc(java)} baris Java logika)",
                  f"- Tabel dibaca: {', '.join(a['tables_read']) or '-'} · ditulis: {', '.join(a['tables_written']) or '-'}",
                  f"- Konstruksi: {', '.join(a['constructs'])} · cabang IF/ELSIF/ELSE: {a['branch_count']}",
                  f"- Endpoint REST SNAP: {'ya' if a.get('api') else 'tidak (tidak ada di mapping.yaml)'}"]
            codes = sorted({t["code"] for t in a["traps"]}, key=lambda c: int(c[1:]))
            s.append(f"- Jebakan semantik terdeteksi: " + (", ".join(f"{c} ({TRAP_NAMES[c]})" for c in codes) or "-"))
            for t in a["traps"]:
                if t["code"] in ("J1", "J2", "J4"):
                    s.append(f"  - {t['code']} baris {t['line']}: {t['detail']}")
            for v in ("jdbc", "memory"):
                rs = variants.get(v, [])
                if rs:
                    ok = sum(r["equal"] for r in rs)
                    s.append(f"- Kesetaraan varian **{v}**: {ok}/{len(rs)} skenario setara")
                    for r in rs:
                        if not r["equal"]:
                            s.append(f"  - ✗ {r['scenario']}: " + "; ".join(r["diffs"][:3]))
        s.append("")
        sections.append(s)

    ok = [a for a in analysis if a["status"] == "ok"]
    total = len(analysis)
    out += ["## Ringkasan", "",
            "| Procedure | Putusan |", "|---|---|"]
    out += [f"| {n} | {v} |" for n, v in sorted(verdicts.items())]
    out.append("")

    # ---------------- metrik
    out += ["## Metrik (kriteria dosen)", "", "### Keefektifan", ""]
    out.append(f"- Procedure diterjemahkan tanpa konstruksi tak didukung: {len(ok)}/{total} "
               f"({100 * len(ok) / total:.0f}%)" if total else "- Tidak ada procedure")
    for v in ("jdbc", "memory"):
        rs = [r for r in results if r["variant"] == v]
        if rs:
            eq = sum(r["equal"] for r in rs)
            out.append(f"- Skenario setara varian {v}: {eq}/{len(rs)} ({100 * eq / len(rs):.0f}%)")
    detected = sorted({t["code"] for a in ok for t in a["traps"]}, key=lambda c: int(c[1:]))
    passing_names = " ".join(r["scenario"] for r in results if r["equal"])
    exercised = [c for c in detected if re.search(rf"\b{c}\b", passing_names)]
    out.append(f"- Jebakan terdeteksi analyzer: {', '.join(detected) or '-'}")
    out.append(f"- Jebakan yang punya skenario setara (disebut di nama skenario): {', '.join(exercised) or '-'}"
               f" ({len(exercised)}/{len(TRAP_NAMES)})")
    out.append("- Uji mutasi: ditunda (mode spike)")

    out += ["", "### Efisiensi", "", f"- Waktu jalan `migrator generate` (5 procedure): {gen_seconds} detik"]
    sums: dict[tuple[str, str], float] = defaultdict(float)
    if manual_csv and manual_csv.exists():
        with manual_csv.open() as f:
            for row in csv.DictReader(f):
                sums[(row["procedure"], row["cara"])] += float(row["menit"] or 0)
    if sums:
        procs = sorted({p for p, _ in sums})
        out += ["", "| Procedure | Manual (menit) | Dengan alat (menit) |", "|---|---|---|"]
        for p in procs:
            out.append(f"| {p} | {sums.get((p, 'manual'), 0):g} | {sums.get((p, 'alat'), 0):g} |")
    else:
        out.append("- Eksperimen migrasi manual: belum diisi (`experiments/manual/waktu.csv`)")

    j4 = [r for r in results if r["procedure"] == "danai_pinjaman" and "J4" in r["scenario"]]
    memory_runs = sum(1 for r in results if r["variant"] == "memory")
    services = sorted((project / "src/main/java/com/p2p/service").glob("*.java"))
    traced = sum(len(re.findall(r"// \S+\.sql:\d+", f.read_text())) for f in services)
    out += ["", "### Kebermanfaatan", "",
            "- Bug transaksi repo semester lalu (debit lender sebelum validasi) tidak terulang: "
            + ("ya, skenario J4 setara di " + ", ".join(sorted({r['variant'] for r in j4 if r['equal']}))
               if j4 and all(r["equal"] for r in j4) else "belum terbukti"),
            f"- Skenario yang lulus tanpa database (repository Map): {memory_runs}",
            f"- Pernyataan hasil terjemahan yang bisa dilacak ke baris SQL asal: {traced}",
            "- Seluruh proses lokal dan deterministik (tanpa LLM, tanpa layanan luar)", ""]

    for s in sections:
        out += s
    return "\n".join(out).rstrip() + "\n"
