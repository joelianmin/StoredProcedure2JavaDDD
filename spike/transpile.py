"""SPIKE (sekali pakai): PL/SQL procedure -> Java Spring Boot service.

Alur: parse (Lark) -> AST sederhana (dict) -> generator Java + laporan migrasi.
"""
import json
import sys
from pathlib import Path

from lark import Lark, Transformer, Token

HERE = Path(__file__).parent
parser = Lark((HERE / "plsql.lark").read_text(), start="start", parser="earley")


# ---------- 1. Parse tree -> AST (dict biasa, mudah dibaca/di-dump JSON) ----------
class ToAst(Transformer):
    def start(self, c): return c[0]
    def procedure(self, c):
        name, params, decls, body, *rest = c
        exc = next((r for r in rest if isinstance(r, list)), [])
        return {"kind": "procedure", "name": str(name), "params": params,
                "decls": decls, "body": body, "exceptions": exc}
    def params(self, c): return c
    def param(self, c): return {"name": str(c[0]), "mode": " ".join(str(c[1]).upper().split()), "type": str(c[2]).upper()}
    def decls(self, c): return c
    def decl(self, c): return {"name": str(c[0]), "type": str(c[1]).upper(), "init": c[2] if len(c) > 2 else None}
    def stmts(self, c): return c
    def assign(self, c): return {"kind": "assign", "target": str(c[0]), "value": c[1]}
    def if_stmt(self, c):
        cond, then, *rest = c
        branches = [(cond, then)] + [r for r in rest if isinstance(r, tuple)]
        els = next((r["else"] for r in rest if isinstance(r, dict)), None)
        return {"kind": "if", "branches": branches, "else": els}
    def elsif(self, c): return (c[0], c[1])
    def else_(self, c): return {"else": c[0]}
    def names(self, c): return [str(x) for x in c]
    def select_into(self, c):
        return {"kind": "select_into", "columns": c[0], "into": c[1], "table": str(c[2]), "where": c[3]}
    def update_stmt(self, c):
        return {"kind": "update", "table": str(c[0]), "set": c[1:-1], "where": c[-1]}
    def set_item(self, c): return (str(c[0]), c[1])
    def condition(self, c): return (str(c[0]), c[1])
    def commit(self, c): return {"kind": "commit"}
    def null_stmt(self, c): return {"kind": "null"}
    def exception_block(self, c): return c
    def handler(self, c): return {"exception": str(c[0]).upper(), "body": c[1]}
    def num(self, c): return {"e": "num", "v": str(c[0])}
    def neg_num(self, c): return {"e": "num", "v": "-" + str(c[0])}
    def str(self, c): return {"e": "str", "v": str(c[0])[1:-1]}
    def var(self, c): return {"e": "var", "v": str(c[0])}
    def comparison(self, c): return {"e": "bin", "op": str(c[1]), "l": c[0], "r": c[2]}
    def _chain(self, c):
        node = c[0]
        for i in range(1, len(c), 2):
            node = {"e": "bin", "op": str(c[i]), "l": node, "r": c[i + 1]}
        return node
    sum = _chain
    product = _chain


# ---------- 2. Generator Java ----------
def camel(name, upper=False):
    parts = name.lower().split("_")
    for prefix in ("p", "v"):  # buang prefix konvensi PL/SQL
        if len(parts) > 1 and parts[0] == prefix:
            parts = parts[1:]
    s = parts[0] + "".join(p.title() for p in parts[1:])
    return s[0].upper() + s[1:] if upper else s

JTYPE = {"NUMBER": "BigDecimal", "INTEGER": "BigDecimal", "VARCHAR2": "String", "DATE": "LocalDate", "BOOLEAN": "boolean"}
CMP = {"<=": "<= 0", ">=": ">= 0", "<": "< 0", ">": "> 0", "=": "== 0", "<>": "!= 0", "!=": "!= 0"}
ARITH = {"+": "add", "-": "subtract", "*": "multiply", "/": "divide"}


