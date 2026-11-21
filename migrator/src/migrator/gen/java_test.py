"""File sisi test proyek hasil generate: skema, konverter, backend kesetaraan, runner Cucumber, feature."""
from __future__ import annotations

from pathlib import Path

from ..naming import java_class
from .java import TableModel

TEST = Path("src/test/java/com/p2p/equivalence")
CONV = {"text": "text", "numeric": "numeric", "integer": "integer", "boolean": "bool", "date": "date"}


def test_files(tables, procs, models: list[TableModel], features: Path | None, env) -> dict[Path, str]:
    by_name = {m.name: m for m in models}
    proc_views = []
    for p in procs:
        touched = sorted({s.table for s in _walk(p.body) if hasattr(s, "table")})
        proc_views.append({"name": p.name, "cls": java_class(p.name) + "Service", "params": p.params,
                           "repos": [by_name[t].var for t in touched]})
    ctx = {"models": models, "procs": proc_views, "conv": CONV}
    files = {
        TEST / "GeneratedSchema.java": env.get_template("GeneratedSchema.java.j2").render(**ctx),
        TEST / "RowConverters.java": env.get_template("RowConverters.java.j2").render(**ctx),
        TEST / "GeneratedBackends.java": env.get_template("GeneratedBackends.java.j2").render(**ctx),
        Path("src/test/resources/META-INF/services/com.p2p.equivalence.BackendFactory"):
            "com.p2p.equivalence.GeneratedBackends\n",
    }
    feats = sorted(features.glob("*.feature")) if features and features.exists() else []
    if feats:
        files[TEST / "RunCucumberTest.java"] = env.get_template("RunCucumberTest.java.j2").render()
        for f in feats:
            files[Path("src/test/resources/features") / f.name] = f.read_text()
    return files


def _walk(stmts):
    from ..ir import walk
    return walk(stmts)
