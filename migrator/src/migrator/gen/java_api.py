"""File lapisan API SNAP: request record, controller per procedure, dan tabel rute."""
from __future__ import annotations

from pathlib import Path

from ..mapping import MappingError, load_mapping, validate_mapping
from ..naming import java_class, member_names
from .java import JTYPE, java_string

API = Path("src/main/java/com/p2p/api")


def api_files(tables, procs, models, mapping_path: Path | None, env, analyses) -> dict[Path, str]:
    apis = []
    if mapping_path:
        mapping = load_mapping(mapping_path)
        errs = validate_mapping(mapping, procs)
        if errs:
            raise MappingError("\n".join(f"  - {e}" for e in errs))
        by_name = {p.name: p for p in procs}
        for name, pm in mapping.procedures.items():
            p = by_name[name]
            names = member_names(p)
            base = java_class(pm.service.replace("-", "_"))
            fields = [{"name": names[x.name], "money": x.name in pm.money,
                       "jtype": "SnapAmount" if x.name in pm.money else JTYPE[x.type],
                       "ptype": JTYPE[x.type]} for x in p.params]
            apis.append({"proc_name": name, "service": pm.service, "service_code": pm.service_code,
                         "request": base + "Request", "controller": base + "Controller",
                         "service_cls": java_class(name) + "Service", "fields": fields,
                         "errors": [{"prefix": java_string(e.prefix), "http": e.http, "case": e.case,
                                     "message": java_string(e.message)} for e in pm.errors]})
        for a in analyses:
            if a.procedure in mapping.procedures:
                a.api = True
    files = {API / "GeneratedSnapRoutes.java": env.get_template("GeneratedSnapRoutes.java.j2").render(apis=apis)}
    for a in apis:
        files[API / f"{a['request']}.java"] = env.get_template("Request.java.j2").render(a=a)
        files[API / f"{a['controller']}.java"] = env.get_template("Controller.java.j2").render(a=a)
    return files