class JavaGen:
    def __init__(self, ast):
        self.ast = ast
        self.notes = []  # catatan migrasi untuk laporan
        self.types = {p["name"]: p["type"] for p in ast["params"]}
        self.types.update({d["name"]: d["type"] for d in ast["decls"]})
        self.out_params = [p for p in ast["params"] if "OUT" in p["mode"]]

    def expr(self, e):
        k = e["e"]
        if k == "num":
            return f'new BigDecimal("{e["v"]}")' if e["v"] not in ("0", "1") else f'BigDecimal.{"ZERO" if e["v"] == "0" else "ONE"}'
        if k == "str":
            return f'"{e["v"]}"'
        if k == "var":
            return camel(e["v"])
        if e["op"] in CMP:
            return f'{self.expr(e["l"])}.compareTo({self.expr(e["r"])}) {CMP[e["op"]]}'
        if e["op"] == "/":
            self.notes.append("Pembagian BigDecimal butuh scale & RoundingMode eksplisit (PL/SQL NUMBER presisi 38).")
            return f'{self.expr(e["l"])}.divide({self.expr(e["r"])}, 10, RoundingMode.HALF_UP)'
        return f'{self.expr(e["l"])}.{ARITH[e["op"]]}({self.expr(e["r"])})'

    def stmts(self, stmts, ind):
        return "".join(self.stmt(s, ind) for s in stmts)

    def stmt(self, s, ind):
        p = "    " * ind
        k = s["kind"]
        if k == "assign":
            return f'{p}{camel(s["target"])} = {self.expr(s["value"])};\n'
        if k == "if":
            out = ""
            for i, (cond, body) in enumerate(s["branches"]):
                kw = "if" if i == 0 else "} else if"
                out += f"{p}{kw} ({self.expr(cond)}) {{\n{self.stmts(body, ind + 1)}"
            if s["else"]:
                out += f"{p}}} else {{\n{self.stmts(s['else'], ind + 1)}"
            return out + f"{p}}}\n"
        if k == "select_into":
            self.notes.append(f"SELECT INTO dari `{s['table']}` -> method repository `find{camel(s['table'], True)}`; NO_DATA_FOUND -> EmptyResultDataAccessException.")
            row = camel(s["table"]) + "Row"
            out = f'{p}var {row} = repository.find{camel(s["table"], True)}({self.expr(s["where"][1])});\n'
            for col, var in zip(s["columns"], s["into"]):
                out += f'{p}{camel(var)} = {row}.{camel(col)}();\n'
            return out
        if k == "update":
            self.notes.append(f"UPDATE `{s['table']}` -> method repository `update{camel(s['table'], True)}` (SQL tetap, hanya dipindah).")
            args = ", ".join([self.expr(s["where"][1])] + [self.expr(v) for _, v in s["set"]])
            return f'{p}repository.update{camel(s["table"], True)}({args});\n'
        if k == "commit":
            self.notes.append("COMMIT eksplisit dihapus -> diganti @Transactional pada method (batas transaksi pindah ke service).")
            return f"{p}// COMMIT -> ditangani @Transactional\n"
        if k == "null":
            return f"{p}// NULL;\n"
        raise ValueError(k)

    def service(self):
        a = self.ast
        cls = camel(a["name"], True) + "Service"
        result = camel(a["name"], True) + "Result"
        ins = [p for p in a["params"] if p["mode"] == "IN"]
        sig = ", ".join(f'{JTYPE[p["type"]]} {camel(p["name"])}' for p in ins)
        body = ""
        for p in self.out_params:
            body += f'        {JTYPE[p["type"]]} {camel(p["name"])} = null;\n'
        for d in a["decls"]:
            init = self.expr(d["init"]) if d["init"] else "null"
            body += f'        {JTYPE[d["type"]]} {camel(d["name"])} = {init};\n'
        body += "\n"
        main = self.stmts(a["body"], 3 if a["exceptions"] else 2)
        ret_fields = ", ".join(camel(p["name"]) for p in self.out_params)
        if a["exceptions"]:
            body += "        try {\n" + main + "        }"
            for h in a["exceptions"]:
                jex = {"NO_DATA_FOUND": "EmptyResultDataAccessException"}.get(h["exception"], "RuntimeException")
                if jex == "RuntimeException":
                    self.notes.append(f"⚠ Exception {h['exception']} belum dipetakan, perlu dicek manual.")
                body += f" catch ({jex} e) {{\n{self.stmts(h['body'], 3)}        }}"
            body += "\n"
        else:
            body += main
        body += f"        return new {result}({ret_fields});\n"
        result_fields = ", ".join(f'{JTYPE[p["type"]]} {camel(p["name"])}' for p in self.out_params)

        return f"""package id.bank.modernisasi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hasil generate otomatis dari PL/SQL procedure `{a["name"]}`. */
@Service
public class {cls} {{

    public record {result}({result_fields}) {{}}

    private final {camel(a["name"], True)}Repository repository;

    public {cls}({camel(a["name"], True)}Repository repository) {{
        this.repository = repository;
    }}

    @Transactional
    public {result} execute({sig}) {{
{body}    }}
}}
"""


def walk(stmts):
    for s in stmts:
        yield s
        if s["kind"] == "if":
            for _, b in s["branches"]:
                yield from walk(b)
            if s["else"]:
                yield from walk(s["else"])


def report(ast, notes):
    all_stmts = list(walk(ast["body"])) + [x for h in ast["exceptions"] for x in walk(h["body"])]
    tables = sorted({s["table"] for s in all_stmts if s["kind"] in ("select_into", "update")})
    branches = sum(len(s["branches"]) + (1 if s["else"] else 0) for s in all_stmts if s["kind"] == "if")
    lines = [f"# Laporan migrasi: {ast['name']}", "",
             f"- Parameter: {len(ast['params'])} ({', '.join(p['name'] + ' ' + p['mode'] for p in ast['params'])})",
             f"- Variabel lokal: {len(ast['decls'])}",
             f"- Tabel disentuh: {', '.join(tables)}",
             f"- Jumlah cabang IF/ELSIF/ELSE: {branches} (kandidat jumlah minimum test case)",
             "", "## Catatan transformasi"]
    lines += [f"- {n}" for n in dict.fromkeys(notes)]
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    src = Path(sys.argv[1])
    out = HERE / "output"
    tree = parser.parse(src.read_text())
    ast = ToAst().transform(tree)
    (out / "ast.json").write_text(json.dumps(ast, indent=2, ensure_ascii=False, default=list))
    gen = JavaGen(ast)
    java = gen.service()
    (out / f"{camel(ast['name'], True)}Service.java").write_text(java)
    (out / "laporan.md").write_text(report(ast, gen.notes))
    print(java)
    print(report(ast, gen.notes))
