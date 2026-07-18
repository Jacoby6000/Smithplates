# Integration

Smithplates plugins are consumed as Maven JARs during `smithy build`. Models and `smithy-build.json` live in the **consumer** repository, not in Smithplates.

For the task-oriented user guide, start with [Getting started](getting-started.md), [Configuration](configuration.md) (canonical settings matrix), [SQL plugin](sql-plugin.md), and [HTTP plugin](http-plugin.md). This page is the combined walkthrough: version alignment, a full SQL+HTTP example, OpenAPI coordination, and HTTP/SQL namespace separation.

## Local development

1. From Smithplates: `sbtn publishM2`
2. From the consumer Smithy project: `smithy build`

Republish after every plugin source change. Consumers reference only the `smithplates-plugin` coordinate, but `publishM2` publishes the plugin **and its full transitive dependency graph** so Maven can resolve the dependency jars — including the renderer jars that carry precompiled SSP template classes (see [Architecture → Template precompilation](../contributing/architecture.md#template-precompilation)). JARs are written under `~/.m2/repository/com/jacoby6000/` using the current build version from `sbtn print smithplatesPlugin/version`.

## Version alignment

Keep the Smithplates plugin version in the consumer `smithy-build.json` aligned with the build you installed (`sbtn print smithplatesPlugin/version` after `publishM2`, or the exact coordinate from a release or snapshot publish):

| Artifact | Version source | `smithy-build.json` coordinate |
|----------|----------------|----------------------------------|
| SQL / HTTP plugin | `sbtn print smithplatesPlugin/version` | `com.jacoby6000:smithplates-plugin:<version>` |
| Smithy toolchain | `1.71.0` (in plugin `libraryDependencies`) | Matching Smithy CLI |
| HTTP service trait | bundled in `smithplates-plugin` | `@httpService` via classpath trait discovery |

See [`CHANGELOG.md`](../../CHANGELOG.md) for breaking changes between releases (especially the v0.3.0 migration notes).

## Combined `smithy-build.json` example

Configure Smithplates under the `smithplates` plugin key. Each top-level entry under `smithplates` is a language id (`python`, `typescript`, …). Set the Maven dependency version to the output of `sbtn print smithplatesPlugin/version` (after `publishM2`) or the exact release/snapshot coordinate you depend on.

At least one language must contain `sql` or `http`. Full field documentation — including `rootNamespace`, `packageName`, `modelsPackageName`, `additionalTemplatesDirectory`, and `enableExternalTemplates` — lives in [Configuration](configuration.md).

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
      "python": {
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests",
        "sql": {
          "sqlite": {
            "enable": true,
            "migrationLocation": "db/migrations/sqlite"
          },
          "postgres": {
            "enable": true,
            "migrationLocation": "db/migrations/postgres"
          },
          "rootNamespace": "generated"
        },
        "http": {
          "rootNamespace": "generated",
          "server": {
            "webFramework": "fastapi"
          },
          "client": {
            "httpLibrary": "httpx"
          }
        }
      },
      "typescript": {
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests",
        "http": {
          "rootNamespace": "generated",
          "client": {
            "httpLibrary": "fetch"
          }
        }
      }
    }
  }
}
```

### What this generates

| Concern | Config | Typical output (under `build/smithy/source/smithplates/`) |
|---------|--------|----------------------------------------------------------|
| Schema migrations | `python.sql.<dialect>` | Versioned `.sql` files under each `migrationLocation` |
| SQL repositories | `python.sql` | Namespace-aware models, protocol, dialect implementations, migration services, and derived-query tests — see [SQL plugin](sql-plugin.md) |
| FastAPI server | `python.http.server` | Route groups, protocols, app wiring, optional `websocket_routes.py` |
| Python HTTP client | `python.http.client` | httpx route-group clients, registry, optional WebSocket client |
| TypeScript HTTP client | `typescript.http.client` | axios or fetch clients + shared models |
| Shared HTTP models / `HttpProblem` | `http` (server and/or client) | Per-shape models under the Smithy namespace; shared `HttpProblem` under `{rootNamespace}/smithplates/codegen/http/` |

Default Python import packages include the Smithy service namespace (for example `generated.example` for namespace `example`). Explicit `packageName` overrides replace that derived package; filesystem paths still follow the Smithy namespace. See [Configuration — Namespace-aware layout](configuration.md#namespace-aware-layout).

Bundled artifact lists come from each template root's `outputs.json` deck. Users do not list individual artifacts in `smithy-build.json`. To append or override bundled outputs, use `additionalTemplatesDirectory` ([Configuration — Custom codegen outputs](configuration.md#custom-codegen-outputs), [Custom templates](custom-templates.md)).

Copy or project artifacts from the Smithy build output tree into your repository layout as needed.

### Modeling reminders

Annotate HTTP API services with `@httpService` (`use smithplates.codegen.http#httpService`). The trait accepts an optional `serialization` field (default `"json"`). Operations use Smithy `@http` bindings and `@tags` for route grouping. Output structures may declare a fixed response header with `@httpStaticHeader`. Service error structures with `@error` may use `@httpProblem` for RFC 9457 problem details. Bidirectional endpoints use `@websocket` (see [HTTP plugin — Websockets](http-plugin.md#websockets)). Nested payload flattening uses Smithy `@nestedProperties` on a single `@httpPayload` member (see [HTTP plugin — Nested payload bodies](http-plugin.md#nested-payload-bodies)).

## OpenAPI projection transforms

Smithplates ships Smithy build projection transforms for OpenAPI and other Smithy tooling that only understands standard traits:

