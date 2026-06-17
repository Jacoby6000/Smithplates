# Contributing to Smithplates

User-facing plugin docs live under [`docs/usage/`](docs/usage/). Deeper architecture notes live under [`docs/contributing/`](docs/contributing/). This guide focuses on **running tests** and **where to work** depending on your role.

## Quick validate

From the repository root, run linters and tests with Nix when available, otherwise Docker:

```bash
./validate                 # lint + test (default)
./validate build           # lint and compile only
./validate test            # tests only
./validate lint --target python/db/sqlite
./validate test --target plugin
./validate lint,test --target python/db/postgres
```

Optional `--target` scopes lint and test to a subset:

| Target | Lint | Test |
|--------|------|------|
| *(default)* | Scala + all template harnesses | `sbtn test` + all pytest |
| `plugin` | Scala only | `sbtn test` excluding template golden suite |
| `python` | All Python service types | Template golden tests + all pytest |
| `python/db` | `db` service type | Golden tests + pytest for `db` |
| `python/db/sqlite` | Shared `db` + sqlite | Golden tests (sqlite variants) + pytest sqlite |
| `python/db/postgres` | Shared `db` + postgres | Golden tests (postgres variants) + pytest postgres |
| `examples` | All example harness linters | All example harness tests |
| `examples/python` | Python petstore reference (ruff, mypy) | pytest (API + sqlite + postgres) + shared HTTP tests |

On Windows (PowerShell):

```powershell
.\validate.ps1
.\validate.ps1 build
.\validate.ps1 test
.\validate.ps1 lint -Target python/db/sqlite
.\validate.ps1 test -Target plugin
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

CI runs [`.github/workflows/ci.yml`](.github/workflows/ci.yml) in parallel jobs: plugin build (`./validate --target plugin`) and per-language template validation (`./validate --target <language>`; currently `python`) on Linux with Nix, plus a Docker smoke job ([`scripts/ci-docker-validate.sh`](scripts/ci-docker-validate.sh)) that checks flake dev-shell parity between host Nix and the test image and that `./validate` selects Docker when Nix is unavailable. Jobs restore Nix, sbt/coursier, uv, and Docker test-image caches where applicable.

### Docker dev-shell parity (CI)

The `validate-docker` job compares sorted output from [`scripts/lib/dev-shell-fingerprint.sh`](scripts/lib/dev-shell-fingerprint.sh) on the host (`nix develop`) and inside `smithystache-test:local` (bind-mounted sources, same flake). The fingerprint must stay aligned with `devShells.default` in [`flake.nix`](flake.nix).

When you add build systems, language targets, or other dev-shell tooling to the flake, update the fingerprint in the same change (or a follow-up that lands before CI fails):

1. Add each new executable to the `for cmd in ...` presence loop.
2. Add a `*-version=` line when the tool exposes a stable `--version` (or equivalent) output worth comparing across host and container.

Do not fingerprint the Nix CLI version: the Docker image pins `nixos/nix` while CI installs Nix via `cachix/install-nix-action`; dev-shell **packages** from `flake.lock` are what must match.

Run the smoke checks locally:

```bash
./scripts/ci-docker-validate.sh
```

## Running tests

[`scripts/run-tests.sh`](scripts/run-tests.sh) runs **tests only** (no linters):

| Subcommand | Runs |
|------------|------|
| `all` (default) | Aggregated `sbtn test` plus Python template pytest suites |
| `scala` | All SBT aggregated module tests (Docker required for `*RendererIt` and postgres harness variants) |
| `plugin` | All SBT tests except `CodegenTemplateTestSuite` |
| `templates` | Scala template golden tests plus Python pytest under `templates/python/tests/` |

**Docker** must be installed and running for:

- `smithplatesSqlDdlRendererPostgresIt` / `smithplatesSqlDdlRendererSqliteIt` (testcontainers-scala)
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

The dev shell provides **Java 11**, **sbtn**, **Smithy CLI**, **uv**, and the **docker** client. It does not start a Docker daemon — use your host Docker service.

### With Docker (no Nix on the host)

When Nix is not installed, `./validate` auto-detects a running Docker daemon and uses the same commands as above:

```bash
./validate
./validate build
```

On Windows: `.\validate.ps1` (uses Docker when Nix is not installed).

The Docker path builds `smithystache-test:local` (override with `SMITHYSTACHE_TEST_IMAGE`) when the Dockerfile or flake inputs change, bind-mounts the repository, and mounts `/var/run/docker.sock` for testcontainers during tests.

### Manual setup

If you prefer installing tools yourself, see [`docs/contributing/getting-started.md`](docs/contributing/getting-started.md). You need **sbtn**, **Java 11**, **Smithy CLI**, **uv**, and **Docker** (for integration tests).

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
| Schema IR | `modules/smithplates-sql-ir/` | `sbtn smithplatesSqlIr/test` |
| Shared DDL rendering | `modules/smithplates-sql-ddl-renderer-common/` | (covered by dialect renderer tests) |
| Service/query IR | `modules/smithplates-sql-service-ir/` | `sbtn smithplatesSqlServiceIr/test` |
| Query rendering | `modules/smithplates-sql-service-query-renderer*` | `sbtn smithplatesSqlServiceQueryRenderer/test`, … |
| DDL rendering | `modules/smithplates-sql-ddl-renderer-*/` | `sbtn smithplatesSqlDdlRendererPostgres/test`, … |
| SSP rendering engine | `modules/smithplates-sql-service-renderer/` | `sbtn smithplatesSqlServiceRenderer/test` |
| HTTP IR and transforms | `modules/smithplates-http-ir/` | `sbtn smithplatesHttpIr/test` |
| HTTP SSP rendering | `modules/smithplates-http-service-renderer/` | `sbtn smithplatesHttpServiceRenderer/test` |
| Template precompilation | `modules/smithplates-scalate-precompiler/` | covered by renderer compile/test tasks |
| Published plugin | `modules/smithplates-plugin/` | `sbtn smithplatesPlugin/test` |
| Dialect IT | `modules/smithplates-sql-ddl-renderer-*-it/` | `sbtn smithplatesSqlDdlRendererPostgresIt/test`, … |

**Conventions:** see [`AGENTS.md`](AGENTS.md) (Scala 3.3.6, strict options, `sbtn`, functional validation with `ValidatedNel`). Pre-commit hooks (`scripts/pre-commit-scala.sh`) run scalafmt, scalafix, and compile on staged Scala/SBT changes.

**Typical loop:**

1. `./scripts/run-tests.sh scala` (or the specific module test above).
2. For SQL rendering changes, ensure Docker IT modules pass.
3. `sbtn publishM2` and validate in a consumer `smithy build` when plugin wiring changes.

Template **content** (Scalate SSP output for target languages) is maintained separately under `templates/` — see below.

---

## Template authors (codegen output)

Work on **target-language artifacts** produced from `@sqlService` models: SSP templates, golden expected outputs, and language test harnesses.

### Layout

```
templates/
  <language>/                 # e.g. python
    src/<feature>/            # SSP sources (bundled into the plugin JAR)
    tests/
      <test-case>/
        smithy/smithy-files.smithy
        smithy-build.json
        expected/             # golden render + execution fixtures
