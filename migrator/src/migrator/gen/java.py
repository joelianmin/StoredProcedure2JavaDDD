"""Generator Java: model tabel untuk template, dan terjemahan procedure -> service.

Terjemahan setia secara semantik (helper Sql.* untuk NULL/tiga nilai/skala) dan, bila analyzer
memastikan nilai tidak NULL, memakai Java biasa supaya mudah dibaca (spec §13).
"""
from __future__ import annotations

from dataclasses import dataclass

from ..analyzer import Env
from ..ir import (Assign, Binary, ColumnRef, Const, Delete, Expr, FieldRef, ForQuery, Found, FuncCall, If,
                  Insert, IsNull, Procedure, Raise, Return, SelectInto, Table, Unary, Update, VarRef, Where,
                  walk)
from ..naming import getter, java_class, java_field, member_names, setter
from ..parse import Unsupported

JTYPE = {"text": "String", "numeric": "BigDecimal", "integer": "Integer", "boolean": "Boolean",
         "date": "LocalDate"}
CMP_HELPER = {"=": "eq", "<>": "ne", "<": "lt", "<=": "le", ">": "gt", ">=": "ge"}
NUM_HELPER = {"+": "add", "-": "sub", "*": "mul", "/": "div"}
INT_HELPER = {"+": "iadd", "-": "isub", "*": "imul", "/": "idiv"}
NUM_METHOD = {"+": "add", "-": "subtract", "*": "multiply"}


def java_string(s: str) -> str:
    out = s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    return f'"{out}"'


# ==================================================================== model tabel
@dataclass(frozen=True)
class ColModel:
    name: str
    pg_type: str
    jtype: str
    field: str
    getter: str
    setter: str
    not_null: bool
    pk: bool
    precision: int | None
    scale: int | None


@dataclass(frozen=True)
class Finder:
    cols: tuple[ColModel, ...]

    @property
    def suffix(self) -> str:
        return "And".join(java_class(c.name) for c in self.cols)

    @property
    def params(self) -> str:
        return ", ".join(f"{c.jtype} {c.field}" for c in self.cols)

    @property
    def args(self) -> str:
        return ", ".join(c.field for c in self.cols)


@dataclass(frozen=True)
class TableModel:
    name: str
    cls: str
    cols: tuple[ColModel, ...]
    finders: tuple[Finder, ...]
    deleters: tuple[Finder, ...]

    @property
    def pk(self) -> ColModel:
        return next(c for c in self.cols if c.pk)

    @property
    def var(self) -> str:
        return self.cls[:1].lower() + self.cls[1:]

    def col(self, name: str) -> ColModel:
        return next(c for c in self.cols if c.name == name)


def _where_cols(where: Where) -> tuple[str, ...]:
    return tuple(c for c, _ in where)


def table_models(tables: dict[str, Table], procs: list[Procedure]) -> list[TableModel]:
    finds: dict[str, set] = {t: set() for t in tables}
    dels: dict[str, set] = {t: set() for t in tables}
    for p in procs:
        for s in walk(p.body):
            if not isinstance(s, (Update, ForQuery, Delete)):
                continue
            pk = tables[s.table].pk.name
            if isinstance(s, (Update, ForQuery)) and _where_cols(s.where) != (pk,):
                finds[s.table].add(_where_cols(s.where))
            if isinstance(s, Delete) and _where_cols(s.where) != (pk,):
                dels[s.table].add(_where_cols(s.where))
    models = []
    for t in tables.values():
        cols = tuple(ColModel(c.name, c.type, JTYPE[c.type], java_field(c.name), getter(c.name), setter(c.name),
                              c.not_null, c.pk, c.precision, c.scale) for c in t.columns)
        by = {c.name: c for c in cols}
        models.append(TableModel(t.name, java_class(t.name), cols,
                                 tuple(Finder(tuple(by[c] for c in k)) for k in sorted(finds[t.name])),
                                 tuple(Finder(tuple(by[c] for c in k)) for k in sorted(dels[t.name]))))
    return models


