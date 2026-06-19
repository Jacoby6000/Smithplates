# Python language test harness

Lints and runs `@pytest.mark.integration` suites from [`templates/python/tests/`](../../templates/python/tests/) golden `expected/` trees.

Each golden case uses `PYTHONPATH=expected/src` with generated modules under `expected/src/generated/<smithy namespace>/`, matching consumer `sourceOutputDir: "src/generated"` and imports such as `generated.example.*`.

## Linters (`run-linters.sh`)

1. **ruff check** — lint (import order, style; long lines in tests and bundled transaction helpers are ignored)
2. **ruff format --check** — formatting
3. **mypy** — strict typing (`--strict`, `extra_checks`; postgres variants pick up bundled `test/<namespace>/postgres/stubs/` via `MYPYPATH`)

```bash
./language-test-harnesses/python/run-linters.sh
```

## Tests (`run-tests.sh`)

4. **pytest** — `@pytest.mark.integration` suites (postgres requires **Docker**)

Suites run per golden case and dialect so each invocation uses an isolated `PYTHONPATH` (required when multiple cases generate the same repository module names). Postgres uses a module-scoped `postgres_container` fixture in [`templates/python/tests/conftest.py`](../../templates/python/tests/conftest.py) so each generated `test_*_derived_sql.py` module gets an isolated database (migration state in `_smithplates_migrations` is not shared across cases). Pytest is configured for live output (`-v`, `--capture=tee-sys`) via [`pyproject.toml`](pyproject.toml).

```bash
./language-test-harnesses/python/run-tests.sh
```

Pass custom pytest args (set `PYTHONPATH` yourself when targeting golden trees):

```bash
export PYTHONPATH=../../templates/python/tests/sql-derived-crud-auto-managed-columns/expected/src
./language-test-harnesses/python/run-tests.sh -m integration ../../templates/python/tests/sql-derived-crud-auto-managed-columns/expected/test/example/sqlite
```

Cases with `expected/src/generated/<smithy namespace>/<implementation>/unsupported.md` are skipped.

Shared iteration logic lives in [`lib/common.sh`](lib/common.sh).
