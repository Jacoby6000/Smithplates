# Contributing to SmithyStache

User-facing plugin docs live under [`docs/usage/`](docs/usage/). Deeper architecture notes live under [`docs/contributing/`](docs/contributing/). This guide focuses on **running tests** and **where to work** depending on your role.

## Running tests

All Scala unit/integration tests and Python template harness checks run through [`scripts/run-tests.sh`](scripts/run-tests.sh):

| Subcommand | Runs |
|------------|------|
| `all` (default) | Aggregated `sbtn test` plus Python template harness |
| `scala` | All SBT aggregated module tests (Docker required for `*RendererIt` and postgres harness variants) |
| `templates` | Python ruff, mypy, and pytest against `templates/python/expected-outputs/` |

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

Build and run tests inside a container that uses the same Nix flake:

```bash
./scripts/docker-test.sh
```

This builds `smithystache-test:local` (override with `SMITHYSTACHE_TEST_IMAGE`), mounts `/var/run/docker.sock` for testcontainers, and runs `./scripts/run-tests.sh` inside the image.

### Manual setup

If you prefer installing tools yourself, see [`docs/contributing/getting-started.md`](docs/contributing/getting-started.md). You need **sbtn**, **Java 11**, **uv**, and **Docker** (for integration tests).

### Lint before pushing

```bash
nix develop . --accept-flake-config --command bash -c 'sbtn scalafmtCheckAll && sbtn '"'"'scalafixAll --check'"'"' && sbtn compile'
```

CI runs the same checks via [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

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
| Published plugin | `modules/smithy-stache-plugin/` | `sbtn smithyStachePlugin/test` |
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
3. Refresh goldens when output changes intentionally (see [`templates/python/expected-outputs/README.md`](templates/python/expected-outputs/README.md)).
4. Run `./language-test-harnesses/python/run-tests.sh` (or `./scripts/run-tests.sh templates`) for ruff, strict mypy, and pytest.

Wire template resources in root [`build.sbt`](build.sbt) (`Compile` / `Test` `unmanagedResourceDirectories`).

### Adding a new language

1. Add `templates/<language>/src/<feature>/` with SSP (or other) templates mirroring the artifact layout expected by [`SqlServiceCodegenDbArtifacts`](modules/smithy-sql-service-renderer/src/main/scala/com/jacoby6000/smithy/stache/sql/codegen/SqlServiceCodegenDbArtifacts.scala).
2. Register bundled templates in the plugin if publishing built-in support (`LanguageTargetTemplateValidator`, `build.sbt` resources).
3. Add golden cases under `templates/<language>/expected-outputs/<test-case>/`.
4. Add a harness under `language-test-harnesses/<language>/` and extend [`scripts/run-tests.sh`](scripts/run-tests.sh) when execution validation is ready.
5. Extend [`CodegenTemplateTestSuite`](modules/smithy-sql-service-renderer/src/test/scala/com/jacoby6000/smithy/stache/codegentest/CodegenTemplateTestSuite.scala) backends in [`SqlServiceCodegenTemplateTestSuite`](modules/smithy-sql-service-renderer/src/test/scala/com/jacoby6000/smithy/stache/sql/SqlServiceCodegenTemplateTestSuite.scala).

Consumers can also point `smithy-stache.sql.languageTargets.<lang>.templateDirectory` at their own template tree; bundled languages use default `classpath:` (see [`docs/usage/integration.md`](docs/usage/integration.md)).

### Postgres mypy stubs

Generated postgres integration tests import `testcontainers`. Bundled stubs ship at `test/db/postgres/stubs/testcontainers/postgres.pyi`; add `<testOutputDir>/db/postgres/stubs` to `mypy_path` in consumer projects.

---

## Documentation and reusable components

Markdown under `docs/reusable-components/` is embedded into other docs via [`scripts/sync_reusable_components.py`](scripts/sync_reusable_components.py). After editing a component, run the sync script and re-stage affected Markdown files.
