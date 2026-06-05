# Integration

SmithyStache plugins are consumed as Maven JARs during `smithy build`. Models and `smithy-build.json` live in the **consumer** repository, not in SmithyStache.

## Local development

1. From SmithyStache: `sbtn publishM2`
2. From the consumer Smithy project: `smithy build`

Republish after every plugin source change. JARs are written under:

- `~/.m2/repository/com/jacoby6000/smithy-sql-plugin/0.1.0/`

## Version alignment

Keep these in sync across SmithyStache and every consumer:

| Artifact | Version in `build.sbt` | `smithy-build.json` coordinate |
|----------|----------------------|--------------------------------|
| SQL plugin | `0.1.0` | `com.jacoby6000:smithy-sql-plugin:0.1.0` |
| Smithy toolchain | `1.71.0` (in plugin `libraryDependencies`) | Matching Smithy CLI |

## `smithy-build.json` example

Configure SmithyStache under the `smithy-stache` plugin key. SQL settings live under `sql`.

```json
{
  "version": "1.0",
  "sources": ["example"],
  "maven": {
    "dependencies": [
      "com.jacoby6000:smithy-sql-plugin:0.1.0"
    ]
  },
  "plugins": {
    "smithy-stache": {
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

An `api` section will be added under `smithy-stache` in a future release.

### `smithy-stache.sql` dialect keys

| Key | Purpose |
|-----|---------|
| `sqlite` | SQLite migration export |
| `postgres` | Postgres migration export |

Each dialect object supports:

| Field | Required | Default | Purpose |
|-------|----------|---------|---------|
| `enable` | No | `false` | When `true`, export DDL and queries for this dialect |
| `migrationLocation` | When `enable` is `true` | — | Output path for the generated `.sql` file |

### `smithy-stache.sql.languageTargets`

Map of language id → language target configuration (for example `python`). Each entry supports:

| Field | Required | Default | Purpose |
|-------|----------|---------|---------|
| `templateDirectory` | When language is not bundled | `classpath:sql-service-codegen/<languageId>` for bundled `python` only | Classpath prefix for Mustache templates; required for languages without bundled templates, and must contain all templates required by enabled dialects |
| `sourceOutputDir` | Yes | — | Base directory for generated src artifacts |
| `testOutputDir` | Yes | — | Base directory for generated test artifacts |

When a language target is configured, bundled `db` service-type templates are selected automatically from enabled dialects. Users do not list individual `artifacts` entries.

Example output layout for bundled templates under `sql-service-codegen/python/db/`:

- `sourceOutputDir/db/model/{{serviceFileName}}_models.py`
- `sourceOutputDir/db/{{serviceFileName}}_protocol.py`
- `sourceOutputDir/db/sqlite/{{serviceFileName}}_aiosqlite.py` (when `sqlite.enable` is `true`)
- `testOutputDir/db/sqlite/test_{{serviceFileName}}_derived_sql.py` (when `sqlite.enable` is `true`)

### Plugin outputs

| Plugin | Build output directory | Contents |
|--------|------------------------|----------|
| `smithy-stache` | `build/smithy/source/smithy-stache/` | SQL migrations and codegen artifacts at configured paths |

See [SQL plugin](sql-plugin.md) for plugin behavior.

## SPI registration

The SQL plugin registers as `com.jacoby6000.smithy.stache.SmithyStacheBuildPlugin`.
