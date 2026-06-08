# Python codegen golden tests

Each subdirectory is one test case discovered by [`SqlServiceCodegenTemplateTestSuite`](../../../modules/smithy-sql-service-renderer/src/test/scala/com/jacoby6000/smithy/stache/sql/SqlServiceCodegenTemplateTestSuite.scala). Name cases after the behavior under test (kebab-case), not fixture entities — for example `sql-json-structs-containing-unions`, not `shipment-json-crud`.

## Current cases

| Case | Exercises |
|------|-----------|
| `sql-derived-crud-auto-managed-columns` | `@sqlDeriveInsert` / `@sqlDeriveSelectOne` / `@sqlDeriveUpdate` / `@sqlDeriveDelete`, `@sqlAutoUuid`, `@sqlCreatedTimestamp`, `@sqlUpdatedTimestamp` |
| `sql-json-structs-containing-unions` | `@sqlJson` columns backed by structures and unions, with derived CRUD and JSON bind/read helpers |

Each `@sqlService` case that defines derived insert + select-one operations golden-tests integration tests under both `test/db/sqlite/` (aiosqlite) and `test/db/postgres/` (psycopg). Models and the service `Protocol` are shared once under `src/db/model/` and `src/db/`.

[`SqlServiceCodegenTemplateTestSuite`](../../../modules/smithy-sql-service-renderer/src/test/scala/com/jacoby6000/smithy/stache/sql/SqlServiceCodegenTemplateTestSuite.scala) golden-compares rendered output only. Execute generated integration tests with [`language-test-harnesses/python/run-tests.sh`](../../../language-test-harnesses/python/run-tests.sh).

## Layout

```
<test-name>/
  smithy/
    smithy-files.smithy       # Smithy model for the scenario
  src/                        # generated src artifacts
    <service-type>/           # e.g. db/
      model/                  # shared model artifacts (generated once)
      <service>_protocol.py   # shared service Protocol (generated once)
      <implementation>/       # e.g. sqlite/ or postgres/
        <service>_<driver>.py
        unsupported.md        # optional: documents why this variant skips the case
  test/                       # generated test artifacts
    <service-type>/           # e.g. db/
      <implementation>/       # e.g. sqlite/ or postgres/
        test_<service>_derived_sql.py
        stubs/                # postgres only: mypy stubs for testcontainers (add to mypy_path)
          testcontainers/
            postgres.pyi
```

Example: `sql-derived-crud-auto-managed-columns/src/db/model/widget_repository_models.py` (shared src) and `test/db/sqlite/test_widget_repository_derived_sql.py` (generated test).

## Adding a case

1. Create `<test-name>/smithy/smithy-files.smithy` with a complete Smithy 2.0 file (`$version`, `namespace`, shapes).
2. Run codegen locally and copy src outputs into `<test-name>/src/db/model/` + `<test-name>/src/db/` + `<test-name>/src/db/<implementation>/`. Copy test outputs into `<test-name>/test/db/<implementation>/` for SQLite and psycopg. Include all four standard artifacts when the service has derived insert + select-one operations.
3. If a variant cannot implement the scenario yet, add `unsupported.md` under `<test-name>/src/db/<implementation>/` explaining why. That suppresses the missing-expectations warning and skips generated-output assertions for that variant.

## Warnings

When a registered backend variant has no `unsupported.md` and no expected files under `<test-name>/src/db/` or `<test-name>/test/db/<implementation>/`, the suite prints:

`WARNING: Test case '<test-name>' has no expected output data for variant src/db/<implementation> under ...`

## Template sources

Bundled Python SSP templates live under [`../src/db/`](../src/db/) with reusable snippets in [`../src/db/fragments/`](../src/db/fragments/). Custom `templateDirectory` overrides must mirror that layout (feature root + `fragments/` tree).
