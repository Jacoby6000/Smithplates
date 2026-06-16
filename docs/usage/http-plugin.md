# HTTP plugin

Smithplates HTTP service codegen turns Smithy `@httpService` models into generated FastAPI server wiring for Python.

## Modeling

Use Smithy HTTP traits for the wire contract and Smithplates HTTP traits for codegen-specific behavior:

- `@httpService` marks the service for Smithplates HTTP codegen.
- Smithy `@http` declares method, URI, and response code.
- Smithy `@tags` group operations into generated route modules.
- `@httpProblem` generates RFC 9457-style problem detail exceptions and response helpers.
- `@httpStaticHeader` adds fixed response headers for generated response bindings.

Keep HTTP shapes in a namespace dedicated to the API contract. Do not reuse SQL table shapes as HTTP request or response shapes; map between generated API and database models in hand-written application code.

## Configuration

HTTP settings live under `smithplates.http.<language>.server`:

```json
{
  "plugins": {
    "smithplates": {
      "http": {
        "python": {
          "server": {
            "webFramework": "fastapi",
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "packageName": "generated.api"
          }
        }
      }
    }
  }
}
```

Python/FastAPI is the bundled HTTP target today. Non-bundled languages or frameworks require an explicit `templateDirectory`.

## Generated output

Generated paths are relative to `build/smithy/source/smithplates/`. For bundled FastAPI templates, Smithplates emits files such as:

```text
<sourceOutputDir>/api/app_factory.py
<sourceOutputDir>/api/app_services.py
<sourceOutputDir>/api/api_response.py
<sourceOutputDir>/api/operation_bindings.py
<sourceOutputDir>/api/apis/<route_group>_api.py
<sourceOutputDir>/api/apis/<route_group>_api_base.py
<sourceOutputDir>/api/models/*.py
```

Generated route modules depend on generated protocol base classes. Application code implements those protocols and passes implementations into the generated app factory or service registry.

## Problem details

Use `@httpProblem` on Smithy error structures when generated HTTP code should expose problem detail responses.

`@httpProblem` can:

- materialize a matching `@httpError` status code during Smithy model transformation;
- generate exception classes for application code to raise;
- emit `application/problem+json` response handling;
- carry a stable problem `type`, `title`, and optional default `detail`.

Smithplates also ships projection transforms for tools that expect only standard Smithy traits:

- `applyHttpProblemHttpError`
- `applyHttpServiceRestJson1`
- `stripSmithplatesHttpCodegenTraits`

See [OpenAPI](openapi.md) for projection usage.

## Reference example

The [Python petstore example](../../example/python/) combines Smithplates HTTP service codegen, SQL repository codegen, OpenAPI export, generated client code, and hand-written adapters.
