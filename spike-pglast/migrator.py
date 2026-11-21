"""SPIKE (sekali pakai): PL/pgSQL stored procedure -> kode DDD Java (repository Map).

Alur:  [1] parse dua tingkat (pglast)  ->  [2] IR milik sendiri  ->  [3] generator Java
  - parse_sql       : DDL tabel + signature procedure + setiap SQL/ekspresi yang tertanam
  - parse_plpgsql   : struktur procedure (DECLARE, IF, RAISE, ...)
"""
import sys
from dataclasses import dataclass, field
from pathlib import Path

import pglast
from pglast import ast as A
from pglast import parse_sql
from pglast.enums import BoolExprType, ConstrType, NullTestType


class Unsupported(Exception):
    """Konstruksi di luar cakupan -> gagal eksplisit, bukan menebak."""


# ============================== [2] IR ==============================
@dataclass
class Column:
    name: str
    type: str
    not_null: bool
    pk: bool


@dataclass
class Table:
    name: str
    columns: list


@dataclass
class Lit:
    value: object  # str | int | float-string | None
    kind: str      # 'text' | 'numeric' | 'null'


@dataclass
class Ref:          # variabel/parameter procedure ATAU kolom baris (dalam UPDATE)
    name: str


@dataclass
class Found:
    pass


@dataclass
class Binary:
    op: str
    left: object
    right: object


@dataclass
class Not:
    arg: object


@dataclass
class IsNull:
    arg: object
    negated: bool


@dataclass
class SelectInto:
    table: str
    columns: list
    targets: list
    key_col: str
    key_expr: object


@dataclass
class Update:
    table: str
    sets: list        # [(kolom, expr)]
    key_col: str
    key_expr: object


@dataclass
class Raise:
    message: str
    params: list


@dataclass
class Assign:
    target: str
    value: object


@dataclass
class If:
    branches: list    # [(cond, [stmt])]
    else_body: list


@dataclass
class Procedure:
    name: str
    params: list      # [(nama, tipe)]
    vars: list        # [(nama, tipe, default_expr|None)]
    body: list = field(default_factory=list)


# ============================== [1] Parse ==============================
def type_name(tn):
    return tn.names[-1].sval


def parse_schema(sql):
    tables = {}
    for raw in parse_sql(sql):
        st = raw.stmt
        if not isinstance(st, A.CreateStmt):
            raise Unsupported(f"DDL tidak didukung: {type(st).__name__}")
        cols = []
        for c in st.tableElts:
            kinds = {k.contype for k in (c.constraints or ())}
            allowed = {ConstrType.CONSTR_NOTNULL, ConstrType.CONSTR_PRIMARY}
            if kinds - allowed:
                raise Unsupported(f"Constraint tidak didukung di {st.relation.relname}.{c.colname}: {kinds - allowed}")
            pk = ConstrType.CONSTR_PRIMARY in kinds
            cols.append(Column(c.colname, type_name(c.typeName), pk or ConstrType.CONSTR_NOTNULL in kinds, pk))
        tables[st.relation.relname] = Table(st.relation.relname, cols)
    return tables


def expr_from_sql(text):
    """Tingkat 2: ekspresi PL/pgSQL masih string -> parse ulang sebagai SQL."""
    return to_expr(parse_sql(f"SELECT {text}")[0].stmt.targetList[0].val)


OPS = {"+", "-", "*", "/", "=", "<>", "!=", "<", "<=", ">", ">="}


