# Python language test harness

Lints and runs `@pytest.mark.integration` suites from [`templates/python/tests/`](../../templates/python/tests/) golden `expected/` trees.

Each golden case has its own `PYTHONPATH` (`expected/src/db/model`, `expected/src/db`, `expected/src/db/<implementation>`), so runners check each case/dialect separately.

## Linters (`run-linters.sh`)

1. **ruff check** — lint (import order, style; long lines in tests and bundled transaction helpers are ignored)
2. **ruff format --check** — formatting
3. **mypy** — strict typing (`--strict`, `extra_checks`; postgres variants pick up bundled `test/db/postgres/stubs/` via `MYPYPATH`)

```bash
./language-test-harnesses/python/run-linters.sh
```

## Tests (`run-tests.sh`)

4. **pytest** — `@pytest.mark.integration` suites (postgres requires **Docker**)

```bash
./language-test-harnesses/python/run-tests.sh
```

Pass custom pytest args (set `PYTHONPATH` yourself when targeting golden trees):

```bash
export PYTHONPATH=../../templates/python/tests/sql-derived-crud-auto-managed-columns/expected/src/db/model:../../templates/python/tests/sql-derived-crud-auto-managed-columns/expected/src/db:../../templates/python/tests/sql-derived-crud-auto-managed-columns/expected/src/db/sqlite
./language-test-harnesses/python/run-tests.sh -m integration ../../templates/python/tests/sql-derived-crud-auto-managed-columns/expected/test/db/sqlite
```

Cases with `expected/src/db/<implementation>/unsupported.md` are skipped.

Shared iteration logic lives in [`lib/common.sh`](lib/common.sh).
