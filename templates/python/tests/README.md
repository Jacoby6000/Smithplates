# Python template golden tests

Golden cases live under `templates/python/tests/<case-name>/`. Each case runs `smithy build` via [`SmithyBuildTemplateRunner`](../../../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/codegentest/SmithyBuildTemplateRunner.scala) as its own munit test (`build - <case-name>`), then compares rendered output to files under `expected/` per dialect variant.

## Layout

```
templates/python/tests/<case-name>/
  smithy/smithy-files.smithy       # Smithy model for the case
  smithy-build.json                # plugin config (dialects, languageTargets)
  expected/
    db/postgres.sql                # golden migration DDL (when dialect enabled)
    db/sqlite.sql
    src/db/model/*_models.py       # shared query models
    src/db/*_protocol.py           # shared Protocol interface
    src/db/sqlite/*_aiosqlite.py   # per-dialect implementation
    src/db/postgres/*_psycopg.py
    test/db/sqlite/test_*_derived_sql.py
    test/db/postgres/test_*_derived_sql.py
```

Optional variant skip marker: `expected/src/db/<implementation>/unsupported.md`.

## HTTP cases

HTTP golden cases use `@httpService` services and `smithplates.http.<language>.server` config (no SQL dialect keys). Variant id: `python/api/fastapi`.

```
templates/python/tests/<case-name>/
  smithy/smithy-files.smithy
  smithy-build.json                # http.python.server + smithplates-plugin maven dep
  expected/
    src/api/app_factory.py
    src/api/app_services.py
    src/api/api_response.py
    src/api/operation_bindings.py
    src/api/api_exceptions.py
    src/api/api_exception_handler.py
    src/api/models/problem.py
    src/api/models/<output_shape>.py
    src/api/apis/<route_group>_api.py
    src/api/apis/<route_group>_api_base.py
```

Shared pytest fixtures for postgres integration tests live in [`conftest.py`](conftest.py) (session-scoped `PostgresContainer`).

## Run golden render comparison

```bash
sbtn "smithplatesPlugin/testOnly *SqlServiceCodegenTemplateTestSuite*"
sbtn "smithplatesPlugin/testOnly *HttpServiceCodegenTemplateTestSuite*"
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

Postgres variants require Docker. See [`language-test-harnesses/python/README.md`](../../language-test-harnesses/python/README.md).
