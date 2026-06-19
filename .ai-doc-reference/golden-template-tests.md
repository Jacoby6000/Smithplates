# Golden Template Tests

Read this when adding or updating SSP codegen golden fixtures, refreshing
expected output, or extending Python DB backend test coverage.

## Layout

SSP codegen template golden tests live under
`templates/<language>/tests/<test-name>/`:

* `smithy/smithy-files.smithy`
* `smithy-build.json`
* Golden files under `expected/` with `src/<service-type>/...`,
  `test/<service-type>/<implementation>/...`, and optional
  `expected/src/<service-type>/<implementation>/unsupported.md`

## Test suite

The single concrete
[`CodegenTemplateTestSuite`](../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/codegentest/CodegenTemplateTestSuite.scala)
covers every variant. Add a
[`CodegenTemplateVariant`](../modules/smithplates-sql-service-renderer/src/test/scala/com/jacoby6000/smithplates/sql/service/renderer/codegentest/CodegenTemplateVariant.scala)
(grouped by `languageId`) to extend coverage rather than subclassing.

Renderer golden tests compare rendered output only via `CodegenTemplateTestSuite`.

## Refresh golden files

```bash
sbtn 'generateGoldenTemplatesFor python <case-name> ...'
```

Generator:
[`SmithplatesGenerators`](../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/generators/SmithplatesGenerators.scala)
on `smithplatesPlugin`.

## Python integration tests

Execute generated integration tests (ruff, mypy, pytest) from
`templates/<language>/tests/<case>/expected/` via
[`language-test-harnesses/<language>/`](../language-test-harnesses/). Pytest temp
output lives under `target/language-test-harnesses/`.

### `@sqlService` Python DB backends

Golden-test shared `db/model/*_models.py` and `db/*_protocol.py` once under
`expected/src/db/`, plus per-implementation `*_aiosqlite.py` / `*_psycopg.py` and
`test_*_derived_sql.py` under `expected/test/db/` when derived insert +
select-one exist.

* Bundled `*_psycopg.py` uses per-cursor `row_factory`
* Bundled `*_aiosqlite.py` uses mapper-style row mapping
  (`_Type_row_factory(cursor, row)` / `_as_sqlite_named_row(cursor, row)`) and
  does **not** assign `connection.row_factory` (see
  [`docs/usage/sql-plugin.md`](../docs/usage/sql-plugin.md))
* Every generated service method takes an optional keyword-only
  `transaction: T | None = None` on the generic
  `{{serviceClassName}}ServiceProtocol[T]`; dialect implementations bind `T`
  (`psycopg.AsyncTransaction`, `aiosqlite.Connection`)
* Bundled `sqlite_transaction_run.py` / `psycopg_transaction_run.py` expose
  `run(connection, transaction, execute)` so service methods only supply an
  `execute` lambda
* Generated `test_*_derived_sql.py` integration tests include
  `test_derived_sql_methods_transaction_commit` and
  `test_derived_sql_methods_transaction_rollback` (caller-managed `transaction=`
  handle) in addition to the CRUD lifecycle test
