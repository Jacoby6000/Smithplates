# Contributing to Smithplates

User-facing plugin docs live under [`docs/usage/`](docs/usage/). Deeper architecture notes live under [`docs/contributing/`](docs/contributing/). This guide focuses on **running tests** and **where to work** depending on your role.

## Quick validate

From the repository root, run linters and tests with Nix when available, otherwise Docker:

```bash
./validate                 # lint + test (default)
./validate build           # lint and compile only
./validate test            # tests only
```

On Windows (PowerShell):

```powershell
.\validate.ps1
.\validate.ps1 build
.\validate.ps1 test
```

## Linters and compilers

Run Scala and template-language static checks through [`scripts/run-linters.sh`](scripts/run-linters.sh):

| Subcommand | Runs |
|------------|------|
| `all` (default) | Scala scalafmt/scalafix/compile plus every `language-test-harnesses/*/run-linters.sh` |
| `scala` | `sbtn scalafmtCheckAll`, `sbtn scalafixAll --check`, `sbtn compile` |
| `templates` | Per-language harness linters (Python: ruff check, ruff format --check, strict mypy) |

```bash
./scripts/run-linters.sh
nix run .#run-linters
```

CI runs [`./validate`](validate) with Nix (Linux), `./validate` without Nix (Linux, auto-selects Docker), and [`validate.ps1`](validate.ps1) (Windows, auto-selects Docker) via [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Running tests

[`scripts/run-tests.sh`](scripts/run-tests.sh) runs **tests only** (no linters):

| Subcommand | Runs |
|------------|------|
| `all` (default) | Aggregated `sbtn test` plus Python template pytest suites |
| `scala` | All SBT aggregated module tests (Docker required for `*RendererIt` and postgres harness variants) |
| `templates` | Python pytest against `templates/python/expected-outputs/` |

**Docker** must be installed and running for:

- `smithySqlPostgresRendererIt` / `smithySqlSqliteRendererIt` (testcontainers-scala)
- Postgres variants of the Python template harness (testcontainers Python)

### With Nix (recommended)

Install [Nix](https://nixos.org/download/) with flakes enabled, then from the repository root:

```bash
nix develop . --accept-flake-config
./scripts/run-tests.sh
```

One-shot without entering the shell:

```bash
nix develop . --accept-flake-config --command ./scripts/run-tests.sh
```

Flake app equivalent:

```bash
nix run .#run-tests
```

The dev shell provides **Java 11**, **sbtn**, **uv**, and the **docker** client. It does not start a Docker daemon — use your host Docker service.

### With Docker (no Nix on the host)

When Nix is not installed, `./validate` auto-detects a running Docker daemon and uses the same commands as above:

```bash
./validate
./validate build
```

On Windows: `.\validate.ps1` (uses Docker when Nix is not installed).

The Docker path builds `smithystache-test:local` (override with `SMITHYSTACHE_TEST_IMAGE`) when the Dockerfile or flake inputs change, bind-mounts the repository, and mounts `/var/run/docker.sock` for testcontainers during tests.

### Manual setup

If you prefer installing tools yourself, see [`docs/contributing/getting-started.md`](docs/contributing/getting-started.md). You need **sbtn**, **Java 11**, **uv**, and **Docker** (for integration tests).

### Lint before pushing

```bash
./validate build
```

Or run linters directly: `./scripts/run-linters.sh` / `nix run .#run-linters`

---

## Plugin authors (Scala)

Work on the Smithy build plugin, SQL IR, query renderers, schema DDL, and codegen **orchestration** in Scala under `modules/`.

| Area | Directory | Typical tests |
|------|-----------|---------------|
| Schema IR | `modules/smithy-sql-ir/` | `sbtn smithySqlIr/test` |
| Service/query IR | `modules/smithy-sql-service-ir/` | `sbtn smithySqlServiceIr/test` |
| Query rendering | `modules/smithy-sql-service-query-renderer*` | `sbtn smithySqlServiceQueryRenderer/test`, … |
| DDL rendering | `modules/smithy-sql-*-renderer/` | `sbtn smithySqlPostgresRenderer/test`, … |
| SSP rendering engine | `modules/smithy-sql-service-renderer/` | `sbtn smithySqlServiceRenderer/test` |
| Published plugin | `modules/smithplates-plugin/` | `sbtn smithplatesPlugin/test` |
| Dialect IT | `modules/smithy-sql-*-renderer-it/` | `sbtn smithySqlPostgresRendererIt/test`, … |

**Conventions:** see [`AGENTS.md`](AGENTS.md) (Scala 3.3.6, strict options, `sbtn`, functional validation with `ValidatedNel`). Pre-commit hooks (`scripts/pre-commit-scala.sh`) run scalafmt, scalafix, and compile on staged Scala/SBT changes.

**Typical loop:**

1. `./scripts/run-tests.sh scala` (or the specific module test above).
2. For SQL rendering changes, ensure Docker IT modules pass.
3. `sbtn publishM2` and validate in a consumer `smithy build` when plugin wiring changes.

Template **content** (Mustache/SSP output for target languages) is maintained separately under `templates/` — see below.

---

## Template authors (codegen output)

Work on **target-language artifacts** produced from `@sqlService` models: SSP templates, golden expected outputs, and language test harnesses.

### Layout

```
templates/
  <language>/                 # e.g. python
    src/<feature>/            # SSP sources (bundled into the plugin JAR)
    expected-outputs/         # golden render + execution fixtures
language-test-harnesses/
  <language>/                 # ruff/mypy/pytest runners for expected-outputs
```

Bundled Python DB templates live under [`templates/python/src/db/`](templates/python/src/db/). Golden render comparisons use [`templates/python/expected-outputs/`](templates/python/expected-outputs/); execution checks use [`language-test-harnesses/python/`](language-test-harnesses/python/).

### Updating bundled Python templates

1. Edit SSP under `templates/python/src/db/` (and `fragments/`).
2. Run `sbtn smithySqlServiceRenderer/test` — compares rendered output to golden files under `expected-outputs/`.
3. Refresh goldens when output changes intentionally: `sbtn 'generateGoldenTemplatesFor python <case-name> [<case-name> ...]'` (see [`templates/python/expected-outputs/README.md`](templates/python/expected-outputs/README.md)).
4. Run `./language-test-harnesses/python/run-linters.sh` then `./language-test-harnesses/python/run-tests.sh` (or `./scripts/run-linters.sh templates` / `./scripts/run-tests.sh templates`).

Wire template resources in root [`build.sbt`](build.sbt) (`Compile` / `Test` `unmanagedResourceDirectories`).

### Adding a new language

1. Add `templates/<language>/src/<feature>/` with SSP (or other) templates mirroring the artifact layout expected by [`SqlServiceCodegenDbArtifacts`](modules/smithy-sql-service-renderer/src/main/scala/com/jacoby6000/smithplates/sql/codegen/SqlServiceCodegenDbArtifacts.scala).
2. Register bundled templates in the plugin if publishing built-in support (`LanguageTargetTemplateValidator`, `build.sbt` resources).
3. Add golden cases under `templates/<language>/expected-outputs/<test-case>/`.
4. Add a harness under `language-test-harnesses/<language>/` with `run-linters.sh` and `run-tests.sh`; extend [`scripts/run-linters.sh`](scripts/run-linters.sh) and [`scripts/run-tests.sh`](scripts/run-tests.sh) pick up new languages automatically.
5. Extend [`CodegenTemplateTestSuite`](modules/smithy-sql-service-renderer/src/test/scala/com/jacoby6000/smithplates/codegentest/CodegenTemplateTestSuite.scala) backends in [`SqlServiceCodegenTemplateTestSuite`](modules/smithy-sql-service-renderer/src/test/scala/com/jacoby6000/smithplates/sql/SqlServiceCodegenTemplateTestSuite.scala).

Consumers can also point `smithplates.sql.languageTargets.<lang>.templateDirectory` at their own template tree; bundled languages use default `classpath:` (see [`docs/usage/integration.md`](docs/usage/integration.md)).

### Postgres mypy stubs

Generated postgres integration tests import `testcontainers`. Bundled stubs ship at `test/db/postgres/stubs/testcontainers/postgres.pyi`; add `<testOutputDir>/db/postgres/stubs` to `mypy_path` in consumer projects.

---

## Documentation and reusable components

Markdown under `docs/reusable-components/` is embedded into other docs via [`scripts/sync_reusable_components.py`](scripts/sync_reusable_components.py). After editing a component, run the sync script and re-stage affected Markdown files.