def to_expr(n):
    if isinstance(n, A.A_Const):
        if n.isnull:
            return Lit(None, "null")
        v = n.val
        if isinstance(v, A.String):
            return Lit(v.sval, "text")
        if isinstance(v, A.Integer):
            return Lit(str(v.ival), "numeric")
        if isinstance(v, A.Float):
            return Lit(v.fval, "numeric")
    if isinstance(n, A.ColumnRef):
        name = n.fields[-1].sval
        return Found() if name == "found" else Ref(name)
    if isinstance(n, A.A_Expr) and n.name[0].sval in OPS:
        return Binary(n.name[0].sval, to_expr(n.lexpr), to_expr(n.rexpr))
    if isinstance(n, A.BoolExpr):
        if n.boolop == BoolExprType.NOT_EXPR:
            return Not(to_expr(n.args[0]))
        op = "AND" if n.boolop == BoolExprType.AND_EXPR else "OR"
        node = to_expr(n.args[0])
        for a in n.args[1:]:
            node = Binary(op, node, to_expr(a))
        return node
    if isinstance(n, A.NullTest):
        return IsNull(to_expr(n.arg), n.nulltesttype == NullTestType.IS_NOT_NULL)
    raise Unsupported(f"Ekspresi tidak didukung: {type(n).__name__}")


def key_equality(where):
    """WHERE <kolom> = <ekspresi> (cakupan spike: satu kesetaraan)."""
    if not (isinstance(where, A.A_Expr) and where.name[0].sval == "=" and isinstance(where.lexpr, A.ColumnRef)):
        raise Unsupported("WHERE harus berbentuk <kolom> = <ekspresi>")
    return where.lexpr.fields[-1].sval, to_expr(where.rexpr)


def unwrap(node):
    (kind, body), = node.items()
    return kind, body


def to_stmt(node):
    kind, s = unwrap(node)
    if kind == "PLpgSQL_stmt_execsql":
        sql = parse_sql(s["sqlstmt"]["PLpgSQL_expr"]["query"])[0].stmt
        if isinstance(sql, A.SelectStmt) and s.get("into"):
            if len(sql.fromClause) != 1 or not isinstance(sql.fromClause[0], A.RangeVar):
                raise Unsupported("SELECT INTO harus dari satu tabel")
            tkind, target = unwrap(s["target"])
            targets = [f["name"] for f in target["fields"]] if tkind == "PLpgSQL_row" else [target["refname"]]
            col, key = key_equality(sql.whereClause)
            return SelectInto(sql.fromClause[0].relname, [t.val.fields[-1].sval for t in sql.targetList],
                              targets, col, key)
        if isinstance(sql, A.UpdateStmt):
            col, key = key_equality(sql.whereClause)
            return Update(sql.relation.relname, [(t.name, to_expr(t.val)) for t in sql.targetList], col, key)
        raise Unsupported(f"SQL tertanam tidak didukung: {type(sql).__name__}")
    if kind == "PLpgSQL_stmt_if":
        branches = [(expr_from_sql(s["cond"]["PLpgSQL_expr"]["query"]), [to_stmt(x) for x in s.get("then_body", [])])]
        for e in s.get("elsif_list", []):
            _, ei = unwrap(e)
            branches.append((expr_from_sql(ei["cond"]["PLpgSQL_expr"]["query"]), [to_stmt(x) for x in ei.get("stmts", [])]))
        return If(branches, [to_stmt(x) for x in s.get("else_body", [])])
    if kind == "PLpgSQL_stmt_raise":
        if s.get("elog_level") != 21:  # 21 = ERROR (RAISE EXCEPTION)
            raise Unsupported("Hanya RAISE EXCEPTION yang didukung")
        return Raise(s["message"], [expr_from_sql(p["PLpgSQL_expr"]["query"]) for p in s.get("params", [])])
    if kind == "PLpgSQL_stmt_assign":
        return Assign(s["varname"], expr_from_sql(s["expr"]["PLpgSQL_expr"]["query"].split(":=", 1)[-1]))
    if kind == "PLpgSQL_stmt_return":
        return None
    raise Unsupported(f"Pernyataan tidak didukung: {kind}")


