"""Parsing dua tingkat dengan pglast (parser asli PostgreSQL).

Tingkat 1: pglast.parse_plpgsql  -> struktur procedure (DECLARE, IF, FOR, RAISE, ...)
Tingkat 2: pglast.parse_sql      -> DDL, signature, dan setiap SQL/ekspresi yang masih berupa string
Hasilnya digabung menjadi IR (migrator.ir). Apa pun di luar cakupan spec §6.3 -> Unsupported.
"""
from __future__ import annotations

import re
from dataclasses import dataclass

import pglast
from pglast import ast as A
from pglast import parse_sql
from pglast.enums import A_Expr_Kind, BoolExprType, ConstrType, NullTestType, SortByDir

from .ir import (Assign, Binary, Column, ColumnRef, Const, Delete, Expr, FieldRef, ForQuery, Found,
                 FuncCall, If, Insert, IsNull, Param, PgType, Procedure, Raise, Return, SelectInto,
                 Table, Unary, Update, Var, VarRef, Where)


class Unsupported(Exception):
    """Konstruksi di luar cakupan: gagal eksplisit, tidak menebak."""

    def __init__(self, construct: str, line: int | None, detail: str = ""):
        self.construct, self.line, self.detail = construct, line, detail
        where = f" (baris {line})" if line else ""
        super().__init__(f"{construct}{where}" + (f": {detail}" if detail else ""))


TYPE_MAP: dict[str, PgType] = {"text": "text", "varchar": "text", "numeric": "numeric",
                               "int4": "integer", "integer": "integer", "int": "integer",
                               "bool": "boolean", "boolean": "boolean", "date": "date"}
OPS = {"+", "-", "*", "/", "=", "<>", "!=", "<", "<=", ">", ">=", "||"}


def _pg_type(name: str, line: int | None) -> PgType:
    if name not in TYPE_MAP:
        raise Unsupported("tipe data", line, name)
    return TYPE_MAP[name]


# ==================================================================== skema
def parse_schema(sql: str) -> dict[str, Table]:
    tables: dict[str, Table] = {}
    for raw in parse_sql(sql):
        st = raw.stmt
        if not isinstance(st, A.CreateStmt):
            raise Unsupported("DDL", None, type(st).__name__)
        tname = st.relation.relname
        cols, table_pk = [], None
        for el in st.tableElts:
            if isinstance(el, A.Constraint):
                if el.contype == ConstrType.CONSTR_PRIMARY and len(el.keys) == 1:
                    table_pk = el.keys[0].sval
                    continue
                raise Unsupported("constraint tabel", None, f"{tname}: {el.contype.name}")
            kinds = {c.contype for c in (el.constraints or ())}
            extra = kinds - {ConstrType.CONSTR_NOTNULL, ConstrType.CONSTR_PRIMARY}
            if extra:
                raise Unsupported("constraint kolom", None, f"{tname}.{el.colname}: {sorted(k.name for k in extra)}")
            tn = el.typeName
            ptype = _pg_type(tn.names[-1].sval, None)
            prec = scale = None
            if tn.typmods:
                if ptype != "numeric":
                    raise Unsupported("typmod", None, f"{tname}.{el.colname}")
                mods = [m.val.ival for m in tn.typmods]
                prec, scale = mods[0], (mods[1] if len(mods) > 1 else 0)
            pk = ConstrType.CONSTR_PRIMARY in kinds
            cols.append(Column(el.colname, ptype, pk or ConstrType.CONSTR_NOTNULL in kinds, pk, prec, scale))
        if table_pk:
            cols = [Column(c.name, c.type, True, True, c.precision, c.scale) if c.name == table_pk else c for c in cols]
        if sum(c.pk for c in cols) != 1:
            raise Unsupported("primary key", None, f"{tname} harus punya tepat satu kolom PK")
        tables[tname] = Table(tname, tuple(cols))
    return tables


# ==================================================================== ekspresi (tingkat 2)
@dataclass
class Scope:
    variables: dict[str, PgType]
    records: dict[str, tuple[str, tuple[str, ...]]]   # record -> (tabel, kolom terpilih)
    row_table: Table | None = None


def parse_expr_text(text: str, scope: Scope, line: int) -> Expr:
    try:
        stmt = parse_sql(f"SELECT {text}")[0].stmt
    except pglast.parser.ParseError as e:
        raise Unsupported("ekspresi", line, str(e))
    if len(stmt.targetList) != 1 or stmt.fromClause:
        raise Unsupported("ekspresi", line, text)
    return to_expr(stmt.targetList[0].val, scope, line)


