# Integration

Smithplates plugins are consumed as Maven JARs during `smithy build`. Models and `smithy-build.json` live in the **consumer** repository, not in Smithplates.

For the task-oriented user guide, start with [Getting started](getting-started.md), [Configuration](configuration.md), [SQL plugin](sql-plugin.md), and [HTTP plugin](http-plugin.md). This page remains the combined integration reference.

## Local development

1. From Smithplates: `sbtn publishM2`
2. From the consumer Smithy project: `smithy build`

Republish after every plugin source change. Consumers reference only the `smithplates-plugin` coordinate, but `publishM2` publishes the plugin **and its full transitive dependency graph** so Maven can resolve the dependency jars — including the renderer jars that carry precompiled SSP template classes (see [Architecture → Template precompilation](../contributing/architecture.md#template-precompilation)). JARs are written under `~/.m2/repository/com/jacoby6000/` using the current build version from `sbtn print smithplatesPlugin/version`.

## Version alignment

Keep the Smithplates plugin version in the consumer `smithy-build.json` aligned with the build you installed (`sbtn print smithplatesPlugin/version` after `publishM2`, or the exact coordinate from a release or snapshot publish):

| Artifact | Version source | `smithy-build.json` coordinate |
|----------|----------------|----------------------------------|
| SQL plugin | `sbtn print smithplatesPlugin/version` | `com.jacoby6000:smithplates-plugin:<version>` |
| Smithy toolchain | `1.71.0` (in plugin `libraryDependencies`) | Matching Smithy CLI |
| HTTP service trait | bundled in `smithplates-plugin` | `@httpService` via classpath trait discovery |

## `smithy-build.json` example

Configure Smithplates under the `smithplates` plugin key. SQL settings live under `sql`. Set the Maven dependency version to the output of `sbtn print smithplatesPlugin/version` (after `publishM2`) or the exact release/snapshot coordinate you depend on.

```json
{
  "version": "1.0",
  "sources": ["example"],
  "maven": {
    "dependencies": [
      "com.jacoby6000:smithplates-plugin:<version>"
    ]
  },
  "plugins": {
    "smithplates": {
      "sql": {
        "sqlite": {
          "enable": true,
          "migrationLocation": "db/migrations/sqlite"
        },
        "postgres": {
          "enable": true,
          "migrationLocation": "db/migrations/postgres"
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

Smithplates ships Smithy build projection transforms for OpenAPI and other Smithy tooling that only understands standard traits:

| Transform | Purpose |
|-----------|---------|
| `applyHttpProblemHttpError` | Materializes implied `@httpError` from `@httpProblem(code: ...)`. The smithplates build plugin applies the same logic before HTTP extraction. |
| `applyHttpServiceRestJson1` | Adds `@restJson1` to services that declare `@httpService`, so you do not need a separate OpenAPI-only service shape. |
| `stripSmithplatesHttpCodegenTraits` | Removes smithplates HTTP codegen traits (`@httpService`, `@httpProblem`, `@httpStaticHeader`) from the transformed projection model after implied traits are materialized. Required when the projection also loads the original Smithy sources; otherwise Smithy reports conflicting `@httpService` traits during merge validation. |

List these transforms in `smithy-build.json` **before** the OpenAPI plugin when exporting `@httpService` models that use `@httpProblem` or other smithplates HTTP traits:

```json
{
  "version": "1.0",
  "projections": {
    "openapi": {
      "transforms": [
        { "name": "applyHttpProblemHttpError" },
        { "name": "applyHttpServiceRestJson1" },
        { "name": "stripSmithplatesHttpCodegenTraits" }
      ],
      "plugins": {
        "openapi": {
          "service": "example.api#ExampleService",
          "protocol": "aws.protocols#restJson1"
        }
      }
    }
  }
}
```

The OpenAPI projection still needs `software.amazon.smithy:smithy-aws-traits` on the build classpath for `@restJson1`. Transforms are registered via SPI on the smithplates plugin classpath (`com.jacoby6000:smithplates-plugin` and its dependencies). For programmatic use outside `smithy build`, call the corresponding `*ModelTransformer.transform(model)` helpers in the smithplates HTTP IR module.

#### OpenAPI Generator coordination

When Smithy OpenAPI export and [OpenAPI Generator `python-fastapi`](https://openapi-generator.tech/docs/generators/python-fastapi) also run in the consumer pipeline (as in the rendering-pipeline reference layout under `extra-context/generated-db-schemas-and-models/`), keep these settings aligned:

| Smithplates `http` field | OpenAPI Generator / consumer setting | Notes |
|--------------------------|--------------------------------------|-------|
| `server.packageName` | `additionalProperties.packageName` in `openapi-generator-server-config.yaml` | Must match so imports resolve across hand-written server code, OAG output, and smithplates wiring. Example: `generated.rendering_pipeline_api`. |
| `server.sourceOutputDir` | `outputDir` + `sourceFolder` in OAG config | Smithplates writes under `build/smithy/source/smithplates/<sourceOutputDir>/`. OAG typically uses `outputDir: src` with `sourceFolder: .`, placing modules under `src/<package path>/`. Copy smithplates artifacts into the same tree after `smithy build`. |
| `server.webFramework: fastapi` | `generatorName: python-fastapi` | Selects bundled FastAPI SSP templates under `templates/python/src/http/fastapi/`. |

Smithplates HTTP codegen replaces the OpenAPI Generator wiring layer for FastAPI servers: `app_factory.py`, `app_services.py`, `api_response.py`, `operation_bindings.py`, route modules (`*_api.py`), and protocol modules (`*_api_base.py`). OpenAPI Generator (with custom Mustache templates) may still emit Pydantic `models/` and depends on Smithy OpenAPI export extensions such as `x-python-response-type` for protocol response unions. Pin OpenAPI Generator version in the consumer (for example `openapitools.json`) and keep custom templates in sync when upgrading.

### HTTP and SQL model separation

When a project uses both `smithplates.http` and `smithplates.sql`, **keep HTTP API models and database models in separate Smithy namespaces** and avoid coupling them in the Smithy model.

| Layer | Smithy namespace (example) | Traits | Purpose |
|-------|----------------------------|--------|---------|
| HTTP API | `example.api` | `@httpService`, `@http`, `@tags` | Wire contract, request/response shapes, HTTP errors |
| Database | `example.db` | `@sqlTable`, `@sqlService`, `@sqlDerive*` | Tables, repository operations, column/JSON types |

**Conventions:**

- Do **not** put `@httpService` operations and `@sqlTable` / `@sqlService` shapes in the same namespace, and do **not** reuse the same structure or enum shapes across HTTP and SQL (even when field names match). HTTP payloads and persistence models evolve on different schedules; sharing Smithy shapes ties codegen and migrations to the wire format.
- Duplicate enums and value types per namespace when both layers need similar concepts (for example `PetStatus` in `example.api` and `example.db`). It is fine for them to differ (API `PetAttributeList` vs DB `PetTags`, and so on).
- Translate between HTTP and SQL in **hand-written application code** (protocol implementations, repository facades, mappers). Generated HTTP route modules should call into generated repository services through that boundary, not by importing one generated model tree from the other.
- OpenAPI export projections should list **API sources only** (for example `api-types.smithy`, `api.smithy`, `http-service.smithy`) and target the HTTP service shape id (for example `example.api#ExampleService`). Do not include SQL Smithy files in OpenAPI projections.

The [Python petstore reference](../../example/python/) demonstrates this layout: `petstore.api` for HTTP/OpenAPI codegen and `petstore.db` for schema/repository codegen, with mapping in `src/server/repository_service.py`.

### `smithplates.sql` dialect keys

Dialect configuration controls the **schema and migrations** path (SQL IR → dialect-specific DDL). Versioned migration `.sql` files are written at build time; generated migration services apply them at runtime and track schema state in `_smithplates_migrations`.

| Key | Purpose |
|-----|---------|
| `sqlite` | SQLite DDL export |
| `postgres` | Postgres DDL export |

Each dialect object supports:

| Field | Required | Default | Purpose |
|-------|----------|---------|---------|
| `enable` | No | `false` | When `true`, render SQL IR to DDL migration files for this dialect and enable dialect-specific SQL service artifacts for configured language targets |
| `migrationLocation` | When `enable` is `true` | — | Output directory for versioned migration `.sql` files (for example `db/migrations/sqlite`; initial schema is written as `v1_initial_schema.sql`) |

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
| `smithplates` | `build/smithy/source/smithplates/` | Schema and migrations | Versioned migration `.sql` files under each dialect `migrationLocation` |
| `smithplates` | `build/smithy/source/smithplates/<sourceOutputDir>` and `.../<testOutputDir>` | SQL database service codegen | Query models, interfaces, dialect-specific implementations, and derived-query integration tests |
| `smithplates` | `build/smithy/source/smithplates/<sourceOutputDir>` and `.../<testOutputDir>` | HTTP service codegen | FastAPI route modules, protocols, app wiring, and response dispatch helpers per `@httpService` service |

The `@httpService` trait ships in `smithplates-plugin`; consumers do not need a separate AWS protocol traits dependency for HTTP codegen.

See [Architecture](../contributing/architecture.md) for the full codegen pipeline.

See [SQL plugin](sql-plugin.md) for plugin behavior.

## SPI registration

The SQL plugin registers as `com.jacoby6000.smithplates.plugin.SmithplatesBuildPlugin`.