def parse_procedure(sql):
    sig = parse_sql(sql)[0].stmt
    if not isinstance(sig, A.CreateFunctionStmt) or not sig.is_procedure:
        raise Unsupported("Input harus CREATE PROCEDURE")
    params = [(p.name, type_name(p.argType)) for p in sig.parameters]
    fn = pglast.parse_plpgsql(sql)[0]["PLpgSQL_function"]
    names = {p for p, _ in params}
    vars_ = []
    for d in fn["datums"]:
        kind, v = unwrap(d)
        if kind == "PLpgSQL_var" and v["refname"] not in names and v["refname"] != "found":
            default = v.get("default_val")
            vars_.append((v["refname"], v["datatype"]["PLpgSQL_type"]["typname"],
                          expr_from_sql(default["PLpgSQL_expr"]["query"]) if default else None))
    body = [x for x in (to_stmt(n) for n in fn["action"]["PLpgSQL_stmt_block"]["body"]) if x is not None]
    return Procedure(sig.funcname[-1].sval, params, vars_, body)


# ============================== [3] Generator Java ==============================
JTYPE = {"text": "String", "varchar": "String", "numeric": "BigDecimal", "int4": "Integer",
         "integer": "Integer", "bool": "Boolean", "boolean": "Boolean"}
HDR = "// DIHASILKAN OTOMATIS oleh migrator (spike) - jangan diedit manual.\n"


def camel(s, upper=False):
    parts = s.lower().split("_")
    if len(parts) > 1 and parts[0] in ("p", "v"):
        parts = parts[1:]
    out = parts[0] + "".join(p.title() for p in parts[1:])
    return out[0].upper() + out[1:] if upper else out


def gen_entity(t):
    cls = camel(t.name, True)
    f = "\n".join(f"    private {JTYPE[c.type]} {camel(c.name)};" for c in t.columns)
    ctor_args = ", ".join(f"{JTYPE[c.type]} {camel(c.name)}" for c in t.columns)
    ctor_body = "\n".join(f"        this.{camel(c.name)} = {camel(c.name)};" for c in t.columns)
    acc = "\n".join(
        f"    public {JTYPE[c.type]} get{camel(c.name, True)}() {{ return {camel(c.name)}; }}\n"
        f"    public void set{camel(c.name, True)}({JTYPE[c.type]} v) {{ this.{camel(c.name)} = v; }}"
        for c in t.columns)
    copy = ", ".join(camel(c.name) for c in t.columns)
    show = " + \", \" + ".join(f'"{c.name}=" + {camel(c.name)}' for c in t.columns)
    return f"""{HDR}package com.p2p.domain;

import java.math.BigDecimal;

/** Entity dari tabel `{t.name}`. */
public class {cls} {{
{f}

    public {cls}({ctor_args}) {{
{ctor_body}
    }}

{acc}

    public {cls} copy() {{ return new {cls}({copy}); }}

    @Override public String toString() {{ return "{cls}[" + {show} + "]"; }}
}}
"""


def pk_of(t):
    return next(c for c in t.columns if c.pk)


def gen_repo(t):
    cls = camel(t.name, True)
    return f"""{HDR}package com.p2p.repository;

import java.util.List;
import java.util.Optional;

import com.p2p.domain.{cls};

public interface {cls}Repository {{
    void save({cls} entity);
    Optional<{cls}> findById(String id);
    List<{cls}> findAll();
}}
"""


