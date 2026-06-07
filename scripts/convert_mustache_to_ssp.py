#!/usr/bin/env python3
"""Convert Mustache templates to Scalate SSP for sql-service-codegen."""

from __future__ import annotations

import sys
from pathlib import Path

LIST_SECTIONS = {
    "members",
    "models",
    "unions",
    "operations",
    "parameters",
    "errors",
    "resultFields",
    "bindParameters",
    "sqliteClassRowFactories",
    "jsonStructures",
    "jsonUnions",
    "jsonStructuresColPostgres",
    "jsonUnionsColPostgres",
    "jsonStructuresColSqlite",
    "jsonUnionsColSqlite",
    "transactionCommitInTxAssertions",
    "transactionCommitAfterAssertions",
    "resultAssertions",
    "updatedResultAssertions",
}

SINGULAR = {
    "members": "member",
    "models": "model",
    "unions": "union",
    "operations": "operation",
    "parameters": "parameter",
    "errors": "error",
    "resultFields": "resultField",
    "bindParameters": "bindParameter",
    "sqliteClassRowFactories": "sqliteClassRowFactory",
    "jsonStructures": "jsonStructure",
    "jsonUnions": "jsonUnion",
    "jsonStructuresColPostgres": "jsonStructureColPostgres",
    "jsonUnionsColPostgres": "jsonUnionColPostgres",
    "jsonStructuresColSqlite": "jsonStructureColSqlite",
    "jsonUnionsColSqlite": "jsonUnionColSqlite",
    "transactionCommitInTxAssertions": "assertion",
    "transactionCommitAfterAssertions": "assertion",
    "resultAssertions": "assertion",
    "updatedResultAssertions": "assertion",
}

ITEM_FIELDS: dict[str, set[str]] = {
    "member": {
        "name",
        "typeName",
        "languageTypeName",
        "optional",
        "required",
        "isStructure",
        "structureClassName",
        "variantClassName",
        "last",
        "first",
        "only",
    },
    "model": {
        "name",
        "className",
        "shapeId",
        "namespace",
        "members",
        "memberLines",
        "hasMembers",
        "last",
    },
    "union": {
        "name",
        "className",
        "shapeId",
        "namespace",
        "members",
        "unionTypeAlias",
        "last",
    },
    "operation": {
        "name",
        "methodName",
        "operationShapeId",
        "parameters",
        "hasParameters",
        "hasOutput",
        "outputClassName",
        "outputTypeName",
        "errors",
        "hasErrors",
        "responseUnion",
        "responseType",
        "hasSql",
        "queryKind",
        "sqlStatement",
        "tableName",
        "bindParameters",
        "hasBindParameters",
        "executionMode",
        "isRowcountExecution",
        "outputKind",
        "isInsertScalar",
        "isInsertStructure",
        "isBooleanMutation",
        "isSelectOne",
        "canUseClassRow",
        "usesDictRowFactory",
        "classRowFactoryName",
        "returningColumnIndex",
        "resultFields",
        "hasResultFields",
        "txI8",
        "txI12",
        "txI16",
        "txI20",
        "last",
    },
    "parameter": {
        "name",
        "typeName",
        "languageTypeName",
        "optional",
        "required",
        "isStructure",
        "structureClassName",
        "last",
    },
    "error": {"name", "className", "shapeId", "last"},
    "resultField": {
        "fieldName",
        "columnName",
        "columnNameLiteral",
        "columnIndex",
        "languageTypeName",
        "isJson",
        "jsonReadExpression",
        "jsonReadExpressionCol",
        "jsonReadExpressionNamedRowCol",
        "rowReader",
        "rowReaderCol",
        "last",
    },
    "bindParameter": {"memberName", "bindExpression", "first", "last", "only"},
    "sqliteClassRowFactory": {"outputClassName", "classRowFactoryName", "last"},
    "jsonStructure": {"className", "languageTypeName", "members", "last"},
    "jsonUnion": {"className", "languageTypeName", "discriminatorKeys", "last"},
    "jsonStructureColPostgres": {"className", "languageTypeName", "members", "last"},
    "jsonUnionColPostgres": {"className", "languageTypeName", "discriminatorKeys", "last"},
    "jsonStructureColSqlite": {"className", "languageTypeName", "members", "last"},
    "jsonUnionColSqlite": {"className", "languageTypeName", "discriminatorKeys", "last"},
    "assertion": {"line", "last"},
}