# ==================================================================== service
class ServiceGen:
    def __init__(self, proc: Procedure, tables: dict[str, Table], models: dict[str, TableModel], readable: bool):
        self.p, self.tables, self.models, self.readable = proc, tables, models, readable
        self.env = Env(proc, tables)
        self.touched = sorted({s.table for s in walk(proc.body) if hasattr(s, "table")})
        repo_fields = {self.repo(t) for t in self.touched}
        self.names = member_names(proc, repo_fields)
        self.uses_found = any(self._has_found(s) for s in walk(proc.body))
        self.counter = 0
        for s in walk(proc.body):
            if isinstance(s, Assign) and s.target in {x.name for x in proc.params}:
                raise Unsupported("assignment ke parameter", s.line, s.target)

    # ------------------------------------------------------------ util
    def repo(self, table: str) -> str:
        return self.models[table].var + "Repository"

    def _has_found(self, s) -> bool:
        def has(e):
            return isinstance(e, Found) or any(has(c) for c in _kids(e))
        exprs = []
        if isinstance(s, If):
            exprs = [c for c, _ in s.branches]
        elif isinstance(s, Assign):
            exprs = [s.value]
        elif isinstance(s, Raise):
            exprs = list(s.params)
        return any(has(e) for e in exprs)

    def nn(self, e: Expr, row: str | None = None) -> bool:
        return self.readable and not self.env.nullable(e, row)

    def ty(self, e: Expr, row: str | None = None):
        return self.env.type_of(e, row)

    def src(self, line) -> str:
        return f"// {self.p.source_file}:{line}" if line else ""

    # ------------------------------------------------------------ ekspresi
    def coerce(self, e: Expr, want: str | None, row: str | None = None) -> str:
        code = self.expr(e, row)
        t = self.ty(e, row)
        if want == "numeric" and t == "integer":
            return f"Sql.num({code})"
        if want == "integer" and t == "numeric":
            raise Unsupported("konversi numeric -> integer", None, "pembulatan implisit tidak didukung")
        if t is None and want and isinstance(e, Const):
            return f"({JTYPE[want]}) null"
        return code

    def expr(self, e: Expr, row: str | None = None) -> str:
        if isinstance(e, Const):
            if e.value is None:
                return "null"
            if e.type == "numeric":
                return f'new BigDecimal("{e.value}")'
            if e.type == "integer":
                return f"Integer.valueOf({e.value})"
            if e.type == "boolean":
                return "Boolean.TRUE" if e.value == "true" else "Boolean.FALSE"
            return java_string(e.value)
        if isinstance(e, VarRef):
            return self.names[e.name]
        if isinstance(e, FieldRef):
            return f"{self.names[e.record]}.{getter(e.field)}()"
        if isinstance(e, ColumnRef):
            return f"row.{getter(e.name)}()"
        if isinstance(e, Found):
            return "found"
        if isinstance(e, IsNull):
            return f"({self.expr(e.arg, row)} {'!=' if e.negated else '=='} null)"
        if isinstance(e, Unary):
            inner = self.expr(e.arg, row)
            if e.op == "NOT":
                if not self.nn(e.arg, row):
                    return f"Sql.not({inner})"
                return f"!{inner}" if inner.isidentifier() else f"!({inner})"
            if self.ty(e.arg, row) == "integer":
                return f"Sql.ineg({inner})"
            return f"{inner}.negate()" if self.nn(e.arg, row) else f"Sql.neg({inner})"
        if isinstance(e, FuncCall):
            if e.name == "round":
                scale = self.coerce(e.args[1], "integer", row) if len(e.args) > 1 else "0"
                return f"Sql.round({self.coerce(e.args[0], 'numeric', row)}, {scale})"
            t = self.ty(e, row)
            return f"Sql.coalesce({', '.join(self.coerce(a, t, row) for a in e.args)})"
        if isinstance(e, Binary):
            return self.binary(e, row)
        raise Unsupported("ekspresi", None, type(e).__name__)

    def binary(self, e: Binary, row) -> str:
        lt, rt = self.ty(e.left, row), self.ty(e.right, row)
        both_nn = self.nn(e.left, row) and self.nn(e.right, row)
        if e.op in ("AND", "OR"):
            l, r = self.expr(e.left, row), self.expr(e.right, row)
            if both_nn:
                return f"({l} {'&&' if e.op == 'AND' else '||'} {r})"
            return f"Sql.{e.op.lower()}({l}, {r})"
        if e.op == "||":
            return f"Sql.concat({self.expr(e.left, row)}, {self.expr(e.right, row)})"
        if e.op in CMP_HELPER:
            want = "numeric" if "numeric" in (lt, rt) else None
            l, r = self.coerce(e.left, want, row), self.coerce(e.right, want, row)
            if both_nn:
                if (lt or rt) in ("text", "boolean") and e.op in ("=", "<>"):
                    return f"{'!' if e.op == '<>' else ''}{l}.equals({r})"
                op = "!=" if e.op == "<>" else ("==" if e.op == "=" else e.op)
                return f"{l}.compareTo({r}) {op} 0"
            return f"Sql.{CMP_HELPER[e.op]}({l}, {r})"
        # aritmetika
        if lt == "integer" and rt in ("integer", None) or rt == "integer" and lt is None:
            return f"Sql.{INT_HELPER[e.op]}({self.expr(e.left, row)}, {self.expr(e.right, row)})"
        l, r = self.coerce(e.left, "numeric", row), self.coerce(e.right, "numeric", row)
        if both_nn and e.op in NUM_METHOD:
            return f"{l}.{NUM_METHOD[e.op]}({r})"
        return f"Sql.{NUM_HELPER[e.op]}({l}, {r})"

    def cond(self, e: Expr) -> str:
        code = self.expr(e)
        if self.nn(e):
            return code[1:-1] if code.startswith("(") and code.endswith(")") and _balanced(code[1:-1]) else code
        return f"Sql.isTrue({code})"

    # ------------------------------------------------------------ pernyataan
    def stmts(self, stmts, ind: int) -> list[str]:
        out = []
        for i, s in enumerate(stmts):
            out += self.stmt(s, ind, stmts[i + 1] if i + 1 < len(stmts) else None)
        return out

    def stmt(self, s, ind: int, nxt) -> list[str]:
        p = "    " * ind
        head = [p + self.src(s.line)]
        if isinstance(s, Assign):
            return head + [f"{p}{self.names[s.target]} = {self.coerce(s.value, self.env.var_type(s.target))};"]
        if isinstance(s, If):
            out = head[:]
            for i, (c, body) in enumerate(s.branches):
                kw = "if" if i == 0 else "} else if"
                out.append(f"{p}{kw} ({self.cond(c)}) {{")
                out += self.stmts(body, ind + 1)
            if s.else_body:
                out.append(f"{p}}} else {{")
                out += self.stmts(s.else_body, ind + 1)
            return out + [f"{p}}}"]
        if isinstance(s, SelectInto):
            m = self.models[s.table]
            self.counter += 1
            var = f"{m.var}{self.counter}"
            out = head + [f"{p}Optional<{m.cls}> {var} = {self.repo(s.table)}.findById({self.coerce(s.key, m.pk.pg_type)});"]
            if self.uses_found:
                out.append(f"{p}found = {var}.isPresent();")
            for col, tgt in zip(s.columns, s.targets):
                out.append(f"{p}{self.names[tgt]} = {var}.map({m.cls}::{getter(col)}).orElse(null);")
            return out
        if isinstance(s, Update):
            return head + self.update(s, ind)
        if isinstance(s, Insert):
            m = self.models[s.table]
            given = dict(s.values)
            args = [self.coerce(given[c.name], c.pg_type) if c.name in given else f"({c.jtype}) null" for c in m.cols]
            out = head + [f"{p}{self.repo(s.table)}.insert(new {m.cls}({', '.join(args)}));"]
            if self.uses_found:
                out.append(f"{p}found = true;")
            return out
        if isinstance(s, Delete):
            m = self.models[s.table]
            if _where_cols(s.where) == (m.pk.name,):
                call = f"{self.repo(s.table)}.deleteById({self.coerce(s.where[0][1], m.pk.pg_type)})"
            else:
                f = Finder(tuple(m.col(c) for c, _ in s.where))
                args = ", ".join(self.coerce(v, m.col(c).pg_type) for c, v in s.where)
                call = f"{self.repo(s.table)}.deleteBy{f.suffix}({args})"
            return head + ([f"{p}found = {call} > 0;"] if self.uses_found else [f"{p}{call};"])
        if isinstance(s, ForQuery):
            m = self.models[s.table]
            f = Finder(tuple(m.col(c) for c, _ in s.where))
            args = ", ".join(self.coerce(v, m.col(c).pg_type) for c, v in s.where)
            rec = self.names[s.record]
            out = head[:]
            if self.uses_found:
                out.append(f"{p}found = false;")
            out.append(f"{p}for ({m.cls} {rec} : {self.repo(s.table)}.findBy{f.suffix}({args})) {{")
            if self.uses_found:
                out.append(f"{p}    found = true;")
            out += self.stmts(s.body, ind + 1)
            return out + [f"{p}}}"]
        if isinstance(s, Raise):
            if "%" in s.message:
                args = "".join(", " + self.expr(a) for a in s.params)
                msg = f"Sql.format({java_string(s.message)}{args})"
            else:
                msg = java_string(s.message)
            return head + [f'{p}throw new ProcedureException("P0001", {msg});']
        if isinstance(s, Return):
            return head + [f"{p}return;"]
        raise Unsupported("pernyataan", s.line, type(s).__name__)

    def update(self, s: Update, ind: int) -> list[str]:
        p = "    " * ind
        m = self.models[s.table]
        if any(c == m.pk.name for c, _ in s.sets):
            raise Unsupported("UPDATE kolom primary key", s.line)
        # semua RHS dihitung dari baris lama dulu (semantik SQL), lalu di-set
        needs_tmp = len(s.sets) > 1 and any(_mentions_column(v) for _, v in s.sets)
        body = []
        if needs_tmp:
            for i, (c, v) in enumerate(s.sets):
                body.append(f"{m.col(c).jtype} new{i} = {self.coerce(v, m.col(c).pg_type, s.table)};")
            for i, (c, _) in enumerate(s.sets):
                body.append(f"row.{m.col(c).setter}(new{i});")
        else:
            for c, v in s.sets:
                body.append(f"row.{m.col(c).setter}({self.coerce(v, m.col(c).pg_type, s.table)});")
        body.append(f"{self.repo(s.table)}.save(row);")
        if _where_cols(s.where) == (m.pk.name,):
            key = self.coerce(s.where[0][1], m.pk.pg_type)
            self.counter += 1
            hit = f"hit{self.counter}"
            out = [f"{p}Optional<{m.cls}> {hit} = {self.repo(s.table)}.findById({key});"]
            if self.uses_found:
                out.append(f"{p}found = {hit}.isPresent();")
            out.append(f"{p}if ({hit}.isPresent()) {{")
            out.append(f"{p}    {m.cls} row = {hit}.get();")
            out += [f"{p}    {b}" for b in body]
            return out + [f"{p}}}"]
        f = Finder(tuple(m.col(c) for c, _ in s.where))
        args = ", ".join(self.coerce(v, m.col(c).pg_type) for c, v in s.where)
        out = []
        if self.uses_found:
            out.append(f"{p}found = false;")
        out.append(f"{p}for ({m.cls} row : {self.repo(s.table)}.findBy{f.suffix}({args})) {{")
        if self.uses_found:
            out.append(f"{p}    found = true;")
        out += [f"{p}    {b}" for b in body]
        return out + [f"{p}}}"]

    # ------------------------------------------------------------ kelas
    def render(self) -> dict:
        """Data untuk Service.java.j2."""
        decls = []
        if self.uses_found:
            decls.append("boolean found = false;")
        for v in self.p.vars:
            init = self.coerce(v.default, v.type) if v.default is not None else "null"
            decls.append(f"{JTYPE[v.type]} {self.names[v.name]} = {init};")
        body = self.stmts(self.p.body, 3)
        sig = ", ".join(f"{JTYPE[x.type]} {self.names[x.name]}" for x in self.p.params)
        sql_sig = ", ".join(f"{x.name} {x.type}" for x in self.p.params)
        return {
            "cls": java_class(self.p.name) + "Service",
            "proc": self.p,
            "sql_sig": sql_sig,
            "signature": sig,
            "repos": [(self.models[t].cls, self.repo(t)) for t in self.touched],
            "decls": decls,
            "body": [line for line in body if line.strip()],
            "uses_date": "LocalDate" in sig or any("LocalDate" in d for d in decls),
        }


def _kids(e):
    if isinstance(e, Binary):
        return (e.left, e.right)
    if isinstance(e, (Unary, IsNull)):
        return (e.arg,)
    if isinstance(e, FuncCall):
        return e.args
    return ()


def _mentions_column(e) -> bool:
    return isinstance(e, ColumnRef) or any(_mentions_column(c) for c in _kids(e))


def _balanced(s: str) -> bool:
    depth = 0
    for ch in s:
        depth += ch == "("
        depth -= ch == ")"
        if depth < 0:
            return False
    return depth == 0
