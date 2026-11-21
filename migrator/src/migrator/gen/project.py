"""Menulis proyek Maven Spring Boot hasil generate (deterministik)."""
from __future__ import annotations

import json
import shutil
from pathlib import Path

from jinja2 import Environment, PackageLoader, StrictUndefined

from ..analyzer import Analysis, analyze, unsupported_analysis
from ..ir import Procedure
from ..parse import Unsupported, parse_procedure, parse_schema
from .java import ServiceGen, table_models

JAVA = Path("src/main/java/com/p2p")
TEST_JAVA = Path("src/test/java/com/p2p")


def _jinja() -> Environment:
    return Environment(loader=PackageLoader("migrator.gen", "templates"), undefined=StrictUndefined,
                       keep_trailing_newline=True, trim_blocks=False, lstrip_blocks=False)


def load_corpus(schema: Path, procs_dir: Path):
    """Parse skema + procedure. Mengembalikan (tables, [Procedure ok], [Analysis semua])."""
    tables = parse_schema(schema.read_text())
    procs: list[Procedure] = []
    analyses: list[Analysis] = []
    for f in sorted(procs_dir.glob("*.sql")):
        try:
            p = parse_procedure(f.read_text(), tables, f.name)
            a = analyze(p, tables)
            procs.append(p)
            analyses.append(a)
        except Unsupported as e:
            analyses.append(unsupported_analysis(f.stem, f.name, e))
        except Exception as e:                                   # bug migrator: tandai, jangan crash
            analyses.append(unsupported_analysis(f.stem, f.name,
                                                 Unsupported("kesalahan internal migrator", None, repr(e))))
    return tables, procs, analyses


def generate_project(schema: Path, procs_dir: Path, mapping: Path | None, features: Path | None,
                     runtime: Path, out: Path, readable: bool = True) -> list[Analysis]:
    tables, procs, analyses = load_corpus(schema, procs_dir)
    files: dict[Path, str] = {}
    env = _jinja()

    # procedure yang gagal di generator juga ditandai unsupported
    ok_procs, by_name = [], {a.procedure: a for a in analyses}
    models_all = {m.name: m for m in table_models(tables, procs)}
    services = {}
    for p in procs:
        try:
            services[p.name] = ServiceGen(p, tables, models_all, readable).render()
            ok_procs.append(p)
        except Unsupported as e:
            by_name[p.name] = unsupported_analysis(p.name, p.source_file, e)
        except Exception as e:
            by_name[p.name] = unsupported_analysis(p.name, p.source_file,
                                                   Unsupported("kesalahan internal generator", None, repr(e)))
    analyses = [by_name[a.procedure] for a in analyses]
    models = table_models(tables, ok_procs)

    files[Path("pom.xml")] = env.get_template("pom.xml.j2").render()
    files[JAVA / "P2pApplication.java"] = env.get_template("Application.java.j2").render()
    for name in ("application", "application-memory", "application-postgres"):
        files[Path(f"src/main/resources/{name}.properties")] = env.get_template(f"{name}.properties.j2").render()
    for m in models:
        files[JAVA / "domain" / f"{m.cls}.java"] = env.get_template("Entity.java.j2").render(t=m)
        files[JAVA / "repository" / f"{m.cls}Repository.java"] = env.get_template("Repository.java.j2").render(t=m)
        files[JAVA / "infrastructure" / f"InMemory{m.cls}Repository.java"] = \
            env.get_template("InMemoryRepository.java.j2").render(t=m)
        files[JAVA / "infrastructure" / f"Jdbc{m.cls}Repository.java"] = \
            env.get_template("JdbcRepository.java.j2").render(t=m)
    for p in ok_procs:
        s = services[p.name]
        files[JAVA / "service" / f"{s['cls']}.java"] = env.get_template("Service.java.j2").render(s=s)

    # runtime tetap
    rt = runtime / "src/main/java/com/p2p"
    for pkg, dest in (("support", JAVA), ("snap", JAVA), ("equivalence", TEST_JAVA)):
        for f in sorted((rt / pkg).glob("*.java")):
            files[dest / pkg / f.name] = f.read_text()

    # hook untuk task berikutnya (test kesetaraan, API)
    extra = _extra_files(tables, ok_procs, models, mapping, features, runtime, env, analyses)
    files.update(extra)

    files[Path("migrator-analysis.json")] = json.dumps(
        {"procedures": [a.to_dict() for a in analyses]}, indent=2, sort_keys=True, ensure_ascii=False) + "\n"

    if out.exists():
        shutil.rmtree(out)
    for rel in sorted(files):
        path = out / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(files[rel])
    return analyses


def _extra_files(tables, procs, models, mapping, features, runtime, env, analyses) -> dict[Path, str]:
    files: dict[Path, str] = {}
    from .java_test import test_files
    files.update(test_files(tables, procs, models, features, env))
    from .java_api import api_files
    files.update(api_files(tables, procs, models, mapping, env, analyses))
    return files
