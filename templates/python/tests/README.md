# Python template golden tests

Golden cases live under `templates/python/tests/<case-name>/`. Each case runs `smithy build` via [`SmithyBuildTemplateRunner`](../../../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/codegentest/SmithyBuildTemplateRunner.scala) and compares rendered output to files under `expected/`.

## Layout

```
templates/python/tests/<case-name>/
  smithy/smithy-files.smithy       # Smithy model for the case
  smithy-build.json                # plugin config (dialects, languageTargets)
  expected/
    db/migrations/postgres/        # golden versioned migration SQL (when dialect enabled)
    db/migrations/sqlite/
    src/db/model/*_models.py       # shared query models
    src/db/*_protocol.py           # shared Protocol interface
    src/db/sqlite/*_aiosqlite.py   # per-dialect implementation
    src/db/postgres/*_psycopg.py
    test/db/sqlite/test_*_derived_sql.py
    test/db/postgres/test_*_derived_sql.py
```

Optional variant skip marker: `expected/src/db/<implementation>/unsupported.md`.

## Run golden render comparison

```bash
sbtn "smithplatesPlugin/testOnly *SqlServiceCodegenTemplateTestSuite*"
# or
./scripts/run-template-golden-tests.sh
```

Scoped by dialect:

```bash
./scripts/run-template-golden-tests.sh   # with SMITHYSTACHE_VALIDATE_TARGET=python/db/sqlite
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
