# HTTP architecture

The HTTP pipeline turns Smithy `@httpService` models into generated Python/FastAPI server wiring.

## Pipeline

```text
Smithy model
  -> HTTP problem transform
  -> HTTP service IR
  -> HTTP service codegen context
  -> Scalate SSP templates
  -> generated FastAPI artifacts
```

## HTTP IR

`smithplates-http-ir` owns:

- Smithplates HTTP trait IDL;
- `@httpService`, `@httpProblem`, and `@httpStaticHeader` trait handling;
- HTTP service extraction;
- route grouping from Smithy `@tags`;
- model transforms for OpenAPI compatibility.

Start here when changing how Smithy HTTP models are interpreted.

Typical changes:

- Add or change an HTTP codegen trait.
- Change route-group extraction from Smithy `@tags`.
- Change request/response binding interpretation.
- Change warning or validation behavior around problem details.

## HTTP transforms

Smithplates ships transforms for compatibility with standard Smithy tools:

- `applyHttpProblemHttpError`
- `applyHttpServiceRestJson1`
- `stripSmithplatesHttpCodegenTraits`

The build plugin applies the HTTP problem transform before extraction. Consumer OpenAPI projections can use the same transform names in `smithy-build.json`.

Transform changes should be tested both as direct model transforms and through OpenAPI-oriented fixtures when they affect exported Smithy models.

## HTTP renderer

`smithplates-http-service-renderer` owns:

- HTTP codegen settings;
- FastAPI server artifact selection;
- httpx client artifact selection;
- generated API model, route, protocol, client, app, response, and exception views;
- bundled HTTP server and client template precompilation.

Bundled templates currently target Python/FastAPI servers and Python/httpx clients.

Typical changes:

- Add generated route, protocol, response, model, or exception attributes.
- Change FastAPI app wiring.
- Change problem+json exception output.
- Add generated tests or support for another framework.

## Plugin orchestration

`smithplates-plugin` wires `smithplates.<language>.http` settings to the HTTP pipeline:

- validates HTTP language targets;
- extracts HTTP IR;
- logs extraction warnings;
- renders configured language targets;
- writes generated artifacts through Smithy's file manifest.

## Test coverage

HTTP golden cases live under `templates/python/tests/http-fastapi-*` and `templates/python/tests/http-httpx-*` and are exercised by `CodegenTemplateTestSuite`. Example-level HTTP behavior is covered by the Python petstore example and shared example tests.

## Change map

| Goal | Primary modules | Tests to start with |
|------|-----------------|---------------------|
| Add or change an HTTP trait | `smithplates-http-ir` | HTTP IR tests, HTTP golden case |
| Change OpenAPI compatibility transforms | `smithplates-http-ir` transforms | transform tests, OpenAPI projection example |
| Change generated FastAPI output | `smithplates-http-service-renderer`, `templates/python/src/http/server` | HTTP golden case, example HTTP tests |
| Change generated httpx client output | `smithplates-http-service-renderer`, `templates/python/src/http/client` | HTTP client golden case |
| Change HTTP plugin settings | `smithplates-plugin` HTTP settings | plugin settings specs, HTTP golden case |
| Add another HTTP framework | HTTP renderer artifact config, templates, validators | new golden variant and example coverage |

HTTP codegen should keep generated route modules thin. Business behavior belongs behind generated protocol boundaries in hand-written application code.
