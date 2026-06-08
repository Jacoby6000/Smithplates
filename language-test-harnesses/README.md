# Language test harnesses

Executable test runners for generated artifacts under [`language-templates/`](../language-templates/).

Golden **render** comparisons stay in Scala (`sbtn smithySqlServiceRenderer/test`). Harnesses here **execute** generated integration tests (pytest) from `language-templates/<language>/expected-outputs/`.

## Python

```bash
./language-test-harnesses/python/run-tests.sh
```

This loops over expected-outputs cases and, for each `test/db/<implementation>/` tree, runs **ruff**, **mypy**, and **pytest** with the matching `PYTHONPATH`. Pytest temp output goes under `target/language-test-harnesses/` (gitignored).

Requires [uv](https://docs.astral.sh/uv/) on `PATH`; postgres variants require **Docker**.