def to_expr(n, scope: Scope, line: int) -> Expr:
    if isinstance(n, A.A_Const):
        if n.isnull:
            return Const(None, None)
        v = n.val
        if isinstance(v, A.Integer):
            return Const(str(v.ival), "integer")
        if isinstance(v, A.Float):
            return Const(v.fval, "numeric")
        if isinstance(v, A.String):
            return Const(v.sval, "text")
        if isinstance(v, A.Boolean):
            return Const("true" if v.boolval else "false", "boolean")
    if isinstance(n, A.ColumnRef):
        return _resolve_name(n, scope, line)
    if isinstance(n, A.A_Expr):
        if n.kind != A_Expr_Kind.AEXPR_OP or len(n.name) != 1 or n.name[0].sval not in OPS:
            raise Unsupported("operator", line, getattr(n.kind, "name", str(n.kind)))
        op = "<>" if n.name[0].sval == "!=" else n.name[0].sval
        if n.lexpr is None:
            if op != "-":
                raise Unsupported("operator unary", line, op)
            return Unary("-", to_expr(n.rexpr, scope, line))
        return Binary(op, to_expr(n.lexpr, scope, line), to_expr(n.rexpr, scope, line))
    if isinstance(n, A.BoolExpr):
        if n.boolop == BoolExprType.NOT_EXPR:
            return Unary("NOT", to_expr(n.args[0], scope, line))
        op = "AND" if n.boolop == BoolExprType.AND_EXPR else "OR"
        node = to_expr(n.args[0], scope, line)
        for a in n.args[1:]:
            node = Binary(op, node, to_expr(a, scope, line))
        return node
    if isinstance(n, A.NullTest):
        return IsNull(to_expr(n.arg, scope, line), n.nulltesttype == NullTestType.IS_NOT_NULL)
    if isinstance(n, A.CoalesceExpr):
        return FuncCall("coalesce", tuple(to_expr(a, scope, line) for a in n.args))
    if isinstance(n, A.FuncCall):
        fname = n.funcname[-1].sval
        if fname != "round" or not n.args or len(n.args) > 2 or n.agg_star or n.over:
            raise Unsupported("fungsi", line, f"{fname}()")
        return FuncCall("round", tuple(to_expr(a, scope, line) for a in n.args))
    raise Unsupported("ekspresi", line, type(n).__name__)


def _resolve_name(n: A.ColumnRef, scope: Scope, line: int) -> Expr:
    parts = [f.sval if isinstance(f, A.String) else "*" for f in n.fields]
    if "*" in parts:
        raise Unsupported("SELECT *", line)
    if len(parts) == 2:
        rec, field = parts
        if rec in scope.records:
            if field not in scope.records[rec][1]:
                raise Unsupported("field record", line, f"{rec}.{field} tidak dipilih di query FOR")
            return FieldRef(rec, field)
        if scope.row_table and rec == scope.row_table.name and scope.row_table.column(field):
            return ColumnRef(field)
        raise Unsupported("nama", line, ".".join(parts))
    if len(parts) != 1:
        raise Unsupported("nama", line, ".".join(parts))
    name = parts[0]
    if name == "found":
        return Found()
    is_var = name in scope.variables
    is_col = bool(scope.row_table and scope.row_table.column(name))
    if is_var and is_col:
        raise Unsupported("nama ambigu", line, f"{name} adalah variabel sekaligus kolom")
    if is_var:
        return VarRef(name)
    if is_col:
        return ColumnRef(name)
    raise Unsupported("nama", line, name)


# ==================================================================== SQL tertanam
def _where(node, table: Table, scope: Scope, line: int) -> Where:
    """WHERE harus konjungsi `kolom = ekspresi` (ekspresi tidak boleh merujuk kolom)."""
    if node is None:
        raise Unsupported("WHERE", line, "wajib ada")
    terms = node.args if isinstance(node, A.BoolExpr) and node.boolop == BoolExprType.AND_EXPR else (node,)
    with_cols = Scope(scope.variables, scope.records, table)
    out = []
    for t in terms:
        if not (isinstance(t, A.A_Expr) and t.kind == A_Expr_Kind.AEXPR_OP and t.name[0].sval == "="
                and isinstance(t.lexpr, A.ColumnRef)):
            raise Unsupported("WHERE", line, "harus berbentuk kolom = ekspresi (gabungan AND)")
        col = _column_name(t.lexpr, table, line, "WHERE")
        rhs = to_expr(t.rexpr, with_cols, line)          # nama yang variabel sekaligus kolom -> Unsupported
        if _has_column_ref(rhs):
            raise Unsupported("WHERE", line, "sisi kanan tidak boleh merujuk kolom (harus nilai/variabel)")
        out.append((col, rhs))
    return tuple(out)


