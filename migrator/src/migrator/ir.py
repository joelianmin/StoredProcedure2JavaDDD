"""IR (Intermediate Representation) milik migrator, independen dari bentuk JSON pglast.

Semua node immutable (frozen dataclass); koleksi berupa tuple supaya IR bisa dibandingkan dan di-hash.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Literal, Union

PgType = Literal["text", "numeric", "integer", "boolean", "date"]


# ---------------------------------------------------------------- skema
@dataclass(frozen=True)
class Column:
    name: str
    type: PgType
    not_null: bool
    pk: bool
    precision: int | None = None
    scale: int | None = None


@dataclass(frozen=True)
class Table:
    name: str
    columns: tuple[Column, ...]

    @property
    def pk(self) -> Column:
        return next(c for c in self.columns if c.pk)

    def column(self, name: str) -> Column | None:
        return next((c for c in self.columns if c.name == name), None)


# ---------------------------------------------------------------- ekspresi
@dataclass(frozen=True)
class Const:
    value: str | None          # None = NULL
    type: PgType | None        # None = NULL tanpa tipe


@dataclass(frozen=True)
class VarRef:
    name: str                  # parameter atau variabel procedure


@dataclass(frozen=True)
class FieldRef:
    record: str                # variabel record FOR (mis. r)
    field: str


@dataclass(frozen=True)
class ColumnRef:
    name: str                  # kolom baris target UPDATE/INSERT/DELETE


@dataclass(frozen=True)
class Found:
    pass


@dataclass(frozen=True)
class Binary:
    op: str                    # + - * / = <> < <= > >= AND OR ||
    left: "Expr"
    right: "Expr"


@dataclass(frozen=True)
class Unary:
    op: str                    # - NOT
    arg: "Expr"


@dataclass(frozen=True)
class IsNull:
    arg: "Expr"
    negated: bool


@dataclass(frozen=True)
class FuncCall:
    name: str                  # round | coalesce
    args: tuple["Expr", ...]


Expr = Union[Const, VarRef, FieldRef, ColumnRef, Found, Binary, Unary, IsNull, FuncCall]
Where = tuple[tuple[str, Expr], ...]   # konjungsi kolom = ekspresi


# ---------------------------------------------------------------- pernyataan
@dataclass(frozen=True)
class Assign:
    target: str
    value: Expr
    line: int


@dataclass(frozen=True)
class If:
    branches: tuple[tuple[Expr, tuple["Stmt", ...]], ...]
    else_body: tuple["Stmt", ...]
    line: int


@dataclass(frozen=True)
class SelectInto:
    table: str
    columns: tuple[str, ...]
    targets: tuple[str, ...]
    key: Expr                  # nilai PK (WHERE <pk> = key)
    line: int


@dataclass(frozen=True)
class Update:
    table: str
    sets: tuple[tuple[str, Expr], ...]
    where: Where
    line: int


@dataclass(frozen=True)
class Insert:
    table: str
    values: tuple[tuple[str, Expr], ...]
    line: int


@dataclass(frozen=True)
class Delete:
    table: str
    where: Where
    line: int


@dataclass(frozen=True)
class Raise:
    message: str
    params: tuple[Expr, ...]
    line: int


@dataclass(frozen=True)
class ForQuery:
    record: str
    table: str
    columns: tuple[str, ...]
    where: Where
    body: tuple["Stmt", ...]
    line: int


@dataclass(frozen=True)
class Return:
    line: int


Stmt = Union[Assign, If, SelectInto, Update, Insert, Delete, Raise, ForQuery, Return]


# ---------------------------------------------------------------- procedure
@dataclass(frozen=True)
class Param:
    name: str
    type: PgType


@dataclass(frozen=True)
class Var:
    name: str
    type: PgType
    default: Expr | None


@dataclass(frozen=True)
class Procedure:
    name: str
    params: tuple[Param, ...]
    vars: tuple[Var, ...]
    records: tuple[str, ...]
    body: tuple[Stmt, ...]
    source_file: str


def walk(stmts) -> "list[Stmt]":
    """Semua pernyataan secara rekursif, urutan kemunculan."""
    out = []
    for s in stmts:
        out.append(s)
        if isinstance(s, If):
            for _, b in s.branches:
                out.extend(walk(b))
            out.extend(walk(s.else_body))
        elif isinstance(s, ForQuery):
            out.extend(walk(s.body))
    return out
