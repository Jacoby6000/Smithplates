# Testing

Smithplates uses several layers of tests because the project validates both Scala extraction/rendering logic and generated target-language output.

## Primary entry point

Use `./validate` from the repository root:

```bash
./validate
./validate build
./validate test --target plugin
./validate lint,test --target python/db/sqlite
```

`./validate` uses Nix when available and falls back to Docker when needed.

## Scala tests

Run focused module tests with `sbtn`:

```bash
sbtn smithplatesSqlIr/test
sbtn smithplatesSqlServiceIr/test
sbtn smithplatesSqlServiceRenderer/test
sbtn smithplatesPlugin/test
```

Use focused `testOnly` runs while iterating:

```bash
sbtn 'smithplatesPlugin/testOnly *SmithplatesSqlSettingsSpec'
sbtn 'smithplatesSqlServiceRenderer/testOnly *SqlServiceCodegenRendererSpec'
```

## Dialect integration tests

Dialect DDL integration tests apply generated schema DDL to real databases through testcontainers:

```bash
sbtn smithplatesSqlDdlRendererPostgresIt/test
sbtn smithplatesSqlDdlRendererSqliteIt/test
```

Docker must be installed and running.

## Golden render tests

Golden template tests render Smithy fixtures under `templates/python/tests/` and compare the generated tree to `expected/`.

```bash
./scripts/run-template-golden-tests.sh
sbtn 'smithplatesPlugin/testOnly *CodegenTemplateTestSuite*'
```

Refresh expected output only when generated output intentionally changes:

```bash
sbtn 'generateGoldenTemplatesFor python <case-name> [<case-name> ...]'
```

## Generated Python execution tests

Language harnesses run ruff, mypy, and pytest against golden `expected/` trees:

```bash
./language-test-harnesses/python/run-linters.sh
./language-test-harnesses/python/run-tests.sh
```

Postgres variants require Docker.

## Example tests

The Python petstore reference has separate validation:

```bash
./validate --target examples/python
```

Use this when changing example wiring, generated HTTP integration, OpenAPI export, or generated-client coordination.
