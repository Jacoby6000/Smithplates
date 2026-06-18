# Python template golden tests

Golden cases live under `templates/python/tests/<case-name>/`. Each case runs the Smithy CLI (`smithy build`) via [`TemplateSmithyBuild`](../../../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/codegentest/TemplateSmithyBuild.scala) as its own munit test (`build - <case-name>`), then compares rendered output to files under `expected/` per dialect variant. Fixture `smithy-build.json.template` files declare `maven.dependencies` on `smithplates-plugin`; render with [`scripts/render-template-smithy-build.sh`](../../../scripts/render-template-smithy-build.sh) (also run by [`scripts/run-template-golden-tests.sh`](../../../scripts/run-template-golden-tests.sh)) after `publishM2`. The [Python petstore reference](../../../example/python/) uses the same consumer layout. Build progress is logged via log4j to stdout as `[template-build] build - <case-name>: … (<elapsed>ms)` (see `modules/smithplates-plugin/src/test/resources/log4j2.xml`; plugin tests set `Test / logBuffered := false` so lines stream during long builds).

## Layout

```
templates/python/tests/<case-name>/
  smithy/smithy-files.smithy       # Smithy model for the case
  smithy-build.json.template       # plugin config template (render to smithy-build.json)
  expected/
    db/migrations/postgres/        # golden versioned migration SQL (when dialect enabled)
    db/migrations/sqlite/
    src/generated/db/model/*_models.py       # shared query models
    src/generated/db/*_protocol.py           # shared Protocol interface
    src/generated/db/sqlite/*_aiosqlite.py   # per-dialect implementation
    src/generated/db/postgres/*_psycopg.py
    test/db/sqlite/test_*_derived_sql.py
    test/db/postgres/test_*_derived_sql.py
```

Optional variant skip marker: `expected/src/generated/db/<implementation>/unsupported.md`.

## HTTP cases

HTTP golden cases use `@httpService` services and `smithplates.<language>.http.server` config (no SQL dialect keys). Variant id: `python/api/fastapi`.

| Case | What it validates |
|------|-------------------|
| `http-fastapi-service-errors-api` | Service-level `@httpError`, `@httpProblem` RFC 9457 exceptions, `Unit` input |
| `http-fastapi-parameter-order-api` | Canonical route param order (header → path → query), `@timestampFormat` |
| `http-fastapi-resource-nested-api` | Resources, nested routes, document/member request bodies |
| `http-fastapi-resource-custom-operations-api` | `resource operations: [...]` on deeply nested resources (redirect + custom read) |
| `http-fastapi-body-unions-nested-api` | Tagged unions and nested structures in input/output bodies |
| `http-fastapi-mixed-input-bindings-api` | POST with `@httpHeader`, `@httpLabel`, and `@httpPayload` together |
| `http-fastapi-operation-errors-api` | Operation-level `@httpError` unions on protocol methods (no exceptions) |
| `http-fastapi-output-bindings-api` | Output `@httpPayload` flattening, `@httpHeader` redirects, `@httpProblem` implied Content-Type |

```
templates/python/tests/<case-name>/
  smithy/smithy-files.smithy
  smithy-build.json.template       # http.python.server config; render after publishM2
  expected/
    src/generated/http/server/app_factory.py
    src/generated/http/server/app_services.py
    src/generated/http/server/api_response.py
    src/generated/http/server/operation_bindings.py
    src/generated/http/server/api_exceptions.py
    src/generated/http/server/api_exception_handler.py
    src/generated/http/models/problem.py
    src/generated/http/models/<output_shape>.py
    src/generated/http/server/apis/<route_group>_api.py
    src/generated/http/server/apis/<route_group>_api_base.py
```

Shared pytest fixtures for postgres integration tests live in [`conftest.py`](conftest.py) (session-scoped `PostgresContainer`).

## Run golden render comparison

```bash
sbtn "smithplatesPlugin/testOnly *CodegenTemplateTestSuite*"
# or
./scripts/run-template-golden-tests.sh
```

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
