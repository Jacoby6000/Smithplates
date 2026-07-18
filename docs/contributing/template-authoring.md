# Template authoring

Smithplates target-language output is rendered with Scalate SSP templates. Template authors usually work in `templates/`, golden fixtures, and language harnesses.

## Layout

```text
templates/
  python/
    base_config.json
    src/
      db/
      http/
    tests/
  typescript/
    base_config.json
    src/
      http/
    tests/
language-test-harnesses/
  python/
  typescript/   # when present
```

Bundled templates are packaged as compile resources and precompiled into renderer jars. Each template root needs an `outputs.json` deck beside the templates.

## SQL templates

Bundled SQL service templates live under:

```text
templates/python/src/db/
```

Shared DB artifacts include model and protocol templates. Dialect-specific trees add SQLite and Postgres implementations, migration services, transaction helpers, generated tests, and stubs where needed.

When no SQL dialect is enabled, shared-only rendering is dialect-free and requires only shared model/protocol templates.

| Artifact area | Template responsibility |
|---------------|-------------------------|
| `model/models.ssp` | Dataclasses and union aliases for table, input, output, and error shapes. |
| `service_protocol.ssp` | Generic repository `Protocol[T]` interface with optional `transaction` parameter. |
| `sqlite/service_aiosqlite.ssp` | `aiosqlite` implementation using mapper-style row mapping. |
| `postgres/service_psycopg.ssp` | `psycopg` implementation using per-cursor row factories. |
| `<dialect>/migrations_service.ssp` | Runtime migration service for ordered SQL files and `_smithplates_migrations`. |
| `<dialect>/tests/...ssp` | Generated pytest lifecycle and transaction coverage for derived SQL methods. |

## HTTP templates

### Python

Bundled HTTP templates live under:

```text
templates/python/src/http/
```

These render FastAPI route modules (including WebSocket routers), protocol base classes, app wiring, response helpers, problem-detail helpers, httpx clients, and model artifacts.

| Artifact area | Template responsibility |
|---------------|-------------------------|
| `app_factory` / `app_services` | App construction and service implementation registry. |
| `operation_bindings` | Request parsing, response dispatch, and operation binding helpers. |
| `api_response` | Generated response wrapper types. |
| `api_exceptions` / handler templates | Problem+json exception classes and FastAPI handlers. |
| `apis/<route_group>` | FastAPI route modules and route-group protocol base classes. |
| `websocket_routes` | `@websocket` handler protocol and router factory. |
| `client/*` / `clients/*` | httpx route-group clients, registry, and WebSocket client. |
| `models` | Python API payload model artifacts. |

### TypeScript

Bundled TypeScript HTTP **client** templates live under:

```text
templates/typescript/src/http/
```

Select `httpLibrary: "fetch"` or `"axios"`. There is no bundled TypeScript HTTP server or SQL set today.

## Fragments

Bundled Python templates organize reusable snippets under `fragments/`. Include or render fragments with Scalate calls such as:

```ssp
<% include("fragments/...") %>
<% render("fragments/...", Map(...)) %>
```

Keep shared formatting, naming, imports, and type rendering in fragments when multiple templates need the same behavior.

Avoid duplicating dialect-specific behavior across templates. Put reusable naming, import, bind, row-reader, and JSON helpers in fragments so golden diffs stay focused when behavior changes.

## Validation workflow

1. Edit SSP templates or fragments.
2. Run golden render tests.
3. Refresh expected files if output changed intentionally.
4. Run language harness linters and tests.

```bash
./scripts/run-template-golden-tests.sh
sbtn 'generateGoldenTemplatesFor python <case-name>'
./language-test-harnesses/python/run-linters.sh
./language-test-harnesses/python/run-tests.sh
```

## Adding or updating a golden case

Golden cases live under `templates/python/tests/<case-name>/`.

Each case contains:

```text
smithy/smithy-files.smithy
smithy-build.json
expected/
```

Use a case when you need to prove a generated-output contract, not just a Scala helper. Prefer a small Smithy model that isolates the behavior under test.

After changing the expected output intentionally:

```bash
sbtn 'generateGoldenTemplatesFor python <case-name>'
./scripts/run-template-golden-tests.sh
```

Then run the language harness if the generated files should type-check or execute.

## Adding a new bundled language or framework

1. Add templates under `templates/<language>/src/<service-type>/`.
2. Add or update artifact configuration in the relevant renderer.
3. Add template-directory validation for bundled support.
4. Add golden cases under `templates/<language>/tests/`.
5. Add or extend a language harness if generated code can be linted or executed.
6. Update user docs for the new public configuration and limitations.

## Template precompilation

Bundled templates are ahead-of-time compiled into JVM classes during the renderer build. Runtime template engines use the same package prefix so consumer `smithy build` runs load precompiled template classes instead of invoking the Scala compiler.

See [Architecture](architecture.md#template-precompilation) for the full build-time and runtime design.
