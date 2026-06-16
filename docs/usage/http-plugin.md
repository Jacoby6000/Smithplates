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

### Route groups

Smithy `@tags` drive generated route modules. For example, operations tagged with `system` render into a system route module:

```smithy
@tags(["system"])
@http(method: "GET", uri: "/health", code: 200)
operation HealthCheck {
    output: HealthCheckOutput
}
```

Group operations around API ownership, not persistence tables. A route group usually maps to a service implementation class in hand-written application code.

## Configuration

HTTP settings live under `smithplates.<language>.http`. Configure `server`, `client`, or both:

```json
{
  "plugins": {
    "smithplates": {
      "python": {
        "http": {
          "rootNamespace": "generated",
          "server": {
            "webFramework": "fastapi",
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests"
          },
          "client": {
            "httpLibrary": "httpx",
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests"
          }
        }
      }
    }
  }
}
```

Python/FastAPI is the bundled HTTP server target today. Python/httpx is the bundled HTTP client target. Non-bundled languages or frameworks require an explicit `templateDirectory`.

Optional `rootNamespace` (default `generated` for bundled Python) prefixes the Python import package derived from each `templateDirectory`. Filesystem layout is `<sourceOutputDir>/` + template-relative path (for example `http/server`). When both `server` and `client` are enabled, the server pass emits models once; the client pass reuses them.

## Generated server output

Generated paths are relative to `build/smithy/source/smithplates/`. For bundled FastAPI templates, Smithplates emits files such as:

```text
<sourceOutputDir>/http/server/app_factory.py
<sourceOutputDir>/http/server/app_services.py
<sourceOutputDir>/http/server/api_response.py
<sourceOutputDir>/http/server/operation_bindings.py
<sourceOutputDir>/http/server/apis/<route_group>_api.py
<sourceOutputDir>/http/server/apis/<route_group>_api_base.py
<sourceOutputDir>/http/models/*.py
```

Generated route modules depend on generated protocol base classes. Application code implements those protocols and passes implementations into the generated app factory or service registry.

## Generated client output

For bundled httpx templates, Smithplates emits files such as:

```text
<sourceOutputDir>/http/client/client_registry.py
<sourceOutputDir>/http/client/client_response.py
<sourceOutputDir>/http/client/operation_bindings.py
<sourceOutputDir>/http/client/clients/<route_group>_client.py
<sourceOutputDir>/http/models/*.py
```

Generated client modules serialize request inputs from Smithy HTTP bindings, issue HTTP requests through httpx, and deserialize responses into the shared models under `http/models/`.

## Application wiring

The generated HTTP layer owns FastAPI routing and wire conversion. Your application owns business behavior:

1. Implement the generated protocol class for each route group.
2. Construct generated service adapters with your dependencies, such as repositories or configuration.
3. Pass those implementations to the generated app factory or service registry.
4. Keep mapping between HTTP models and database models in hand-written code.

This keeps generated files replaceable and avoids editing generated route modules.

### Client wiring

1. Create an `httpx.AsyncClient` (or reuse an existing client).
2. Call `create_api_clients(client, base_url=...)` from the generated client registry.
3. Invoke generated route-group client methods such as `clients.warehouse_api.create_shelf_item(...)`.

## Problem details

Use `@httpProblem` on Smithy error structures when generated HTTP code should expose problem detail responses.

`@httpProblem` can:

- materialize a matching `@httpError` status code during Smithy model transformation;
- generate exception classes for application code to raise;
- emit `application/problem+json` response handling;
- carry a stable problem `type`, `title`, and optional default `detail`.

Example:

```smithy
use smithplates.codegen.http#httpProblem
use smithy.api#error

@error("client")
@httpProblem(
    code: 404,
    type: "https://example.com/problems/widget-not-found",
    title: "Widget not found"
)
structure WidgetNotFound {
    message: String
}
```

Generated application code raises the generated exception type; generated FastAPI handlers serialize the problem response.

Smithplates also ships projection transforms for tools that expect only standard Smithy traits:

- `applyHttpProblemHttpError`
- `applyHttpServiceRestJson1`
- `stripSmithplatesHttpCodegenTraits`

See [OpenAPI](openapi.md) for projection usage.

## Reference example

The [Python petstore example](../../example/python/) combines Smithplates HTTP service codegen, SQL repository codegen, OpenAPI export, generated client code, and hand-written adapters.