| Transform | Purpose |
|-----------|---------|
| `applyHttpProblemHttpError` | Materializes implied `@httpError` from `@httpProblem(code: ...)`. The smithplates build plugin applies the same logic before HTTP extraction. |
| `applyHttpServiceRestJson1` | Adds `@restJson1` to services that declare `@httpService`, so you do not need a separate OpenAPI-only service shape. |
| `stripSmithplatesHttpCodegenTraits` | Removes smithplates HTTP codegen traits (`@httpService`, `@httpProblem`, `@httpStaticHeader`) from the transformed projection model after implied traits are materialized. Required when the projection also loads the original Smithy sources; otherwise Smithy reports conflicting `@httpService` traits during merge validation. |

`@websocket` is **not** stripped by `stripSmithplatesHttpCodegenTraits`. Keep WebSocket operations out of OpenAPI projections, or accept that OpenAPI tooling will see the trait unless you filter those shapes yourself.

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

More detail: [OpenAPI](openapi.md).

### OpenAPI Generator coordination

Smithplates HTTP codegen reads Smithy directly and owns the FastAPI server wiring: `app_factory.py`, `app_services.py`, `api_response.py`, `operation_bindings.py`, route modules (`*_api.py`), protocol modules (`*_api_base.py`), and optional `websocket_routes.py`. Smithplates also generates HTTP clients under `smithplates.<language>.http.client` (Python/httpx and TypeScript axios/fetch). Use OpenAPI export when you also need an OpenAPI document for external tooling.

The petstore references use this split:

| Concern | Tool | Output |
|---------|------|--------|
| FastAPI server wiring | Smithplates HTTP server codegen | `example/python/src/generated/petstore/api/` |
| Shared HTTP models | Smithplates HTTP codegen | same namespace root as the server/client |
| httpx async HTTP client | Smithplates HTTP client codegen (Python) | `example/python/src/generated/petstore/api/client/` |
| fetch HTTP client | Smithplates HTTP client codegen (TypeScript) | `example/typescript/src/generated/petstore/api/` |
| OpenAPI document + reference Python client | Smithy OpenAPI plugin + OpenAPI Generator | `example/openapi-reference-python/` |

Keep OpenAPI projections scoped to API Smithy sources only. Do not include SQL Smithy files in the OpenAPI projection, and keep hand-written adapters responsible for mapping generated API models to generated DB models.

## HTTP and SQL model separation

When a project uses both `smithplates.<language>.http` and `smithplates.<language>.sql`, **keep HTTP API models and database models in separate Smithy namespaces** and avoid coupling them in the Smithy model.

| Layer | Smithy namespace (example) | Traits | Purpose |
|-------|----------------------------|--------|---------|
| HTTP API | `example.api` | `@httpService`, `@http`, `@tags`, optional `@websocket` / `@httpProblem` | Wire contract, request/response shapes, HTTP errors |
| Database | `example.db` | `@sqlTable`, `@sqlService`, `@sqlDerive*` | Tables, repository operations, column/JSON types |

**Conventions:**

- Do **not** put `@httpService` operations and `@sqlTable` / `@sqlService` shapes in the same namespace, and do **not** reuse the same structure or enum shapes across HTTP and SQL (even when field names match). HTTP payloads and persistence models evolve on different schedules; sharing Smithy shapes ties codegen and migrations to the wire format.
- Duplicate enums and value types per namespace when both layers need similar concepts (for example `PetStatus` in `example.api` and `example.db`). It is fine for them to differ (API `PetAttributeList` vs DB `PetTags`, and so on).
- Translate between HTTP and SQL in **hand-written application code** (protocol implementations, repository facades, mappers). Generated HTTP route modules should call into generated repository services through that boundary, not by importing one generated model tree from the other.
- OpenAPI export projections should list **API sources only** (for example `api-types.smithy`, `api.smithy`, `http-service.smithy`) and target the HTTP service shape id (for example `example.api#ExampleService`). Do not include SQL Smithy files in OpenAPI projections.

The [Python petstore reference](../../example/python/) demonstrates this layout: `petstore.api` for HTTP/OpenAPI codegen and `petstore.db` for schema/repository codegen, with mapping in `src/server/repository_service.py`.

## Plugin outputs summary

All plugin file-manifest paths are relative to **`build/smithy/source/smithplates/`** (the Smithy projection directory for the `smithplates` plugin).

| Plugin | Build output directory | Pipeline path | Contents |
|--------|------------------------|---------------|----------|
| `smithplates` | `build/smithy/source/smithplates/` | Schema and migrations | Versioned migration `.sql` files under each dialect `migrationLocation` |
| `smithplates` | `build/smithy/source/smithplates/<sourceOutputDir>` and `.../<testOutputDir>` | SQL database service codegen | Namespace-aware query models, interfaces, dialect implementations, migration services, `conftest.py`, and derived-query integration tests |
| `smithplates` | `build/smithy/source/smithplates/<sourceOutputDir>` and `.../<testOutputDir>` | HTTP service/client codegen | FastAPI routes/protocols/app wiring/WebSockets (Python); HTTP clients (Python/httpx, TypeScript axios/fetch); shared models and `HttpProblem` helpers per `@httpService` |

The `@httpService` trait ships in `smithplates-plugin`; consumers do not need a separate AWS protocol traits dependency for HTTP codegen.

See [Architecture](../contributing/architecture.md) for the full codegen pipeline and [SQL plugin](sql-plugin.md) / [HTTP plugin](http-plugin.md) for feature behavior.

## SPI registration

The plugin registers as `com.jacoby6000.smithplates.plugin.SmithplatesBuildPlugin`.
