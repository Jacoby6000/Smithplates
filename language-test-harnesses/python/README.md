# Python language test harness

Runs `@pytest.mark.integration` suites from [`language-templates/python/expected-outputs/`](../../language-templates/python/expected-outputs/).

Each golden case has its own `PYTHONPATH` (`src/db/model`, `src/db`, `src/db/<implementation>`), so the default runner checks and tests each case/dialect separately:

1. **ruff check** — lint (import order, style; long lines in tests and bundled transaction helpers are ignored)
2. **ruff format --check** — formatting
3. **mypy** — strict typing (`--strict`, `extra_checks`, typed `testcontainers` stubs under `stubs/`)
4. **pytest** — `@pytest.mark.integration` suites

## Run

```bash
./language-test-harnesses/python/run-tests.sh
```

Equivalent to looping:

```bash
PYTHONPATH=<case>/src/db/model:<case>/src/db:<case>/src/db/sqlite \
  uv run pytest -m "integration and sqlite" <case>/test/db/sqlite
```

Pass custom pytest args (set `PYTHONPATH` yourself when targeting golden trees):

```bash
export PYTHONPATH=../../language-templates/python/expected-outputs/sql-derived-crud-auto-managed-columns/src/db/model:../../language-templates/python/expected-outputs/sql-derived-crud-auto-managed-columns/src/db:../../language-templates/python/expected-outputs/sql-derived-crud-auto-managed-columns/src/db/sqlite
./language-test-harnesses/python/run-tests.sh -m integration ../../language-templates/python/expected-outputs/sql-derived-crud-auto-managed-columns/test/db/sqlite
```

Cases with `src/db/<implementation>/unsupported.md` are skipped.

Postgres variants require **Docker**.
