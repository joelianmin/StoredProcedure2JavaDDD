"""mapping.yaml: procedure -> endpoint SNAP (layanan, kode layanan, parameter uang, pemetaan error)."""
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

import yaml

from .ir import Procedure


class MappingError(Exception):
    pass


@dataclass(frozen=True)
class ErrorRule:
    prefix: str
    http: int
    case: str
    message: str


@dataclass(frozen=True)
class ProcedureMapping:
    procedure: str
    service: str
    service_code: str
    money: tuple[str, ...]
    errors: tuple[ErrorRule, ...]


@dataclass(frozen=True)
class Mapping:
    procedures: dict[str, ProcedureMapping]


def load_mapping(path: Path) -> Mapping:
    try:
        raw = yaml.safe_load(path.read_text()) or {}
    except yaml.YAMLError as e:
        raise MappingError(f"YAML tidak valid: {e}")
    procs = raw.get("procedures")
    if not isinstance(procs, dict):
        raise MappingError("kunci 'procedures' wajib berupa map")
    out = {}
    for name, m in procs.items():
        if not isinstance(m, dict):
            raise MappingError(f"{name}: harus berupa map")
        errors = []
        for prefix, e in (m.get("errors") or {}).items():
            if not isinstance(e, dict):
                raise MappingError(f"{name}.errors.{prefix}: harus berupa map {{http, case, message}}")
            errors.append(ErrorRule(str(prefix), e.get("http"), str(e.get("case", "")), str(e.get("message", ""))))
        out[name] = ProcedureMapping(name, str(m.get("service", "")), str(m.get("serviceCode", "")),
                                     tuple(m.get("money") or ()), tuple(errors))
    return Mapping(out)


def validate_mapping(m: Mapping, procs: list[Procedure]) -> list[str]:
    errs = []
    by_name = {p.name: p for p in procs}
    services = {}
    for name, pm in m.procedures.items():
        if name not in by_name:
            errs.append(f"{name}: procedure tidak ada atau tidak didukung")
            continue
        if not re.fullmatch(r"[a-z0-9]+(-[a-z0-9]+)*", pm.service):
            errs.append(f"{name}.service: harus kebab-case, bukan '{pm.service}'")
        if pm.service in services:
            errs.append(f"{name}.service: '{pm.service}' sudah dipakai {services[pm.service]}")
        services[pm.service] = name
        if not re.fullmatch(r"\d{2}", pm.service_code):
            errs.append(f"{name}.serviceCode: harus tepat 2 digit (string), bukan '{pm.service_code}'")
        params = {x.name: x.type for x in by_name[name].params}
        for p in pm.money:
            if params.get(p) != "numeric":
                errs.append(f"{name}.money: '{p}' bukan parameter numeric")
        for e in pm.errors:
            if not isinstance(e.http, int) or not 100 <= e.http <= 599:
                errs.append(f"{name}.errors['{e.prefix}'].http: harus kode HTTP, bukan '{e.http}'")
            if not re.fullmatch(r"\d{2}", e.case):
                errs.append(f"{name}.errors['{e.prefix}'].case: harus tepat 2 digit, bukan '{e.case}'")
    return errs
