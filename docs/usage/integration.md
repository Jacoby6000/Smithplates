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
| HTTP service trait | `0.1.0` (bundled in `smithplates-plugin`) | `com.jacoby6000:smithplates-plugin:0.1.0` supplies `@httpService` via classpath trait discovery |

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

HTTP service codegen is configured under `http` (parallel to `sql`). At least one of `sql` or `http` must be present in plugin settings.

Unlike SQL, HTTP settings do **not** use a nested `languageTargets` map or per-dialect `enable` flags. Top-level keys under `http` are **language ids** (for example `python`); each language entry contains a `server` object with web-framework and output settings.

```json
"http": {
  "python": {
    "server": {
      "webFramework": "fastapi",
      "sourceOutputDir": "src/generated",
      "testOutputDir": "tests",
      "packageName": "generated.rendering_pipeline_api"
    }
  }
}
```

### `smithplates.http.<language>.server`

Map of language id → HTTP language configuration (for example `python`). Each language entry requires a `server` object. Controls **HTTP service codegen** (`@httpService` service IR + Scalate SSP templates → route modules, protocols, app wiring, and response dispatch helpers). The `server` object supports:

| Field | Required | Default | Purpose |
|-------|----------|---------|---------|
| `webFramework` | No | `fastapi` | Python web framework for generated route and protocol templates. Only `fastapi` is supported today; additional frameworks (for example `flask`) will use the same field when added. |
| `templateDirectory` | When language is not bundled | `classpath:` for bundled `python` only (templates packaged from [`templates/python/src/http/`](../../templates/python/src/http/)) | Classpath prefix for Scalate SSP templates; required for languages without bundled templates, and must contain all templates required by `webFramework`. |
| `sourceOutputDir` | Yes | — | Base directory for generated src artifacts |
| `testOutputDir` | Yes | — | Base directory for generated test artifacts |
| `packageName` | No | `generated.api` | Python import root for generated modules (for example `generated.rendering_pipeline_api`) |

Example output layout for bundled FastAPI templates (paths relative to `build/smithy/source/smithplates/`; sources under `templates/python/src/http/`):

- `build/smithy/source/smithplates/<sourceOutputDir>/api/app_factory.py`
- `build/smithy/source/smithplates/<sourceOutputDir>/api/app_services.py`
- `build/smithy/source/smithplates/<sourceOutputDir>/api/operation_bindings.py`
- `build/smithy/source/smithplates/<sourceOutputDir>/api/apis/<route_group>_api.py` (per Smithy `@tags` route group)
- `build/smithy/source/smithplates/<sourceOutputDir>/api/apis/<route_group>_api_base.py`

Copy or project artifacts from the Smithy build output tree into your repository layout as needed.

Annotate HTTP API services with `@httpService` (`use smithplates.codegen.http#httpService`). The trait accepts an optional `serialization` field (default `"json"`). Operations use Smithy `@http` bindings and `@tags` for route grouping. Output structures may declare a fixed response header with `@httpStaticHeader` (`use smithplates.codegen.http#httpStaticHeader`).

Service error structures with `@error` may use `@httpProblem` (`use smithplates.codegen.http#httpProblem`) to emit RFC 9457 `application/problem+json` exception classes and imply `Content-Type: application/problem+json` on operation error response bindings. Set `code` on `@httpProblem` to imply `@httpError` with the same status (otherwise declare `@httpError` separately). Set `type` to an HTTPS URL documenting the error (defaults to `about:blank`; smithplates warns when `type` is not HTTPS). Provide `title` and optional trait `detail` defaults; raise the generated exception with `detail=` and `instance=` to describe a specific occurrence (for example trace identifiers).

#### OpenAPI Generator coordination

When Smithy OpenAPI export and [OpenAPI Generator `python-fastapi`](https://openapi-generator.tech/docs/generators/python-fastapi) also run in the consumer pipeline (as in the rendering-pipeline reference layout under `extra-context/generated-db-schemas-and-models/`), keep these settings aligned:

| Smithplates `http` field | OpenAPI Generator / consumer setting | Notes |
|--------------------------|--------------------------------------|-------|
| `server.packageName` | `additionalProperties.packageName` in `openapi-generator-server-config.yaml` | Must match so imports resolve across hand-written server code, OAG output, and smithplates wiring. Example: `generated.rendering_pipeline_api`. |
| `server.sourceOutputDir` | `outputDir` + `sourceFolder` in OAG config | Smithplates writes under `build/smithy/source/smithplates/<sourceOutputDir>/`. OAG typically uses `outputDir: src` with `sourceFolder: .`, placing modules under `src/<package path>/`. Copy smithplates artifacts into the same tree after `smithy build`. |
| `server.webFramework: fastapi` | `generatorName: python-fastapi` | Selects bundled FastAPI SSP templates under `templates/python/src/http/fastapi/`. |

Smithplates HTTP codegen replaces the OpenAPI Generator wiring layer for FastAPI servers: `app_factory.py`, `app_services.py`, `api_response.py`, `operation_bindings.py`, route modules (`*_api.py`), and protocol modules (`*_api_base.py`). OpenAPI Generator (with custom Mustache templates) may still emit Pydantic `models/` and depends on Smithy OpenAPI export extensions such as `x-python-response-type` for protocol response unions. Pin OpenAPI Generator version in the consumer (for example `openapitools.json`) and keep custom templates in sync when upgrading.

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
| `smithplates` | `build/smithy/source/smithplates/<sourceOutputDir>` and `.../<testOutputDir>` | HTTP service codegen | FastAPI route modules, protocols, app wiring, and response dispatch helpers per `@httpService` service |

The `@httpService` trait ships in `smithplates-plugin`; consumers do not need a separate AWS protocol traits dependency for HTTP codegen.

See [Architecture](../contributing/architecture.md) for the full codegen pipeline.

See [SQL plugin](sql-plugin.md) for plugin behavior.

## SPI registration

The SQL plugin registers as `com.jacoby6000.smithplates.plugin.SmithplatesBuildPlugin`.
