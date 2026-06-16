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

## HTTP transforms

Smithplates ships transforms for compatibility with standard Smithy tools:

- `applyHttpProblemHttpError`
- `applyHttpServiceRestJson1`
- `stripSmithplatesHttpCodegenTraits`

The build plugin applies the HTTP problem transform before extraction. Consumer OpenAPI projections can use the same transform names in `smithy-build.json`.

## HTTP renderer

`smithplates-http-service-renderer` owns:

- HTTP codegen settings;
- FastAPI artifact selection;
- generated API model, route, protocol, app, response, and exception views;
- bundled HTTP template precompilation.

Bundled templates currently target Python/FastAPI.

## Plugin orchestration

`smithplates-plugin` wires `smithplates.http` settings to the HTTP pipeline:

- validates HTTP language targets;
- extracts HTTP IR;
- logs extraction warnings;
- renders configured language targets;
- writes generated artifacts through Smithy's file manifest.

## Test coverage

HTTP golden cases live under `templates/python/tests/http-fastapi-*` and are exercised by `CodegenTemplateTestSuite`. Example-level HTTP behavior is covered by the Python petstore example and shared example tests.
