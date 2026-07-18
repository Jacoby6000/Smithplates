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

## Choosing what to run

| Change type | Minimum focused checks | Broader checks before PR |
|-------------|------------------------|--------------------------|
| SQL trait or schema extraction | `sbtn smithplatesSqlIr/test` | `./validate test --target plugin` |
| SQL service/query extraction | `sbtn smithplatesSqlServiceIr/test` | `./validate test --target plugin` |
| DDL renderer behavior | affected renderer unit tests | affected dialect IT module with Docker |
| Query renderer behavior | affected query-renderer module tests | SQL golden tests and Python harness for affected dialect |
| SQL service renderer Scala logic | focused `smithplatesSqlServiceRenderer/testOnly ...` | `./validate lint,test --target python/db/<dialect>` |
| Python DB SSP templates | golden render test for affected case | Python harness linters and pytest |
| HTTP IR or transforms | `sbtn smithplatesHttpIr/test` | HTTP golden tests and example HTTP tests |
| HTTP SSP templates (Python) | HTTP golden case under `templates/python/tests/` | `./validate --target examples/python` |
| HTTP SSP templates (TypeScript) | HTTP golden case under `templates/typescript/tests/` | `./validate --target examples/typescript` |
| Codegen core / planner | `sbtn smithplatesCodegenCore/test` | `./validate test --target plugin` |
| Plugin settings or orchestration | focused plugin spec | `./validate test --target plugin` plus affected generated-output tests |
| Docs only | `git diff --check`, reusable component check if relevant | pre-commit docs hook |

When in doubt, choose the smallest test that exercises the changed contract first, then run the harness that validates generated output.

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

Golden template tests render Smithy fixtures under `templates/<language>/tests/` and compare the generated tree to `expected/`. Python and TypeScript cases share [`CodegenTemplateTestSuite`](../../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/codegentest/CodegenTemplateTestSuite.scala).

```bash
./scripts/run-template-golden-tests.sh
sbtn 'smithplatesPlugin/testOnly *CodegenTemplateTestSuite*'
```

Refresh expected output only when generated output intentionally changes:

```bash
sbtn 'generateGoldenTemplatesFor python <case-name> [<case-name> ...]'
sbtn 'generateGoldenTemplatesFor typescript <case-name> [<case-name> ...]'
```

See [`templates/python/tests/README.md`](../../templates/python/tests/README.md) and [`templates/typescript/tests/README.md`](../../templates/typescript/tests/README.md).

## Generated Python execution tests

The Python language harness runs ruff, mypy, and pytest against golden `expected/` trees:

```bash
./language-test-harnesses/python/run-linters.sh
./language-test-harnesses/python/run-tests.sh
```

Postgres variants require Docker. There is no TypeScript golden harness yet; use the example typecheck below.

## HTTP shared example tests

Shared HTTP scenario tests live under `example/tests/`. They run the reference server and generated client together:

```bash
cd example/tests
./run-tests.sh python python
./run-tests.sh typescript python
./run-tests.sh openapi-reference-python python
./run-tests.sh python python health-check
```

Use these when HTTP route behavior, generated app wiring, OpenAPI export/client coordination, or example adapters change.

## Example tests

Petstore references have separate validation:

```bash
./validate --target examples/python
./validate --target examples/typescript
```

Use these when changing example wiring, generated HTTP integration, OpenAPI export, or generated-client coordination.

## Troubleshooting `sbtn`

Always use `sbtn` from the repository root. If the thin client hangs or stops responding, clear stale processes and retry:

```bash
ps aux | rg -i 'sbtn|sbt-launch'
pkill -f 'sbtn-x86_64-pc-linux' || true
pkill -f 'sbt-launch\.jar' || true
```

Prefer one focused `sbtn` command at a time over long chained reload/test sequences.
