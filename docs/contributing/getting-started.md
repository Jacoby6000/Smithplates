# Getting started

## Prerequisites

| Tool | Purpose |
|------|---------|
| [sbtn](https://www.scala-sbt.org/) | SBT thin client (`coursier install sbtn` or `cs install sbtn`) |
| [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/cli.html) | Validate models and run `smithy build` in consumer projects (via Coursier) |
| [Docker](https://www.docker.com/) | Required only for dialect integration tests |
| [uv](https://docs.astral.sh/uv/) | Required for Python language test harness (`language-test-harnesses/python/`) |
| [pre-commit](https://pre-commit.com/) | Optional; installs git hooks for fmt/fix/compile |

Assume `sbtn` is already on `PATH`. Run commands from the Smithplates repository root.

## Modules

| SBT project | Directory | Maven coordinate | Role |
|-------------|-----------|------------------|------|
| `smithplatesPlugin` | [`modules/smithplates-plugin/`](../../modules/smithplates-plugin/) | `com.jacoby6000:smithplates-plugin` (version from `sbtn print smithplatesPlugin/version`) | Published `smithplates` build plugin (orchestration only) |
| `smithplatesSqlIr` | [`modules/smithplates-sql-ir/`](../../modules/smithplates-sql-ir/) | — | Schema IR, table extraction |
| `smithplatesSqlDdlRendererCommon` | [`modules/smithplates-sql-ddl-renderer-common/`](../../modules/smithplates-sql-ddl-renderer-common/) | — | Shared DDL rendering (`SqlSchemaDdlRenderer`, `SqlShared`) |
| `smithplatesSqlServiceIr` | [`modules/smithplates-sql-service-ir/`](../../modules/smithplates-sql-service-ir/) | — | Query/service IR, extractors |
| `smithplatesSqlServiceQueryRenderer` | [`modules/smithplates-sql-service-query-renderer/`](../../modules/smithplates-sql-service-query-renderer/) | — | `SqlQueryRenderer` trait, parameterized statements, query output types |
| `smithplatesSqlServiceQueryRendererCommon` | [`modules/smithplates-sql-service-query-renderer-common/`](../../modules/smithplates-sql-service-query-renderer-common/) | — | Shared dialect-neutral query rendering (`SqlQueryRendering`) |
| `smithplatesSqlServiceQueryRendererPostgres` | [`modules/smithplates-sql-service-query-renderer-postgres/`](../../modules/smithplates-sql-service-query-renderer-postgres/) | — | Postgres `SqlQueryRenderer` |
| `smithplatesSqlServiceQueryRendererSqlite` | [`modules/smithplates-sql-service-query-renderer-sqlite/`](../../modules/smithplates-sql-service-query-renderer-sqlite/) | — | SQLite `SqlQueryRenderer` |
| `smithplatesSqlDdlRendererPostgres` | [`modules/smithplates-sql-ddl-renderer-postgres/`](../../modules/smithplates-sql-ddl-renderer-postgres/) | — | Postgres DDL renderer |
| `smithplatesSqlDdlRendererSqlite` | [`modules/smithplates-sql-ddl-renderer-sqlite/`](../../modules/smithplates-sql-ddl-renderer-sqlite/) | — | SQLite DDL renderer |
| `smithplatesSqlServiceRenderer` | [`modules/smithplates-sql-service-renderer/`](../../modules/smithplates-sql-service-renderer/) | — | Scalate SSP service codegen (Python templates) |
| `smithplatesTestkit` | [`modules/smithplates-testkit/`](../../modules/smithplates-testkit/) | — | Shared Smithy fixtures and JDBC DDL test helpers (`src/main`) |
| `smithplatesSqlDdlRendererPostgresIt` | [`modules/smithplates-sql-ddl-renderer-postgres-it/`](../../modules/smithplates-sql-ddl-renderer-postgres-it/) | — | Postgres renderer integration tests |
| `smithplatesSqlDdlRendererSqliteIt` | [`modules/smithplates-sql-ddl-renderer-sqlite-it/`](../../modules/smithplates-sql-ddl-renderer-sqlite-it/) | — | SQLite renderer integration tests |

## Build and publish

### Local Maven (`publishM2`)

Publish plugin JARs to the local Maven repository (`~/.m2`) before running `smithy build` in a consumer project:

```bash
sbtn publishM2
```

Consumer `smithy-build.json` files reference `com.jacoby6000:smithplates-plugin`. Version numbers must match the current build (see [Maven Central](#maven-central) below for release versioning).

### Maven Central

Releases are automated with [`sbt-ci-release`](https://github.com/sbt/sbt-ci-release) via [`.github/workflows/release.yml`](../../.github/workflows/release.yml):

- **Stable release:** push an annotated tag whose name starts with `v` (for example `v1.0.0`).
- **Snapshot:** every push to `main` publishes a unique `-SNAPSHOT` version derived from git history (`sbt-dynver`).
- **PR snapshot:** a maintainer can comment exactly `!release` on a pull request whose head branch lives on this repository. CI publishes a hash snapshot of the PR head commit and replies with Maven coordinates. Maintainers can also run the **PR release** workflow manually from the Actions tab (`workflow_dispatch`) and choose the target branch from the **Use workflow from** dropdown.

Only `smithplates-plugin` is the consumer-facing artifact (`com.jacoby6000:smithplates-plugin`); its compile dependency modules are also published so the plugin POM resolves on Maven Central. Test and integration modules set `publish / skip := true`.

`!release` is limited to repository collaborators (OWNER, MEMBER, or COLLABORATOR) and does not run for fork-head pull requests.

#### GitHub Actions secrets

Configure these under **Settings → Secrets and variables → Actions**:

| Secret | Purpose |
|--------|---------|
| `SONATYPE_USERNAME` | Username from a [Central Portal user token](https://central.sonatype.com/usertoken) (not your account login) |
| `SONATYPE_PASSWORD` | Password from the same user token |
| `PGP_PASSPHRASE` | Passphrase for the GPG key used to sign artifacts |
| `PGP_SECRET` | Base64-encoded armored private key (`gpg --armor --export-secret-keys <key-id> \| base64 -w0`) |

Upload the matching **public** key to a keyserver (for example [keys.openpgp.org](https://keys.openpgp.org/)) before the first release.

#### Consuming PR or main snapshots

Add the Central Portal snapshots resolver and pin the exact version string from the publish log or the `!release` bot comment:

```scala
resolvers += Resolver.sonatypeCentralSnapshots
libraryDependencies += "com.jacoby6000" % "smithplates-plugin" % "0.0.0+3-abc123def-SNAPSHOT"
```

#### Sonatype namespace

Publishing requires approval for the Maven `groupId` `com.jacoby6000` on [central.sonatype.com](https://central.sonatype.com/). If you do not control that domain, request `io.github.Jacoby6000` instead and update `organization` in [`build.sbt`](../../build.sbt).

#### Manual publish (optional)

For a one-off local release with credentials in `~/.sbt/sonatype_central_credentials`:

```bash
export SONATYPE_USERNAME=...
export SONATYPE_PASSWORD=...
export PGP_PASSPHRASE=...
export PGP_SECRET=...
sbtn smithplatesPlugin/publishSigned
```

## Lint and format

Requires the sbt plugins in [`project/plugins.sbt`](../../project/plugins.sbt) (`sbtn` downloads them on first run):

```bash
sbtn scalafmtCheckAll
sbtn 'scalafixAll --check'
```

Apply fixes locally with `sbtn scalafmtAll` and `sbtn scalafixAll`.

## Pre-commit hooks

Install [pre-commit](https://pre-commit.com/) once, then enable hooks for this repository:

```bash
pre-commit install
```

On each commit that touches `*.scala` or `*.sbt`, hooks run (via [`scripts/pre-commit-scala.sh`](../../scripts/pre-commit-scala.sh)):

1. `sbtn scalafmtAll`
2. `sbtn scalafixAll`
3. `sbtn compile`
4. `scripts/sync_reusable_components.py --check` when `docs/reusable-components/` or embedded component markers change

If scalafmt or scalafix change files, stage the updates and commit again. After editing files under [`docs/reusable-components/`](../reusable-components/), run `scripts/sync_reusable_components.py` and re-stage the updated Markdown files. Run all hooks manually with `pre-commit run --all-files`.

## Linters

Run Scala and template-language linters/compilers (see [CONTRIBUTING.md](../../CONTRIBUTING.md) for Nix and Docker options):

```bash
./scripts/run-linters.sh
```

Subcommands: `scala` (scalafmt, scalafix, compile), `templates` (Python ruff + mypy).

## Tests

Run test suites only (linters are separate):

```bash
./scripts/run-tests.sh
```

Subcommands: `scala` (aggregated `sbtn test`), `templates` (Python pytest only).

## Unit tests

```bash
sbtn smithplatesSqlIr/test
sbtn smithplatesSqlServiceIr/test
sbtn smithplatesSqlDdlRendererPostgres/test
sbtn smithplatesSqlDdlRendererSqlite/test
sbtn smithplatesSqlServiceRenderer/test
sbtn smithplatesPlugin/test
```

## Integration tests

Requires Docker:

```bash
sbtn smithplatesSqlDdlRendererPostgresIt/test
sbtn smithplatesSqlDdlRendererSqliteIt/test
```

Python generated-code integration tests (pytest against `templates/python/tests/<case>/expected/`) require [uv](https://docs.astral.sh/uv/) and Docker for postgres variants:

```bash
./language-test-harnesses/python/run-tests.sh
```

See [Integration tests](integration-tests.md) for coverage and module layout.

## Typical workflow

1. Change plugin sources in Smithplates.
2. Run `sbtn publishM2`.
3. Run `smithy build` in the consumer Smithy project (models and `smithy-build.json` live in that repo).
4. Run unit tests on affected modules and, when SQL rendering changes, dialect IT modules.
