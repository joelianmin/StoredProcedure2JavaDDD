"""CLI migrator: generate | report."""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog="migrator", description="Migrator deterministik PL/pgSQL -> Spring Boot DDD")
    sub = ap.add_subparsers(dest="cmd", required=True)

    g = sub.add_parser("generate", help="parse korpus dan generate proyek Java")
    g.add_argument("--schema", type=Path, required=True)
    g.add_argument("--procs", type=Path, required=True)
    g.add_argument("--mapping", type=Path)
    g.add_argument("--features", type=Path)
    g.add_argument("--runtime", type=Path, required=True)
    g.add_argument("--out", type=Path, required=True)
    g.add_argument("--no-readable", action="store_true", help="selalu pakai helper Sql.* (tanpa optimasi keterbacaan)")

    r = sub.add_parser("report", help="tulis laporan kelayakan pensiun")
    r.add_argument("--project", type=Path, required=True)
    r.add_argument("--manual", type=Path)
    r.add_argument("--out", type=Path, required=True)

    args = ap.parse_args(argv)
    if args.cmd == "generate":
        from .gen.project import generate_project
        from .mapping import MappingError
        t0 = time.perf_counter()
        try:
            analyses = generate_project(args.schema, args.procs, args.mapping, args.features, args.runtime,
                                        args.out, readable=not args.no_readable)
        except MappingError as e:
            print(f"mapping.yaml tidak valid:\n{e}", file=sys.stderr)
            return 3
        elapsed = time.perf_counter() - t0
        (args.out / "generate-seconds.txt").write_text(f"{elapsed:.3f}\n")
        bad = [a for a in analyses if a.status != "ok"]
        for a in analyses:
            mark = "OK " if a.status == "ok" else "TIDAK DIDUKUNG"
            print(f"  {mark:15} {a.procedure}" + (f"  -> {a.reason}" if a.reason else ""))
        print(f"Proyek ditulis ke {args.out} ({elapsed:.2f} detik)")
        return 2 if bad else 0
    if args.cmd == "report":
        from .report import build_report
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(build_report(args.project, args.manual))
        print(f"Laporan ditulis ke {args.out}")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