language-test-harnesses/
  <language>/                 # ruff/mypy/pytest runners for golden expected/ trees
```

Bundled Python DB templates live under [`templates/python/src/db/`](templates/python/src/db/). Golden render comparisons use [`templates/python/tests/`](templates/python/tests/) (`expected/` under each case); execution checks use [`language-test-harnesses/python/`](language-test-harnesses/python/).

### Updating bundled Python templates

1. Edit SSP under `templates/python/src/db/` (and `fragments/`).
2. Run `./scripts/run-template-golden-tests.sh` — compares rendered output to golden files under `tests/<case>/expected/`.
3. Refresh goldens when output changes intentionally: `sbtn 'generateGoldenTemplatesFor python <case-name> [<case-name> ...]'` (see [`templates/python/tests/README.md`](templates/python/tests/README.md)).
4. Run `./language-test-harnesses/python/run-linters.sh` then `./language-test-harnesses/python/run-tests.sh` (or `./scripts/run-linters.sh templates` / `./scripts/run-tests.sh templates`).

Wire template resources in root [`build.sbt`](build.sbt) (`Compile` / `Test` `unmanagedResourceDirectories`).

### Adding a new language

1. Add `templates/<language>/src/<feature>/` with SSP templates mirroring the artifact layout expected by [`SqlServiceCodegenDbArtifacts`](modules/smithplates-sql-service-renderer/src/main/scala/com/jacoby6000/smithplates/sql/service/renderer/SqlServiceCodegenDbArtifacts.scala).
2. Register bundled templates in the plugin if publishing built-in support (`LanguageTargetTemplateValidator`, `build.sbt` resources).
3. Add golden cases under `templates/<language>/tests/<test-case>/`.
4. Add a harness under `language-test-harnesses/<language>/` with `run-linters.sh` and `run-tests.sh`; extend [`scripts/run-linters.sh`](scripts/run-linters.sh) and [`scripts/run-tests.sh`](scripts/run-tests.sh) pick up new languages automatically.
5. Add the new language's [`CodegenTemplateVariant`](modules/smithplates-sql-service-renderer/src/test/scala/com/jacoby6000/smithplates/sql/service/renderer/codegentest/CodegenTemplateVariant.scala)s to [`CodegenTemplateTestSuite`](modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/codegentest/CodegenTemplateTestSuite.scala) (discovery, build, and comparison registration are grouped by `languageId`).

Consumers can also point `smithplates.<language>.sql.templateDirectory` at their own template tree; bundled languages use default `classpath:` (see [`docs/usage/integration.md`](docs/usage/integration.md)).

### Postgres mypy stubs

Generated postgres integration tests import `testcontainers`. Bundled stubs ship at `test/db/postgres/stubs/testcontainers/postgres.pyi`; add `<testOutputDir>/db/postgres/stubs` to `mypy_path` in consumer projects.

---

## Documentation and reusable components

Markdown under `docs/reusable-components/` is embedded into other docs via [`scripts/sync_reusable_components.py`](scripts/sync_reusable_components.py). After editing a component, run the sync script and re-stage affected Markdown files.
