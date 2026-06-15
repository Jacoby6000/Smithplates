# Reference implementations

Each subdirectory corresponds to a Smithplates `languageTargets` entry (for example `python`).

Shared Smithy models for all reference implementations live under [`petstore-smithy-spec/`](petstore-smithy-spec/).

See [`python/README.md`](python/README.md) for the petstore reference project.

Cross-language HTTP scenarios live under [`tests/`](tests/). Run them with:

```bash
./tests/run-tests.sh python python
```
