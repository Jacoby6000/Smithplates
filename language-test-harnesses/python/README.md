# Python language test harness

Lints and runs `@pytest.mark.integration` suites from [`templates/python/expected-outputs/`](../../templates/python/expected-outputs/).

Each golden case has its own `PYTHONPATH` (`src/db/model`, `src/db`, `src/db/<implementation>`), so runners check each case/dialect separately.

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
export PYTHONPATH=../../templates/python/expected-outputs/sql-derived-crud-auto-managed-columns/src/db/model:../../templates/python/expected-outputs/sql-derived-crud-auto-managed-columns/src/db:../../templates/python/expected-outputs/sql-derived-crud-auto-managed-columns/src/db/sqlite
./language-test-harnesses/python/run-tests.sh -m integration ../../templates/python/expected-outputs/sql-derived-crud-auto-managed-columns/test/db/sqlite
```

Cases with `src/db/<implementation>/unsupported.md` are skipped.

Shared iteration logic lives in [`lib/common.sh`](lib/common.sh).
