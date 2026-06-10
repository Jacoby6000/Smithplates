# Language test harnesses

Executable linters and test runners for generated artifacts under [`templates/`](../templates/).

Golden **render** comparisons stay in Scala (`./scripts/run-template-golden-tests.sh` / `sbtn smithplatesPlugin/testOnly *SqlServiceCodegenTemplateTestSuite*`). Harnesses here **lint** and **execute** generated code from `templates/<language>/tests/<case>/expected/`.

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
