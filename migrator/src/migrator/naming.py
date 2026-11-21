"""Aturan penamaan SQL -> Java (deterministik)."""
from __future__ import annotations

from .ir import Procedure

JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
    "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
    "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
    "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var",
    "record", "yield", "sealed", "permits",
}
# nama lokal yang dipakai kode hasil generate
RESERVED_LOCALS = {"found", "row", "hit", "affected", "iterated", "unitOfWork"}


def _camel(parts: list[str]) -> str:
    parts = [p for p in parts if p]
    return parts[0].lower() + "".join(p[:1].upper() + p[1:].lower() for p in parts[1:])


def _escape(name: str) -> str:
    return name + "_" if name in JAVA_KEYWORDS else name


def java_class(sql_name: str) -> str:
    return "".join(p[:1].upper() + p[1:].lower() for p in sql_name.split("_") if p)


def java_field(sql_name: str) -> str:
    """Nama kolom -> field/getter Java (tanpa membuang prefix)."""
    return _escape(_camel(sql_name.split("_")))


def java_member(sql_name: str) -> str:
    """Nama parameter/variabel -> lokal Java; prefix p_/v_ dibuang."""
    parts = sql_name.split("_")
    if len(parts) > 1 and parts[0] in ("p", "v"):
        parts = parts[1:]
    return _escape(_camel(parts))


def member_names(proc: Procedure, extra_reserved: set[str] = frozenset()) -> dict[str, str]:
    """Nama Java untuk semua parameter, variabel, dan record. Bila hasil buang-prefix bentrok
    (sesama nama atau dengan nama lokal cadangan), nama lengkap dipakai."""
    sql_names = [p.name for p in proc.params] + [v.name for v in proc.vars] + list(proc.records)
    short = {n: java_member(n) for n in sql_names}
    reserved = RESERVED_LOCALS | set(extra_reserved)
    counts: dict[str, int] = {}
    for j in short.values():
        counts[j] = counts.get(j, 0) + 1
    out = {}
    for n, j in short.items():
        if counts[j] > 1 or j in reserved:
            j = _escape(_camel(n.split("_")))
            if j in reserved:
                j = j + "Var"
        out[n] = j
    return out


def kebab(sql_name: str) -> str:
    return sql_name.replace("_", "-").lower()


def getter(col: str) -> str:
    f = java_field(col).rstrip("_")
    return "get" + f[:1].upper() + f[1:]


def setter(col: str) -> str:
    f = java_field(col).rstrip("_")
    return "set" + f[:1].upper() + f[1:]
