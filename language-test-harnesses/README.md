# Language test harnesses

Executable linters and test runners for generated artifacts under [`templates/`](../templates/).

Golden **render** comparisons stay in Scala (`sbtn smithySqlServiceRenderer/test`). Harnesses here **lint** and **execute** generated code from `templates/<language>/expected-outputs/`.

## Python

```bash
./language-test-harnesses/python/run-linters.sh   # ruff + mypy
./language-test-harnesses/python/run-tests.sh     # pytest
```

Or from the repository root:

```bash
./scripts/run-linters.sh templates
./scripts/run-tests.sh templates
```

Requires [uv](https://docs.astral.sh/uv/) on `PATH`; postgres pytest variants require **Docker**.
