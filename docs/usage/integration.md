# Integration

Smithplates plugins are consumed as Maven JARs during `smithy build`. Models and `smithy-build.json` live in the **consumer** repository, not in Smithplates.

## Local development

1. From Smithplates: `sbtn publishM2`
2. From the consumer Smithy project: `smithy build`

Republish after every plugin source change. JARs are written under:

- `~/.m2/repository/com/jacoby6000/smithplates-plugin/0.1.0/`

## Version alignment

Keep these in sync across Smithplates and every consumer:

| Artifact | Version in `build.sbt` | `smithy-build.json` coordinate |
|----------|----------------------|--------------------------------|
| SQL plugin | `0.1.0` | `com.jacoby6000:smithplates-plugin:0.1.0` |
| Smithy toolchain | `1.71.0` (in plugin `libraryDependencies`) | Matching Smithy CLI |

## `smithy-build.json` example

Configure Smithplates under the `smithplates` plugin key. SQL settings live under `sql`.

```json
{
  "version": "1.0",
  "sources": ["example"],
  "maven": {
    "dependencies": [
      "com.jacoby6000:smithplates-plugin:0.1.0"
    ]
  },
  "plugins": {
    "smithplates": {
      "sql": {
        "sqlite": {
          "enable": true,
          "migrationLocation": "db/sqlite.sql"
        },
        "postgres": {
          "enable": true,
          "migrationLocation": "db/postgres.sql"
        },
        "languageTargets": {
          "python": {
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests"
          }
        }
      }
    }
  }
}
```

An `api` section will be added under `smithplates` in a future release.

### `smithplates.sql` dialect keys

Dialect configuration controls the **schema and migrations** path (SQL IR → dialect-specific DDL). Migration files are written today; per-language migration engines are planned ([#2](https://github.com/Jacoby6000/Smithplates/issues/2)).

| Key | Purpose |
|-----|---------|
| `sqlite` | SQLite DDL export |
| `postgres` | Postgres DDL export |

Each dialect object supports:

| Field | Required | Default | Purpose |
|-------|----------|---------|---------|
| `enable` | No | `false` | When `true`, render SQL IR to DDL and derived DML for this dialect |
| `migrationLocation` | When `enable` is `true` | — | Output path for the generated `.sql` migration file |

### `smithplates.sql.languageTargets`

Map of language id → language target configuration (for example `python`). Controls **SQL database service codegen** (database services and operations IR + SQL IR + Scalate SSP templates → query models, interfaces, dialect-specific implementations, and test suites). Each entry supports:

| Field | Required | Default | Purpose |
|-------|----------|---------|---------|
| `templateDirectory` | When language is not bundled | `classpath:` for bundled `python` only (templates packaged from [`templates/python/src/db/`](../../templates/python/src/db/)) | Classpath prefix for Scalate SSP templates; required for languages without bundled templates, and must contain all top-level templates required by enabled dialects. Bundled Python templates also use a `fragments/` tree referenced from main templates via `<% include("fragments/...") %>` / `<% render("fragments/...", Map(...)) %>`. |
| `sourceOutputDir` | Yes | — | Base directory for generated src artifacts |
| `testOutputDir` | Yes | — | Base directory for generated test artifacts |

When a language target is configured, bundled `db` service-type templates are selected automatically from enabled dialects. Users do not list individual `artifacts` entries.

Example output layout for bundled templates (paths relative to `build/smithy/source/smithplates/`; sources under `templates/python/src/db/`):

- `build/smithy/source/smithplates/<sourceOutputDir>/db/model/{{serviceFileName}}_models.py`
- `build/smithy/source/smithplates/<sourceOutputDir>/db/{{serviceFileName}}_protocol.py`
- `build/smithy/source/smithplates/<sourceOutputDir>/db/sqlite/{{serviceFileName}}_aiosqlite.py` (when `sqlite.enable` is `true`)
- `build/smithy/source/smithplates/<testOutputDir>/db/sqlite/test_{{serviceFileName}}_derived_sql.py` (when `sqlite.enable` is `true`)

Copy or project artifacts from the Smithy build output tree into your repository layout as needed.

### Plugin outputs

All plugin file-manifest paths are relative to **`build/smithy/source/smithplates/`** (the Smithy projection directory for the `smithplates` plugin).

| Plugin | Build output directory | Pipeline path | Contents |
|--------|------------------------|---------------|----------|
| `smithplates` | `build/smithy/source/smithplates/` | Schema and migrations | Dialect `.sql` files at each `migrationLocation` |
| `smithplates` | `build/smithy/source/smithplates/<sourceOutputDir>` and `.../<testOutputDir>` | SQL database service codegen | Query models, interfaces, dialect-specific implementations, and derived-query integration tests |

See [Architecture](../contributing/architecture.md) for the full codegen pipeline.

See [SQL plugin](sql-plugin.md) for plugin behavior.

## SPI registration

The SQL plugin registers as `com.jacoby6000.smithplates.plugin.SmithplatesBuildPlugin`.
