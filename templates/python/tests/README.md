# Python template golden tests

Golden cases live under `templates/python/tests/<case-name>/`. Each case runs `smithy build` via [`SmithyBuildTemplateRunner`](../../../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/codegentest/SmithyBuildTemplateRunner.scala) as its own munit test (`build - <case-name>`), then compares rendered output to files under `expected/` per dialect variant. Fixture `smithy-build.json` files intentionally omit `maven.dependencies`; the runner loads the plugin from the sbt test classpath. The [Python petstore reference](../../../example/python/) shows the consumer Maven layout. Build progress is logged via log4j to stdout as `[template-build] build - <case-name>: … (<elapsed>ms)` (see `modules/smithplates-plugin/src/test/resources/log4j2.xml`; plugin tests set `Test / logBuffered := false` so lines stream during long builds).

Executable HTTP fixtures place `test_*.py` and `mypy-files.txt` at the case root. The Python language harness lints the complete generated tree, type-checks the listed auth-sensitive files, and runs those tests alongside the derived-SQL suites.

## Layout

```
templates/python/tests/<case-name>/
  smithy/smithy-files.smithy       # Smithy model for the case
  smithy-build.json                # plugin config (language sql/http blocks); no maven block
  expected/
    db/migrations/postgres/        # golden versioned migration SQL (when dialect enabled)
    db/migrations/sqlite/
    src/generated/<smithy namespace>/models/*_models.py       # shared query models
    src/generated/<smithy namespace>/*_protocol.py            # shared Protocol interface
    src/generated/<smithy namespace>/sqlite/*_aiosqlite.py    # per-dialect implementation
    src/generated/<smithy namespace>/postgres/*_psycopg.py
    test/<smithy namespace>/sqlite/test_*_derived_sql.py
    test/<smithy namespace>/postgres/test_*_derived_sql.py
```

Golden fixtures use `namespace example` and `sourceOutputDir: "src/generated"`, so paths look like `src/generated/example/...`.

Optional variant skip marker: `expected/src/generated/<smithy namespace>/<implementation>/unsupported.md`.

## HTTP cases

HTTP golden cases use `@httpService` services and `smithplates.python.http.{server,client}` config (no SQL dialect keys). Variants include `python/api/fastapi` and client-focused cases.

| Case | What it validates |
|------|-------------------|
| `http-fastapi-service-errors-api` | Service-level `@httpError`, `@httpProblem` RFC 9457 exceptions, `Unit` input |
| `http-fastapi-parameter-order-api` | Canonical route param order (header → path → query), `@timestampFormat` |
| `http-fastapi-resource-nested-api` | Resources, nested routes, document/member request bodies |
| `http-fastapi-resource-custom-operations-api` | `resource operations: [...]` on deeply nested resources (redirect + custom read) |
| `http-fastapi-body-unions-nested-api` | Tagged unions and nested structures in input/output bodies |
| `http-fastapi-mixed-input-bindings-api` | POST with `@httpHeader`, `@httpLabel`, and `@httpPayload` together |
| `http-fastapi-operation-errors-api` | Operation-level `@httpError` unions on protocol methods (no exceptions) |
| `http-fastapi-operation-httpproblem-errors-api` | Operation-level `@httpProblem` error unions |
| `http-fastapi-output-bindings-api` | Output `@httpPayload` flattening, `@httpHeader` redirects, `@httpProblem` implied Content-Type |
| `http-fastapi-string-alias-enum-imports-api` | String aliases and enum import closure |
| `http-fastapi-httpx-combined-api` | Server + default async httpx client from one model |
| `http-httpx-mixed-input-bindings-api` | Sync httpx client with mixed input bindings |
| `http-httpx2-client-api` | Async and sync HTTPX2 clients |
| `http-fastapi-websocket-api` | `@websocket` server routes + client |
| `http-fastapi-websocket-path-params-api` | WebSocket URI path labels |
| `http-nested-properties-api` | Smithy `@nestedProperties` body flattening with both async and sync clients |
| `http-union-list-member-api` | Unions and list members in HTTP models |
| `http-additional-templates-append` | Consumer `additionalTemplatesDirectory` append |
| `http-additional-templates-override` | Consumer deck `overrides` by bundled id |
| `http-additional-templates-external` | `enableExternalTemplates` filesystem templates |
| `http-additional-templates-path-collision` | Duplicate resolved output paths fail |

```
templates/python/tests/<case-name>/
  smithy/smithy-files.smithy
  smithy-build.json                # http.python.server / client config; no maven block in golden fixtures
  expected/
    src/generated/<smithy namespace>/app_factory.py
    src/generated/<smithy namespace>/app_services.py
    src/generated/<smithy namespace>/api_response.py
    src/generated/<smithy namespace>/operation_bindings.py
    src/generated/<smithy namespace>/api_exceptions.py
    src/generated/<smithy namespace>/api_exception_handler.py
    src/generated/smithplates/codegen/http/http_problem.py   # shared HttpProblem base
    src/generated/<smithy namespace>/<output_shape>.py
    src/generated/<smithy namespace>/apis/<route_group>_api.py
    src/generated/<smithy namespace>/apis/<route_group>_api_base.py
    src/generated/<smithy namespace>/websocket_routes.py      # when @websocket ops exist
    src/generated/<smithy namespace>/clients/websocket_client.py
```

Shared pytest fixtures for postgres integration tests live in [`conftest.py`](conftest.py) (session-scoped `PostgresContainer`).

## SQL cases (selection)

| Case | What it validates |
|------|-------------------|
| `sql-derived-crud-auto-managed-columns` | `@sqlAutoUuid` / timestamps / `@sqlAutoIncrement` insert omission |
| `sql-enums` | Smithy `enum` / `intEnum` DDL + models |
| `sql-self-referential-foreign-key` | Self-FK inline in `CREATE TABLE` |
| `sql-additional-templates-*` | Consumer SQL decks (append / override / external) |
| `sql-derive-*` | Per-derive-trait and join cardinality coverage |

## Run golden render comparison

```bash
./scripts/run-template-golden-tests.sh
# or
sbtn "smithplatesPlugin/testOnly *CodegenTemplateTestSuite*"
```

Without a Python-scoped `SMITHYSTACHE_VALIDATE_TARGET`, `./scripts/run-template-golden-tests.sh` runs the **full** `CodegenTemplateTestSuite` (Python and TypeScript cases). The env/target scoping is Python harness-oriented; TypeScript-only iteration should use the `sbtn` form above.

Scoped by dialect or HTTP framework:

```bash
./scripts/run-template-golden-tests.sh   # with SMITHYSTACHE_VALIDATE_TARGET=python/db/sqlite
./scripts/run-template-golden-tests.sh   # with SMITHYSTACHE_VALIDATE_TARGET=python/api/fastapi
```

## Refresh goldens

After intentional template output changes:

```bash
sbtn 'generateGoldenTemplatesFor python <case-name> [<case-name> ...]'
```

Writes into `expected/` for each case (runs ruff format on generated Python).

## Execute generated tests

Lint and run pytest against golden `expected/` trees:

```bash
./language-test-harnesses/python/run-linters.sh
./language-test-harnesses/python/run-tests.sh
```

Postgres variants require Docker. See [`language-test-harnesses/python/README.md`](../../../language-test-harnesses/python/README.md).