def gen_inmemory(t):
    cls = camel(t.name, True)
    pk = camel(pk_of(t).name, True)
    nn = [c for c in t.columns if c.not_null]
    checks = "\n".join(
        f'        if (e.get{camel(c.name, True)}() == null) throw new ProcedureException("23502", '
        f'"null value in column \\"{c.name}\\" of relation \\"{t.name}\\" violates not-null constraint");'
        for c in nn)
    scale = "\n".join(
        f"        if (e.get{camel(c.name, True)}() != null) e.set{camel(c.name, True)}(Sql.scale(e.get{camel(c.name, True)}(), 2));"
        for c in t.columns if c.type == "numeric")
    return f"""{HDR}package com.p2p.infrastructure;

import java.util.*;

import com.p2p.domain.{cls};
import com.p2p.repository.{cls}Repository;
import com.p2p.support.*;

/** Repository berbasis Map (gaya semester lalu) + snapshot untuk rollback. */
public class InMemory{cls}Repository implements {cls}Repository, Snapshotable {{
    private Map<String, {cls}> data = new HashMap<>();
    private Map<String, {cls}> snapshot;

    @Override public void save({cls} e) {{
{checks}
{scale}
        data.put(e.get{pk}(), e.copy());
    }}
    @Override public Optional<{cls}> findById(String id) {{
        return Optional.ofNullable(data.get(id)).map({cls}::copy);
    }}
    @Override public List<{cls}> findAll() {{
        List<{cls}> out = new ArrayList<>();
        data.values().forEach(e -> out.add(e.copy()));
        return out;
    }}
    @Override public void takeSnapshot() {{
        snapshot = new HashMap<>();
        data.forEach((k, v) -> snapshot.put(k, v.copy()));
    }}
    @Override public void restoreSnapshot() {{ data = snapshot; }}
}}
"""