def _column_name(ref: A.ColumnRef, table: Table, line: int, where: str) -> str:
    """Kolom tabel target; qualifier (bila ada) harus nama tabel itu sendiri."""
    parts = [f.sval if isinstance(f, A.String) else "*" for f in ref.fields]
    if "*" in parts or len(parts) > 2 or (len(parts) == 2 and parts[0] != table.name):
        raise Unsupported(where, line, f"{'.'.join(parts)} bukan kolom tabel {table.name}")
    if not table.column(parts[-1]):
        raise Unsupported(where, line, f"kolom {parts[-1]} tidak ada di {table.name}")
    return parts[-1]


def _has_column_ref(e) -> bool:
    if isinstance(e, ColumnRef):
        return True
    kids = (e.left, e.right) if isinstance(e, Binary) else (e.arg,) if isinstance(e, (Unary, IsNull)) \
        else e.args if isinstance(e, FuncCall) else ()
    return any(_has_column_ref(k) for k in kids)


def _ret(st) -> bool:
    """RETURNING ada? (PG18: returningClause, versi lama: returningList)."""
    return bool(getattr(st, "returningClause", None) or getattr(st, "returningList", None))


def _single_table(from_clause, tables: dict[str, Table], line: int) -> Table:
    if not from_clause or len(from_clause) != 1 or not isinstance(from_clause[0], A.RangeVar):
        raise Unsupported("FROM", line, "harus tepat satu tabel (tanpa JOIN/subquery)")
    name = from_clause[0].relname
    if name not in tables:
        raise Unsupported("tabel", line, name)
    return tables[name]


def _plain_select(sel, tables, line) -> tuple[Table, tuple[str, ...]]:
    if not isinstance(sel, A.SelectStmt) or sel.withClause or sel.groupClause or sel.havingClause \
            or sel.distinctClause or sel.limitCount or sel.limitOffset or sel.op != 0 or sel.valuesLists:
        raise Unsupported("SELECT", line, "hanya SELECT kolom FROM satu tabel WHERE ...")
    table = _single_table(sel.fromClause, tables, line)
    cols = []
    for t in sel.targetList:
        if not isinstance(t.val, A.ColumnRef) or t.name:
            raise Unsupported("SELECT", line, "daftar kolom harus nama kolom polos")
        cols.append(_column_name(t.val, table, line, "SELECT"))
    return table, tuple(cols)


