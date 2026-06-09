# Python codegen golden tests

Each subdirectory is one test case discovered by [`SqlServiceCodegenTemplateTestSuite`](../../../modules/smithy-sql-service-renderer/src/test/scala/com/jacoby6000/smithy/stache/sql/SqlServiceCodegenTemplateTestSuite.scala). Name cases after the behavior under test (kebab-case), not fixture entities — for example `sql-json-structs-containing-unions`, not `shipment-json-crud`.

## Current cases

| Case | Exercises |
|------|-----------|
| `sql-derived-crud-auto-managed-columns` | `@sqlDeriveInsert` / `@sqlDeriveSelectOne` / `@sqlDeriveUpdate` / `@sqlDeriveDelete`, `@sqlAutoUuid`, `@sqlCreatedTimestamp`, `@sqlUpdatedTimestamp` |
| `sql-json-structs-containing-unions` | `@sqlJson` columns backed by structures and unions, with derived CRUD and JSON bind/read helpers |
| `sql-derive-insert-only` | `@sqlDeriveInsert` as the sole derived operation (no integration tests) |
| `sql-derive-update-only` | `@sqlDeriveUpdate` as the sole derived operation (no integration tests) |
| `sql-derive-delete-only` | `@sqlDeriveDelete` as the sole derived operation (no integration tests) |
| `sql-derive-select-one-only` | `@sqlDeriveSelectOne` as the sole derived operation (no integration tests) |
| `sql-derive-select-one-join-many-to-one` | Required FK join → required nested `category: Category` on `GetWidgetResult` |
| `sql-derive-select-one-join-many-to-one-optional` | Optional FK join → optional nested `category: Category | None` on `GetWidgetResult` |
| `sql-derive-select-one-join-one-to-many` | `@sqlDeriveSelectOne` with a child-table join; nested `order_lines: list[OrderLine]` on `GetOrderResult` (includes insert + integration tests) |
| `sql-derive-select-one-join-one-to-one` | `@sqlDeriveSelectOne` with `@sqlUniqueIndex` FK join; nested singular `bar: Bar` on `GetProfileResult` |
| `sql-derive-select-one-join-transitive` | Widget → Category → Department; second join ON clause uses Category FK to Department |
| `sql-derive-select-one-join-transitive-reverse` | Department → Category → Widget; nested `categories` and `widgets` collections |

Each `@sqlService` case that defines derived insert + select-one operations golden-tests integration tests under both `test/db/sqlite/` (aiosqlite) and `test/db/postgres/` (psycopg). Models and the service `Protocol` are shared once under `src/db/model/` and `src/db/`. Single derived-operation cases generate src artifacts only — integration tests require both insert and select-one.

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
2. Run codegen and write expected outputs from the repo root:

   ```bash
   sbtn 'generateGoldenTemplatesFor python <test-name> [<test-name> ...]'
   ```

   Example: `sbtn 'generateGoldenTemplatesFor python sql-derive-select-one-join-many-to-one sql-derive-select-one-join-transitive'`

   The task renders sqlite and postgres variants into `<test-name>/src/db/...` and `<test-name>/test/db/...` when applicable.
3. If a variant cannot implement the scenario yet, add `unsupported.md` under `<test-name>/src/db/<implementation>/` explaining why. That suppresses the missing-expectations warning and skips generated-output assertions for that variant.

## Warnings

When a registered backend variant has no `unsupported.md` and no expected files under `<test-name>/src/db/` or `<test-name>/test/db/<implementation>/`, the suite prints:

`WARNING: Test case '<test-name>' has no expected output data for variant src/db/<implementation> under ...`

## Template sources

Bundled Python SSP templates live under [`../src/db/`](../src/db/) with reusable snippets in [`../src/db/fragments/`](../src/db/fragments/). Custom `templateDirectory` overrides must mirror that layout (feature root + `fragments/` tree).