class ServiceGen:
    def __init__(self, proc, tables):
        self.p, self.tables = proc, tables
        self.repos = sorted({s.table for s in self.walk(proc.body) if isinstance(s, (SelectInto, Update))})
        self.row_ctx = None   # tabel aktif saat menerjemahkan SET di UPDATE
        self.tmp = 0

    def walk(self, stmts):
        for s in stmts:
            yield s
            if isinstance(s, If):
                for _, b in s.branches:
                    yield from self.walk(b)
                yield from self.walk(s.else_body)

    def e(self, x):
        if isinstance(x, Lit):
            if x.kind == "null":
                return "null"
            return f'"{x.value}"' if x.kind == "text" else f'new BigDecimal("{x.value}")'
        if isinstance(x, Found):
            return "found"
        if isinstance(x, Ref):
            if self.row_ctx and x.name in {c.name for c in self.tables[self.row_ctx].columns}:
                return f"row.get{camel(x.name, True)}()"
            return camel(x.name)
        if isinstance(x, Not):
            return f"Sql.not({self.e(x.arg)})"
        if isinstance(x, IsNull):
            return f"Sql.isNull({self.e(x.arg)}, {str(x.negated).lower()})"
        fn = {"+": "add", "-": "sub", "*": "mul", "/": "div", "=": "eq", "<>": "ne", "!=": "ne",
              "<": "lt", "<=": "le", ">": "gt", ">=": "ge", "AND": "and", "OR": "or"}[x.op]
        return f"Sql.{fn}({self.e(x.left)}, {self.e(x.right)})"

    def s(self, st, ind):
        p = "    " * ind
        if isinstance(st, SelectInto):
            cls = camel(st.table, True)
            self.tmp += 1
            r = f"r{self.tmp}"
            out = f"{p}Optional<{cls}> {r} = {camel(st.table)}Repository.findById({self.e(st.key_expr)});\n"
            out += f"{p}found = {r}.isPresent();\n"
            for col, tgt in zip(st.columns, st.targets):
                out += f"{p}{camel(tgt)} = {r}.map({cls}::get{camel(col, True)}).orElse(null);\n"
            return out
        if isinstance(st, Update):
            cls = camel(st.table, True)
            out = f"{p}{{ // UPDATE {st.table} ... WHERE {st.key_col} = ...\n"
            out += f"{p}    Optional<{cls}> hit = {camel(st.table)}Repository.findById({self.e(st.key_expr)});\n"
            out += f"{p}    found = hit.isPresent();\n{p}    if (found) {{\n{p}        {cls} row = hit.get();\n"
            self.row_ctx = st.table
            vals = [(c, self.e(v)) for c, v in st.sets]   # semua RHS dievaluasi dulu (semantik SQL)
            self.row_ctx = None
            for i, (c, v) in enumerate(vals):
                out += f"{p}        var set{i} = {v};\n"
            for i, (c, _) in enumerate(vals):
                out += f"{p}        row.set{camel(c, True)}(set{i});\n"
            out += f"{p}        {camel(st.table)}Repository.save(row);\n{p}    }}\n{p}}}\n"
            return out
        if isinstance(st, Raise):
            args = "".join(", " + self.e(a) for a in st.params)
            return f'{p}throw new ProcedureException("P0001", Sql.format("{st.message}"{args}));\n'
        if isinstance(st, Assign):
            return f"{p}{camel(st.target)} = {self.e(st.value)};\n"
        if isinstance(st, If):
            out = ""
            for i, (c, body) in enumerate(st.branches):
                kw = "if" if i == 0 else "} else if"
                out += f"{p}{kw} (Sql.isTrue({self.e(c)})) {{\n" + "".join(self.s(b, ind + 1) for b in body)
            if st.else_body:
                out += f"{p}}} else {{\n" + "".join(self.s(b, ind + 1) for b in st.else_body)
            return out + f"{p}}}\n"
        raise Unsupported(type(st).__name__)

    def gen(self):
        cls = camel(self.p.name, True) + "Service"
        fields = "\n".join(f"    private final {camel(t, True)}Repository {camel(t)}Repository;" for t in self.repos)
        ctor_args = ", ".join(f"{camel(t, True)}Repository {camel(t)}Repository" for t in self.repos)
        ctor_body = "\n".join(f"        this.{camel(t)}Repository = {camel(t)}Repository;" for t in self.repos)
        args = ", ".join(f"{JTYPE[t]} {camel(n)}" for n, t in self.p.params)
        decl = "".join(f"            {JTYPE[t]} {camel(n)} = {self.e(d) if d else 'null'};\n" for n, t, d in self.p.vars)
        body = "".join(self.s(x, 3) for x in self.p.body)
        imports = "".join(f"import com.p2p.domain.{camel(t, True)};\nimport com.p2p.repository.{camel(t, True)}Repository;\n" for t in self.repos)
        return f"""{HDR}package com.p2p.service;

import java.math.BigDecimal;
import java.util.Optional;

{imports}import com.p2p.support.*;

/** Diterjemahkan dari stored procedure `{self.p.name}`. Satu pemanggilan = satu transaksi. */
public class {cls} {{
{fields}
    private final UnitOfWork unitOfWork;

    public {cls}({ctor_args}, UnitOfWork unitOfWork) {{
{ctor_body}
        this.unitOfWork = unitOfWork;
    }}

    public void execute({args}) {{
        unitOfWork.run(() -> {{
            boolean found = false;
{decl}
{body}        }});
    }}
}}
"""


def main(schema_path, proc_path, out_dir):
    tables = parse_schema(Path(schema_path).read_text())
    proc = parse_procedure(Path(proc_path).read_text())
    out = Path(out_dir) / "com" / "p2p"
    files = {}
    for t in tables.values():
        files[f"domain/{camel(t.name, True)}.java"] = gen_entity(t)
        files[f"repository/{camel(t.name, True)}Repository.java"] = gen_repo(t)
        files[f"infrastructure/InMemory{camel(t.name, True)}Repository.java"] = gen_inmemory(t)
    files[f"service/{camel(proc.name, True)}Service.java"] = ServiceGen(proc, tables).gen()
    for rel, src in files.items():
        f = out / rel
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(src)
    print(f"IR procedure: {proc.name}, {len(proc.params)} parameter, {len(proc.vars)} variabel, "
          f"{sum(1 for _ in ServiceGen(proc, tables).walk(proc.body))} pernyataan")
    print("Dihasilkan:", *sorted(files), sep="\n  ")


if __name__ == "__main__":
    main(*sys.argv[1:4])