# ==================================================================== procedure (tingkat 1)
class _ProcParser:
    def __init__(self, sql: str, tables: dict[str, Table], source_file: str):
        self.tables, self.source_file = tables, source_file
        m = re.search(r"\$\$", sql)
        self.base = sql[: m.start()].count("\n") if m else 0   # lineno 1 = baris tempat $$ dibuka

    def line(self, node: dict) -> int | None:
        return self.base + node["lineno"] if "lineno" in node else None

    def parse(self, sql: str) -> Procedure:
        stmts = parse_sql(sql)
        if len(stmts) != 1 or not isinstance(stmts[0].stmt, A.CreateFunctionStmt) or not stmts[0].stmt.is_procedure:
            raise Unsupported("input", None, "harus tepat satu CREATE PROCEDURE")
        sig = stmts[0].stmt
        lang = next((o.arg.sval for o in sig.options if o.defname == "language"), None)
        if lang != "plpgsql":
            raise Unsupported("bahasa", None, str(lang))
        params = []
        for p in sig.parameters or ():
            if p.mode.value not in ("i", "d"):
                raise Unsupported("parameter OUT/INOUT", None, p.name)
            if p.argType.typmods:
                raise Unsupported("parameter bertipe (p,s)", None, p.name)
            if p.defexpr is not None:
                raise Unsupported("DEFAULT parameter", None, p.name)
            params.append(Param(p.name, _pg_type(p.argType.names[-1].sval, None)))
        fn = pglast.parse_plpgsql(sql)[0]["PLpgSQL_function"]
        pnames = {p.name for p in params}
        self.datums = fn["datums"]
        vars_, records = [], []
        variables: dict[str, PgType] = {p.name: p.type for p in params}
        for d in self.datums:
            (kind, v), = d.items()
            if kind == "PLpgSQL_var" and v["refname"] not in pnames and v["refname"] != "found":
                tname = v["datatype"]["PLpgSQL_type"]["typname"]
                if v.get("notnull") or v.get("isconst"):
                    raise Unsupported("variabel NOT NULL/CONSTANT", self.line(v), v["refname"])
                if "(" in tname:
                    raise Unsupported("variabel bertipe (p,s)", self.line(v), v["refname"])
                variables[v["refname"]] = _pg_type(tname, self.line(v))
                vars_.append(v)
            elif kind == "PLpgSQL_rec":
                records.append(v["refname"])
        self.scope = Scope(variables, {})
        parsed_vars = []
        for v in vars_:
            dv = v.get("default_val")
            default = parse_expr_text(dv["PLpgSQL_expr"]["query"], self.scope, self.line(v)) if dv else None
            parsed_vars.append(Var(v["refname"], variables[v["refname"]], default))
        block = fn["action"]["PLpgSQL_stmt_block"]
        if block.get("exceptions"):
            raise Unsupported("EXCEPTION WHEN", self.line(block))
        body = self.stmts(block.get("body", []))
        return Procedure(sig.funcname[-1].sval, tuple(params), tuple(parsed_vars), tuple(records),
                         body, self.source_file)

    def stmts(self, nodes) -> tuple:
        out = []
        for n in nodes:
            s = self.stmt(n)
            if s is not None:
                out.append(s)
        return tuple(out)

    def expr(self, node: dict, line) -> Expr:
        return parse_expr_text(node["PLpgSQL_expr"]["query"], self.scope, line)

    def stmt(self, node):
        (kind, s), = node.items()
        line = self.line(s)
        if kind == "PLpgSQL_stmt_execsql":
            return self.execsql(s, line)
        if kind == "PLpgSQL_stmt_if":
            branches = [(self.expr(s["cond"], line), self.stmts(s.get("then_body", [])))]
            for e in s.get("elsif_list", []):
                (_, ei), = e.items()
                branches.append((self.expr(ei["cond"], self.line(ei)), self.stmts(ei.get("stmts", []))))
            return If(tuple(branches), self.stmts(s.get("else_body", [])), line)
        if kind == "PLpgSQL_stmt_raise":
            if s.get("elog_level") != 21 or s.get("options") or s.get("condname"):
                raise Unsupported("RAISE", line, "hanya RAISE EXCEPTION 'pesan', arg...")
            return Raise(s.get("message", ""), tuple(self.expr(p, line) for p in s.get("params", [])), line)
        if kind == "PLpgSQL_stmt_assign":
            (dkind, d), = self.datums[s["varno"]].items()
            if dkind != "PLpgSQL_var" or d["refname"] not in self.scope.variables:
                raise Unsupported("assignment", line, "target harus variabel skalar")
            m = re.match(r"\s*[\w\"]+\s*(:=|=)", s["expr"]["PLpgSQL_expr"]["query"])
            if not m:
                raise Unsupported("assignment", line, s["expr"]["PLpgSQL_expr"]["query"])
            text = s["expr"]["PLpgSQL_expr"]["query"][m.end():]
            return Assign(d["refname"], parse_expr_text(text, self.scope, line), line)
        if kind == "PLpgSQL_stmt_fors":
            return self.fors(s, line)
        if kind == "PLpgSQL_stmt_return":
            return Return(line) if line is not None else None   # RETURN implisit di akhir diabaikan
        raise Unsupported(kind.replace("PLpgSQL_stmt_", "pernyataan "), line)

    def execsql(self, s, line):
        sql = s["sqlstmt"]["PLpgSQL_expr"]["query"]
        try:
            stmts = parse_sql(sql)
        except pglast.parser.ParseError as e:
            raise Unsupported("SQL", line, str(e))
        if len(stmts) != 1:
            raise Unsupported("SQL", line, "lebih dari satu pernyataan")
        st = stmts[0].stmt
        if isinstance(st, A.SelectStmt):
            if not s.get("into"):
                raise Unsupported("SELECT tanpa INTO", line)
            if s.get("strict"):
                raise Unsupported("SELECT INTO STRICT", line)
            table, cols = _plain_select(st, self.tables, line)
            if st.sortClause:
                raise Unsupported("ORDER BY di SELECT INTO", line)
            where = _where(st.whereClause, table, self.scope, line)
            if len(where) != 1 or where[0][0] != table.pk.name:
                raise Unsupported("SELECT INTO", line, f"WHERE harus {table.pk.name} = <ekspresi>")
            (tkind, target), = s["target"].items()
            targets = tuple(f["name"] for f in target["fields"]) if tkind == "PLpgSQL_row" else (target["refname"],)
            if len(targets) != len(cols):
                raise Unsupported("SELECT INTO", line, "jumlah kolom dan target berbeda")
            return SelectInto(table.name, cols, targets, where[0][1], line)
        if isinstance(st, A.UpdateStmt):
            table = self._table(st.relation, line)
            if st.fromClause or _ret(st) or st.withClause:
                raise Unsupported("UPDATE", line, "FROM/RETURNING/WITH tidak didukung")
            row_scope = Scope(self.scope.variables, self.scope.records, table)
            sets = []
            for t in st.targetList:
                if t.indirection or not table.column(t.name):
                    raise Unsupported("UPDATE", line, f"kolom {t.name}")
                sets.append((t.name, to_expr(t.val, row_scope, line)))
            return Update(table.name, tuple(sets), _where(st.whereClause, table, self.scope, line), line)
        if isinstance(st, A.InsertStmt):
            table = self._table(st.relation, line)
            if not st.cols:
                raise Unsupported("INSERT", line, "daftar kolom wajib disebut")
            if st.onConflictClause or _ret(st) or st.withClause:
                raise Unsupported("INSERT", line, "ON CONFLICT/RETURNING/WITH tidak didukung")
            sel = st.selectStmt
            if not isinstance(sel, A.SelectStmt) or not sel.valuesLists or len(sel.valuesLists) != 1:
                raise Unsupported("INSERT", line, "hanya VALUES satu baris")
            names = [c.name for c in st.cols]
            if any(not table.column(n) for n in names):
                raise Unsupported("INSERT", line, "kolom tidak dikenal")
            vals = [to_expr(v, self.scope, line) for v in sel.valuesLists[0]]
            return Insert(table.name, tuple(zip(names, vals)), line)
        if isinstance(st, A.DeleteStmt):
            table = self._table(st.relation, line)
            if st.usingClause or _ret(st) or st.withClause:
                raise Unsupported("DELETE", line, "USING/RETURNING/WITH tidak didukung")
            return Delete(table.name, _where(st.whereClause, table, self.scope, line), line)
        raise Unsupported("SQL", line, type(st).__name__)

    def _table(self, rv, line) -> Table:
        if rv.relname not in self.tables:
            raise Unsupported("tabel", line, rv.relname)
        return self.tables[rv.relname]

    def fors(self, s, line):
        (vkind, var), = s["var"].items()
        if vkind != "PLpgSQL_rec":
            raise Unsupported("FOR", line, "variabel loop harus record")
        sel = parse_sql(s["query"]["PLpgSQL_expr"]["query"])[0].stmt
        table, cols = _plain_select(sel, self.tables, line)
        where = _where(sel.whereClause, table, self.scope, line)
        sort = sel.sortClause or ()
        if len(sort) != 1 or not isinstance(sort[0].node, A.ColumnRef) \
                or sort[0].node.fields[-1].sval != table.pk.name \
                or sort[0].sortby_dir not in (SortByDir.SORTBY_DEFAULT, SortByDir.SORTBY_ASC):
            raise Unsupported("FOR", line, f"wajib ORDER BY {table.pk.name} (urutan Map harus sama)")
        if table.pk.name not in cols:
            cols = cols + (table.pk.name,)
        rec = var["refname"]
        self.scope.records[rec] = (table.name, cols)
        body = self.stmts(s.get("body", []))
        del self.scope.records[rec]
        return ForQuery(rec, table.name, cols, where, body, line)


def parse_procedure(sql: str, tables: dict[str, Table], source_file: str) -> Procedure:
    try:
        return _ProcParser(sql, tables, source_file).parse(sql)
    except pglast.parser.ParseError as e:
        raise Unsupported("sintaks", None, str(e))
