# Mustache template golden tests

Each subdirectory is one test case discovered by [`MustacheTemplateTestSuite`](../../scala/com/jacoby6000/smithy/stache/mustachetest/MustacheTemplateTestSuite.scala). Name cases after the behavior under test (kebab-case), not fixture entities — for example `sql-json-structs-containing-unions`, not `shipment-json-crud`.

## Current cases

| Case | Exercises |
|------|-----------|
| `sql-derived-crud-auto-managed-columns` | `@sqlDeriveInsert` / `@sqlDeriveSelectOne` / `@sqlDeriveUpdate` / `@sqlDeriveDelete`, `@sqlAutoUuid`, `@sqlCreatedTimestamp`, `@sqlUpdatedTimestamp` |
| `sql-json-structs-containing-unions` | `@sqlJson` columns backed by structures and unions, with derived CRUD and JSON bind/read helpers |

Each `@sqlService` case that defines derived insert + select-one operations golden-tests integration tests under both `python/test/db/sqlite/` (aiosqlite) and `python/test/db/postgres/` (psycopg). Models and the service `Protocol` are shared once under `python/src/db/model/` and `python/src/db/`.

[`SqlServiceCodegenMustacheTemplateTestSuite`](../../scala/com/jacoby6000/smithy/stache/sql/codegen/SqlServiceCodegenMustacheTemplateTestSuite.scala) golden-compares output, then runs strict mypy/pyright on generated src under `python/src/db/` and pytest on generated tests under `python/test/db/<implementation>/` via [`PythonCodegenWorkspace`](../../scala/com/jacoby6000/smithy/stache/sql/codegen/PythonCodegenWorkspace.scala) under `target/sql-service-codegen-python-workspace/`.

## Layout

```
<test-name>/
  smithy/
    smithy-files.smithy       # Smithy model for the scenario
  <language>/                 # e.g. python/
    src/                      # generated src artifacts
      <service-type>/         # e.g. db/
        model/                # shared model artifacts (generated once)
        <service>_protocol.py # shared service Protocol (generated once)
        <implementation>/     # e.g. sqlite/ or postgres/
          <service>_<driver>.py
          unsupported.md      # optional: documents why this variant skips the case
    test/                     # generated test artifacts
      <service-type>/         # e.g. db/
        <implementation>/     # e.g. sqlite/ or postgres/
          test_<service>_derived_sql.py
```

Example: `sql-derived-crud-auto-managed-columns/python/src/db/model/widget_repository_models.py` (shared src) and `python/test/db/sqlite/test_widget_repository_derived_sql.py` (generated test).

## Adding a case

1. Create `<test-name>/smithy/smithy-files.smithy` with a complete Smithy 2.0 file (`$version`, `namespace`, shapes).
2. Run codegen locally and copy src outputs into `<test-name>/python/src/db/model/` + `<test-name>/python/src/db/` + `<test-name>/python/src/db/<implementation>/`. Copy test outputs into `<test-name>/python/test/db/<implementation>/` for SQLite (`SqlServiceCodegenPythonDbBackend.sqlite`) and psycopg (`SqlServiceCodegenPythonDbBackend.postgres`). Include all four standard artifacts when the service has derived insert + select-one operations.
3. If a variant cannot implement the scenario yet, add `unsupported.md` under `<test-name>/python/src/db/<implementation>/` explaining why. That suppresses the missing-expectations warning and skips generated-output assertions for that variant.

## Warnings

When a registered backend variant has no `unsupported.md` and no expected files under `<test-name>/python/src/db/` or `<test-name>/python/test/db/<implementation>/`, the suite prints:

`WARNING: Test case '<test-name>' has no expected output data for variant <language>/src/<service-type>/<implementation> under ...`

## Implementing a backend

1. Implement [`MustacheTemplateLanguageBackend`](../../scala/com/jacoby6000/smithy/stache/mustachetest/MustacheTemplateLanguageBackend.scala) with a [`MustacheTemplateVariant`](../../scala/com/jacoby6000/smithy/stache/mustachetest/MustacheTemplateVariant.scala) (`languageId`, `serviceTypeId`, `implementationId`), plus `loadModel`, `render`, and optional `validateRenderedOutputs`.
2. Subclass `MustacheTemplateTestSuite` and pass your backend(s).
3. Mismatch failures include a contextual diff via [`TextContentDiff`](../../scala/com/jacoby6000/smithy/stache/mustachetest/TextContentDiff.scala).