class Converter:
    def __init__(self) -> None:
        self.context_stack: list[str] = []

    def ref(self, name: str) -> str:
        for item in reversed(self.context_stack):
            if name in ITEM_FIELDS.get(item, set()):
                return f'${{{item}("{name}")}}'
        return f'${{attrs("{name}")}}'

    def bool_ref(self, name: str) -> str:
        for item in reversed(self.context_stack):
            if name in ITEM_FIELDS.get(item, set()):
                return f'{item}("{name}").asInstanceOf[Boolean]'
        return f'attrs.get("{name}").exists(isTruthy)'

    def list_source(self, name: str) -> str:
        for item in reversed(self.context_stack):
            if name in ITEM_FIELDS.get(item, set()):
                return f'{item}("{name}")'
        return f'attrs("{name}")'

    def include_stmt(self, partial: str) -> str:
        if self.context_stack:
            overlay = self.context_stack[-1]
            return (
                f'<% render("{partial}", '
                f'Map("attrs" -> (attrs ++ {overlay}.asInstanceOf[Map[String, Any]]))) %>'
            )
        return f'<% include("{partial}") %>'

    def convert(self, text: str) -> str:
        return self.parse(text, 0)[0]

    def parse(self, text: str, index: int) -> tuple[str, int]:
        out: list[str] = []
        length = len(text)
        while index < length:
            start = text.find("{{", index)
            if start == -1:
                out.append(text[index:])
                break
            out.append(text[index:start])
            if start + 2 < length and text[start + 2] == "#":
                index = self.parse_section(text, start, out, inverted=False)
            elif start + 2 < length and text[start + 2] == "^":
                index = self.parse_section(text, start, out, inverted=True)
            elif start + 2 < length and text[start + 2] == ">":
                end = text.find("}}", start)
                if end == -1:
                    raise ValueError(f"Unclosed partial at {start}")
                partial = text[start + 3 : end].strip().removesuffix(".mustache")
                out.append(self.include_stmt(partial))
                index = end + 2
            elif start + 2 < length and text[start + 2] == "/":
                return ("".join(out), start)
            elif text.startswith("{{{", start):
                end = text.find("}}}", start)
                if end == -1:
                    raise ValueError(f"Unclosed unescaped tag at {start}")
                name = text[start + 3 : end].strip()
                out.append(self.ref(name))
                index = end + 3
            else:
                end = text.find("}}", start)
                if end == -1:
                    raise ValueError(f"Unclosed tag at {start}")
                raw = text[start + 2 : end].strip()
                if raw == "newline":
                    out.append("${\"\\n\"}")
                else:
                    out.append(self.ref(raw))
                index = end + 2
        return ("".join(out), index)

    def parse_section(self, text: str, start: int, out: list[str], inverted: bool) -> int:
        end_name = text.find("}}", start)
        if end_name == -1:
            raise ValueError(f"Unclosed section at {start}")
        name = text[start + 3 : end_name].strip()
        body_start = end_name + 2

        if name in LIST_SECTIONS:
            item = SINGULAR[name]
            list_source = self.list_source(name)
            self.context_stack.append(item)
            converted_body, close_start = self.parse(text, body_start)
            self.context_stack.pop()
        elif inverted:
            converted_body, close_start = self.parse(text, body_start)
        else:
            converted_body, close_start = self.parse(text, body_start)

        close_end = text.find("}}", close_start)
        if close_end == -1 or text[close_start + 2 : close_end].strip() != f"/{name}":
            raise ValueError(f"Unclosed section {name}")
        index = close_end + 2

        if name in LIST_SECTIONS:
            item = SINGULAR[name]
            out.append(
                f"<% for ({item} <- {list_source}.asInstanceOf[List[Map[String, Any]]]) {{ %>{converted_body}<% }} %>"
            )
        elif inverted:
            out.append(f"<% if (!{self.bool_ref(name)}) {{ %>{converted_body}<% }} %>")
        else:
            out.append(f"<% if ({self.bool_ref(name)}) {{ %>{converted_body}<% }} %>")
        return index


def convert_file(source: Path, target: Path) -> None:
    converted = Converter().convert(source.read_text(encoding="utf-8"))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(converted, encoding="utf-8")


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print("Usage: convert_mustache_to_ssp.py <root-directory>", file=sys.stderr)
        return 1
    root = Path(argv[1])
    for mustache in sorted(root.rglob("*.mustache")):
        convert_file(mustache, mustache.with_suffix(".ssp"))
        print(f"converted {mustache.relative_to(root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
