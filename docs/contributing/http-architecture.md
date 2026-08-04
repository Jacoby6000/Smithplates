# HTTP architecture

The HTTP pipeline turns Smithy `@httpService` models into generated server and client artifacts via the language-neutral planner (`outputs.json` + `CodegenPlanner`) and Scalate SSP templates.

## Pipeline

```text
Smithy model
  -> HTTP problem transform
  -> HTTP service IR + HttpCoreModelExtractor (neutral ModelSet)
  -> CodegenPlanner (bundled + optional consumer decks)
  -> Scalate SSP templates (TemplateView + HttpNeutral* attributes)
  -> generated FastAPI / httpx / TypeScript / WebSocket artifacts
```

## HTTP IR

`smithplates-http-ir` owns:

- Smithplates HTTP trait IDL (`@httpService`, `@httpProblem`, `@httpStaticHeader`, `@websocket`);
- HTTP service extraction and body-binding resolution (including `@nestedProperties` → `NestedDocument`);
- route grouping from Smithy `@tags`;
- model transforms for OpenAPI compatibility;
- `HttpCoreModelExtractor` / `HttpCoreMetaBuilder` for neutral core metadata.

Start here when changing how Smithy HTTP models are interpreted.

Typical changes:

- Add or change an HTTP codegen trait.
- Change route-group extraction from Smithy `@tags`.
- Change request/response binding interpretation (payload, nested properties, headers).
- Change warning or validation behavior around problem details or WebSockets.

## HTTP transforms

Smithplates ships transforms for compatibility with standard Smithy tools:

- `applyHttpProblemHttpError`
- `applyHttpServiceRestJson1`
- `stripSmithplatesHttpCodegenTraits`

The build plugin applies the HTTP problem transform before extraction. Consumer OpenAPI projections can use the same transform names in `smithy-build.json`.

Transform changes should be tested both as direct model transforms and through OpenAPI-oriented fixtures when they affect exported Smithy models.

## HTTP renderer

`smithplates-http-service-renderer` owns:

- HTTP codegen settings and deck composition;
- FastAPI server artifact selection;
- Python httpx and TypeScript axios/fetch client artifact selection;
- neutral template attribute builders (`HttpNeutralModelTemplateAttributes`, `HttpNeutralServiceTemplateAttributes`, `HttpNeutralRouteGroupTemplateAttributes`);
- bundled HTTP server and client template precompilation.

Bundled templates currently target:

- Python/FastAPI servers (REST + `@websocket`);
- Python/HTTPX and HTTPX2 clients;
- TypeScript clients (`fetch` or `axios`).

Typical changes:

- Add generated route, protocol, response, model, client, or exception attributes.
- Change FastAPI app wiring or WebSocket router emission.
- Change problem+json exception output.
- Add golden coverage for another framework or language variant.

## Plugin orchestration

`smithplates-plugin` wires `smithplates.<language>.http` settings to the HTTP pipeline:

- validates HTTP language targets;
- extracts HTTP IR;
- logs extraction warnings;
- plans and renders configured language targets from `outputs.json` decks;
- writes generated artifacts through Smithy's file manifest.

## Test coverage

HTTP golden cases live under:

- `templates/python/tests/http-fastapi-*`, `http-httpx-*`, websocket and nested-properties cases;
- `templates/typescript/tests/http-axios-*`, `http-fetch-*` (including websocket).

They are exercised by `CodegenTemplateTestSuite`. Example-level HTTP behavior is covered by the Python petstore, TypeScript petstore client, and shared example tests under `example/tests/`.

## Change map

| Goal | Primary modules | Tests to start with |
|------|-----------------|---------------------|
| Add or change an HTTP trait | `smithplates-http-ir` | HTTP IR tests, HTTP golden case |
| Change OpenAPI compatibility transforms | `smithplates-http-ir` transforms | transform tests, OpenAPI projection example |
| Change generated FastAPI output | `smithplates-http-service-renderer`, `templates/python/src/http/server` | HTTP golden case, example HTTP tests |
| Change generated Python HTTP client output | `smithplates-http-service-renderer`, `templates/python/src/http/client` | HTTP client golden case |
| Change generated TypeScript client output | `smithplates-http-service-renderer`, `templates/typescript/src/http` | TypeScript golden cases, example/typescript |
| Change HTTP plugin settings | `smithplates-plugin` HTTP settings | plugin settings specs, HTTP golden case |
| Add another HTTP framework | HTTP renderer deck composition, templates, validators | new golden variant and example coverage |

HTTP codegen should keep generated route modules thin. Business behavior belongs behind generated protocol boundaries in hand-written application code.
