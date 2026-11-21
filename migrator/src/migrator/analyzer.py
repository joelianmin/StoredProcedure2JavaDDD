"""Analyzer: tipe ekspresi, nullability (untuk J1/J2 dan keterbacaan output), jebakan J1-J12, status.

Aturan nullability (sound, flow-insensitive; spec §13):
- parameter selalu nullable;
- variabel non-null bila SETIAP definisinya non-null dan tidak dibaca sebelum definisi pertamanya.
  Definisi non-null: default/assign dengan ekspresi non-null, atau "guarded select" = SELECT INTO dari
  kolom NOT NULL yang langsung diikuti `IF NOT FOUND THEN ... RAISE ... END IF` tanpa else;
- kolom/field record mengikuti NOT NULL di skema.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .ir import (Assign, Binary, ColumnRef, Const, Delete, Expr, FieldRef, ForQuery, Found, FuncCall, If,
                 Insert, IsNull, PgType, Procedure, Raise, Return, SelectInto, Stmt, Table, Unary, Update,
                 VarRef, walk)
from .parse import Unsupported

COMPARE = {"=", "<>", "<", "<=", ">", ">="}
ARITH = {"+", "-", "*", "/"}


def sql_text(e: Expr) -> str:
    """Tampilan ekspresi ala SQL untuk laporan."""
    if isinstance(e, Const):
        return "NULL" if e.value is None else (f"'{e.value}'" if e.type == "text" else e.value)
    if isinstance(e, VarRef):
        return e.name
    if isinstance(e, FieldRef):
        return f"{e.record}.{e.field}"
    if isinstance(e, ColumnRef):
        return e.name
    if isinstance(e, Found):
        return "FOUND"
    if isinstance(e, Binary):
        return f"({sql_text(e.left)} {e.op} {sql_text(e.right)})"
    if isinstance(e, Unary):
        return f"{e.op} {sql_text(e.arg)}" if e.op == "NOT" else f"-{sql_text(e.arg)}"
    if isinstance(e, IsNull):
        return f"{sql_text(e.arg)} IS {'NOT ' if e.negated else ''}NULL"
    if isinstance(e, FuncCall):
        return f"{e.name}({', '.join(sql_text(a) for a in e.args)})"
    return "?"


class Env:
    """Konteks tipe + nullability untuk satu procedure."""

    def __init__(self, proc: Procedure, tables: dict[str, Table]):
        self.proc, self.tables = proc, tables
        self.params = {p.name: p.type for p in proc.params}
        self.vars = {v.name: v.type for v in proc.vars}
        self.records: dict[str, str] = {}
        for s in walk(proc.body):
            if isinstance(s, ForQuery):
                self.records[s.record] = s.table
        self.nn_vars = self._non_null_vars()

    # ------------------------------------------------------------ tipe
    def var_type(self, name: str) -> PgType:
        return self.params.get(name) or self.vars[name]

    def column(self, table: str, col: str):
        return self.tables[table].column(col)

    def type_of(self, e: Expr, row_table: str | None = None) -> PgType | None:
        if isinstance(e, Const):
            return e.type
        if isinstance(e, VarRef):
            return self.var_type(e.name)
        if isinstance(e, FieldRef):
            return self.column(self.records[e.record], e.field).type
        if isinstance(e, ColumnRef):
            return self.column(row_table, e.name).type
        if isinstance(e, (Found, IsNull)):
            return "boolean"
        if isinstance(e, Unary):
            return "boolean" if e.op == "NOT" else self.type_of(e.arg, row_table)
        if isinstance(e, FuncCall):
            if e.name == "round":
                return "numeric"
            return next((t for t in (self.type_of(a, row_table) for a in e.args) if t), None)
        if isinstance(e, Binary):
            lt, rt = self.type_of(e.left, row_table), self.type_of(e.right, row_table)
            if e.op in ("AND", "OR"):
                return "boolean"
            if e.op == "||":
                return "text"
            if e.op in COMPARE:
                if "text" in (lt, rt) and e.op not in ("=", "<>"):
                    raise Unsupported("perbandingan teks", None, f"{sql_text(e)} (bergantung collation)")
                return "boolean"
            if "text" in (lt, rt) or "boolean" in (lt, rt) or "date" in (lt, rt):
                raise Unsupported("aritmetika", None, f"{sql_text(e)} pada tipe {lt}/{rt}")
            if lt == "integer" and rt in ("integer", None) or rt == "integer" and lt is None:
                return "integer"
            return "numeric"
        raise Unsupported("ekspresi", None, type(e).__name__)

    # ------------------------------------------------------------ nullability
    def nullable(self, e: Expr, row_table: str | None = None) -> bool:
        if isinstance(e, Const):
            return e.value is None
        if isinstance(e, VarRef):
            return e.name not in self.nn_vars
        if isinstance(e, FieldRef):
            return not self.column(self.records[e.record], e.field).not_null
        if isinstance(e, ColumnRef):
            return not self.column(row_table, e.name).not_null
        if isinstance(e, (Found, IsNull)):
            return False
        if isinstance(e, Unary):
            return self.nullable(e.arg, row_table)
        if isinstance(e, Binary):
            return self.nullable(e.left, row_table) or self.nullable(e.right, row_table)
        if isinstance(e, FuncCall):
            if e.name == "coalesce":
                return all(self.nullable(a, row_table) for a in e.args)
            return any(self.nullable(a, row_table) for a in e.args)
        return True

    def _non_null_vars(self) -> set[str]:
        defs: dict[str, list] = {v.name: [] for v in self.proc.vars}
        for v in self.proc.vars:
            if v.default is not None:
                defs[v.name].append(("expr", v.default))
        # Definite assignment terstruktur: sebuah variabel hanya dianggap "pasti terisi" di titik baca
        # bila definisinya mendominasi titik itu. Definisi di dalam IF dihitung hanya bila SEMUA cabang
        # (termasuk ELSE) mengisinya; definisi di dalam FOR tidak pernah dihitung setelah loop
        # (loop bisa berjalan nol kali). Cabang yang berakhir RAISE/RETURN tidak ikut irisan.
        unassigned_read: set[str] = set()
        TOP = None                                # jalur tidak berlanjut (RAISE/RETURN)

        def reads(e: Expr, out: list):
            if isinstance(e, VarRef):
                out.append(e.name)
            for child in _children(e):
                reads(child, out)

        def meet(states):
            live = [s for s in states if s is not TOP]
            if not live:
                return TOP
            out = set(live[0])
            for s in live[1:]:
                out &= s
            return out

        def visit(stmts, assigned):
            for i, s in enumerate(stmts):
                if assigned is TOP:
                    return TOP
                for e in _exprs_of(s):
                    names: list = []
                    reads(e, names)
                    unassigned_read.update(n for n in names if n in defs and n not in assigned)
                if isinstance(s, Assign):
                    defs[s.target].append(("expr", s.value))
                    assigned = assigned | {s.target}
                elif isinstance(s, SelectInto):
                    guarded = i + 1 < len(stmts) and _is_not_found_guard(stmts[i + 1])
                    for col, tgt in zip(s.columns, s.targets):
                        ok = guarded and self.column(s.table, col).not_null
                        defs[tgt].append(("const", not ok))
                    assigned = assigned | set(s.targets)
                elif isinstance(s, If):
                    outs = [visit(b, assigned) for _, b in s.branches]
                    outs.append(visit(s.else_body, assigned) if s.else_body else assigned)
                    assigned = meet(outs)
                elif isinstance(s, ForQuery):
                    visit(s.body, assigned)             # isi loop tidak mendominasi pernyataan sesudahnya
                elif isinstance(s, (Raise, Return)):
                    return TOP
            return assigned

        visit(self.proc.body, {v.name for v in self.proc.vars if v.default is not None})
        nn = {n for n, d in defs.items() if d and n not in unassigned_read}
        changed = True
        while changed:                          # titik tetap terbesar
            changed = False
            self.nn_vars = nn
            for n in list(nn):
                for kind, val in defs[n]:
                    if (kind == "const" and val) or (kind == "expr" and self.nullable(val)):
                        nn.discard(n)
                        changed = True
                        break
        return nn


def _children(e: Expr):
    if isinstance(e, Binary):
        return (e.left, e.right)
    if isinstance(e, (Unary, IsNull)):
        return (e.arg,)
    if isinstance(e, FuncCall):
        return e.args
    return ()


def _exprs_of(s: Stmt):
    if isinstance(s, Assign):
        return (s.value,)
    if isinstance(s, If):
        return tuple(c for c, _ in s.branches)
    if isinstance(s, SelectInto):
        return (s.key,)
    if isinstance(s, (Update,)):
        return tuple(v for _, v in s.sets) + tuple(v for _, v in s.where)
    if isinstance(s, Insert):
        return tuple(v for _, v in s.values)
    if isinstance(s, (Delete, ForQuery)):
        return tuple(v for _, v in s.where)
    if isinstance(s, Raise):
        return s.params
    return ()


def _is_not_found_guard(s: Stmt) -> bool:
    return (isinstance(s, If) and len(s.branches) == 1 and not s.else_body
            and s.branches[0][0] == Unary("NOT", Found())
            and bool(s.branches[0][1]) and isinstance(s.branches[0][1][-1], Raise))


def is_not_found_guard(s: Stmt) -> bool:
    return _is_not_found_guard(s)


# ==================================================================== analisis
@dataclass(frozen=True)
class TrapHit:
    code: str
    line: int
    detail: str


@dataclass
class Analysis:
    procedure: str
    source_file: str
    status: str                       # ok | unsupported
    reason: str | None = None
    tables_read: list[str] = field(default_factory=list)
    tables_written: list[str] = field(default_factory=list)
    branch_count: int = 0
    constructs: list[str] = field(default_factory=list)
    traps: list[TrapHit] = field(default_factory=list)
    api: bool = False

    def to_dict(self) -> dict:
        return {"procedure": self.procedure, "source_file": self.source_file, "status": self.status,
                "reason": self.reason, "tables_read": self.tables_read, "tables_written": self.tables_written,
                "branch_count": self.branch_count, "constructs": self.constructs, "api": self.api,
                "traps": [{"code": t.code, "line": t.line, "detail": t.detail} for t in self.traps]}


def unsupported_analysis(name: str, source_file: str, err: Unsupported) -> Analysis:
    return Analysis(name, source_file, "unsupported", f"konstruksi tidak didukung: {err}")


def analyze(proc: Procedure, tables: dict[str, Table]) -> Analysis:
    env = Env(proc, tables)
    traps: list[TrapHit] = []
    read, written, constructs = set(), set(), set()
    branches = 0
    wrote_before = False

    def check_expr(e: Expr, line: int, row_table: str | None, in_cond: bool):
        env.type_of(e, row_table)                           # memunculkan Unsupported bila tipe tidak valid
        if isinstance(e, Binary):
            lt, rt = env.type_of(e.left, row_table), env.type_of(e.right, row_table)
            nl = [x for x in (e.left, e.right) if env.nullable(x, row_table)]
            if e.op in COMPARE and in_cond and nl:
                traps.append(TrapHit("J1", line, f"{sql_text(e)}: {', '.join(sql_text(x) for x in nl)} bisa NULL"))
            if e.op in ARITH and nl:
                traps.append(TrapHit("J2", line, f"{sql_text(e)}: {', '.join(sql_text(x) for x in nl)} bisa NULL"))
            if e.op == "/":
                both_int = lt == "integer" and rt == "integer"
                traps.append(TrapHit("J7" if both_int else "J6", line, sql_text(e)))
            if e.op == "||":
                traps.append(TrapHit("J8", line, sql_text(e)))
            if e.op in ("=", "<>") and "numeric" in (lt, rt):
                traps.append(TrapHit("J9", line, sql_text(e)))
        if isinstance(e, FuncCall) and e.name == "round":
            traps.append(TrapHit("J5", line, sql_text(e)))
        for c in _children(e):
            check_expr(c, line, row_table, in_cond)

    def write_traps(table: str, cols, line: int, insert: bool):
        for c in cols:
            col = tables[table].column(c)
            if col.type == "numeric" and col.precision is not None:
                traps.append(TrapHit("J11", line, f"{table}.{c} numeric({col.precision},{col.scale})"))
            if col.not_null and not insert:
                traps.append(TrapHit("J12", line, f"{table}.{c} NOT NULL"))
        if insert:
            traps.append(TrapHit("J12", line, f"INSERT {table} (NOT NULL/PK ganda)"))

    def visit(stmts):
        nonlocal branches, wrote_before
        for s in stmts:
            constructs.add(type(s).__name__)
            if isinstance(s, If):
                branches += len(s.branches) + (1 if s.else_body else 0)
                for cond, body in s.branches:
                    check_expr(cond, s.line, None, True)
                    visit(body)
                visit(s.else_body)
            elif isinstance(s, Assign):
                check_expr(s.value, s.line, None, False)
            elif isinstance(s, SelectInto):
                read.add(s.table)
                check_expr(s.key, s.line, None, False)
                traps.append(TrapHit("J3", s.line, f"SELECT INTO dari {s.table}"))
            elif isinstance(s, Update):
                written.add(s.table)
                for _, v in s.sets:
                    check_expr(v, s.line, s.table, False)
                for _, v in s.where:
                    check_expr(v, s.line, None, False)
                write_traps(s.table, [c for c, _ in s.sets], s.line, False)
                wrote_before = True
            elif isinstance(s, Insert):
                written.add(s.table)
                for _, v in s.values:
                    check_expr(v, s.line, None, False)
                write_traps(s.table, [c for c, _ in s.values], s.line, True)
                wrote_before = True
            elif isinstance(s, Delete):
                written.add(s.table)
                for _, v in s.where:
                    check_expr(v, s.line, None, False)
                wrote_before = True
            elif isinstance(s, ForQuery):
                read.add(s.table)
                for _, v in s.where:
                    check_expr(v, s.line, None, False)
                visit(s.body)
            elif isinstance(s, Raise):
                for p in s.params:
                    check_expr(p, s.line, None, False)
                if s.params:
                    traps.append(TrapHit("J10", s.line, f"RAISE '{s.message}' dengan {len(s.params)} argumen"))
                if wrote_before:
                    traps.append(TrapHit("J4", s.line, "RAISE setelah penulisan: semua perubahan harus di-rollback"))

    for v in proc.vars:
        if v.default is not None:
            check_expr(v.default, 0, None, False)
    visit(proc.body)
    traps = sorted(set(traps), key=lambda t: (t.line, t.code, t.detail))
    return Analysis(proc.name, proc.source_file, "ok", None, sorted(read), sorted(written),
                    branches, sorted(constructs), traps)
