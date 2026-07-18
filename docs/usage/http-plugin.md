# HTTP plugin

Smithplates HTTP codegen turns Smithy `@httpService` models into:

- **Python/FastAPI** server wiring (route groups, protocols, app factory, WebSockets);
- **Python/httpx** and **TypeScript** (axios or fetch) HTTP clients from the same model;
- shared API models and RFC 9457 problem-detail helpers.

## Modeling

Use Smithy HTTP traits for the wire contract and Smithplates HTTP traits for codegen-specific behavior:

- `@httpService` marks the service for Smithplates HTTP codegen.
- Smithy `@http` declares method, URI, and response code.
- Smithy `@tags` group operations into generated route modules.
- `@httpProblem` generates RFC 9457-style problem detail exceptions and response helpers.
- `@httpStaticHeader` adds fixed response headers for generated response bindings.
- Smithy `@nestedProperties` on a single `@httpPayload` member flattens that member's target structure as the HTTP request body while the operation input shape is reconstructed for dispatch (see [Nested payload bodies](#nested-payload-bodies)).
- `@websocket` promotes an operation into a bidirectional WebSocket endpoint (see [Websockets](#websockets)).

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
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests",
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

Bundled targets today:

| Language | Server | Client libraries |
|----------|--------|------------------|
| `python` | FastAPI (`webFramework: "fastapi"`) | `httpx` |
| `typescript` | — (client-only) | `fetch` or `axios` |

Non-bundled languages or frameworks require an explicit `templateDirectory` with an `outputs.json` deck.

Optional `rootNamespace` (default `generated` for bundled Python) prefixes Python import packages. Filesystem layout is `<sourceOutputDir>/<smithy namespace path>/` (for example `example` for a service in namespace `example`). When both `server` and `client` are enabled, the server pass emits models once; the client pass reuses them.

## Generated server output

Generated paths are relative to `build/smithy/source/smithplates/`. For bundled FastAPI templates, Smithplates emits files such as:

```text
<sourceOutputDir>/<smithy namespace>/app_factory.py
<sourceOutputDir>/<smithy namespace>/app_services.py
<sourceOutputDir>/<smithy namespace>/api_response.py
<sourceOutputDir>/<smithy namespace>/operation_bindings.py
<sourceOutputDir>/<smithy namespace>/apis/<route_group>_api.py
<sourceOutputDir>/<smithy namespace>/apis/<route_group>_api_base.py
<sourceOutputDir>/<smithy namespace>/<model_shape>.py
```

Generated route modules depend on generated protocol base classes. Application code implements those protocols and passes implementations into the generated app factory or service registry.

## Generated client output

### Python / httpx

For bundled httpx templates, Smithplates emits files such as:

```text
<sourceOutputDir>/<smithy namespace>/client/client_registry.py
<sourceOutputDir>/<smithy namespace>/client/client_response.py
<sourceOutputDir>/<smithy namespace>/client/operation_bindings.py
<sourceOutputDir>/<smithy namespace>/clients/<route_group>_client.py
<sourceOutputDir>/<smithy namespace>/<model_shape>.py
```

Generated client modules serialize request inputs from Smithy HTTP bindings, issue HTTP requests through httpx, and deserialize responses into the shared models at the namespace root.

### TypeScript / fetch or axios

For bundled TypeScript clients (`httpLibrary: "fetch"` or `"axios"`), Smithplates emits camelCase `.ts` modules such as:

```text
<sourceOutputDir>/<smithy namespace>/client/clientRegistry.ts
<sourceOutputDir>/<smithy namespace>/client/clientResponse.ts
<sourceOutputDir>/<smithy namespace>/client/operationBindings.ts
<sourceOutputDir>/<smithy namespace>/clients/<routeGroup>Client.ts
<sourceOutputDir>/<smithy namespace>/<modelShape>.ts
```

See [`example/typescript/`](../../example/typescript/) for a petstore fetch-client reference.

## Nested payload bodies

When an operation input has a single `@httpPayload` member annotated with Smithy `@nestedProperties`, Smithplates treats the **payload target** as the flattened HTTP request body (OpenAPI-style nested properties) and reconstructs the outer input shape when invoking the service protocol. Path/query/header members may still appear alongside that payload on the input shape.

```smithy
use smithy.api#httpPayload
use smithy.api#nestedProperties

structure CreateWidgetInput {
    @httpPayload
    @nestedProperties
    body: WidgetCreateRequest
}

structure WidgetCreateRequest {
    @required
    name: String
}
```

Without `@nestedProperties`, a lone `@httpPayload` member remains a normal document body whose wire type is the payload member itself (wrapped in the input shape as usual).

## Application wiring

The generated HTTP layer owns FastAPI routing and wire conversion. Your application owns business behavior:

1. Implement the generated protocol class for each route group.
2. Construct generated service adapters with your dependencies, such as repositories or configuration.
3. Pass those implementations to the generated app factory or service registry.
4. Keep mapping between HTTP models and database models in hand-written code.

This keeps generated files replaceable and avoids editing generated route modules.

## Websockets

`@websocket` marks an operation as a bidirectional WebSocket endpoint. The operation's input shape is the union (or structure) of messages the server can receive from the client; the operation's output shape is the union (or structure) of messages the client can receive from the server. A typical operation uses union-typed input and output so many distinct message types flow in each direction.

The operation must also declare an `@http` binding (its `uri` is the WebSocket route path) and at least one `@tags` value for grouping. WebSocket operations are excluded from REST route/client generation and are handled by dedicated templates instead.

```smithy
@tags(["chat"])
@http(method: "GET", uri: "/chat", code: 200)
@websocket
operation ChatStream {
    input: ClientMessage
    output: ServerMessage
}

union ClientMessage {
    join: JoinRoom
    leave: LeaveRoom
    ping: Ping
}

union ServerMessage {
    welcome: Welcome
    roomJoined: RoomJoined
    pong: Pong
    error: ServerError
}
```

### Generated server output (Python/FastAPI)

Smithplates emits `<sourceOutputDir>/<smithy namespace>/websocket_routes.py` containing a `WebsocketHandlers` protocol (one async handler per `@websocket` operation) and a `build_websocket_router(handlers)` factory. Implement the protocol, build the router, and include it on your FastAPI app. Each handler receives a typed inbound message and the live `WebSocket`; call the generated `send_<operation>_message` helper to serialize outbound messages.

### Generated client output

- Python: `<sourceOutputDir>/<smithy namespace>/clients/websocket_client.py` — a `<Service>WebsocketClient` with a `connect_<operation>` method per endpoint returning a connection exposing `send`, `receive`, `close`, and async iteration. Requires the `websockets` package only when the service declares at least one `@websocket` operation.
- TypeScript: `<sourceOutputDir>/<smithy namespace>/clients/websocketClient.ts` — a `<Service>WebsocketClient` using the native `WebSocket` API, with a `connect<Operation>` method returning a connection exposing `send`, `onMessage`, `onClose`, and `close`.

### Client wiring

**Python / httpx:**

1. Create an `httpx.AsyncClient` (or reuse an existing client).
2. Call `create_api_clients(client, base_url=...)` from the generated client registry.
3. Invoke generated route-group client methods such as `clients.warehouse_api.create_shelf_item(...)`.

**TypeScript / fetch or axios:** construct the generated client registry with your `baseUrl` (and axios instance when using axios), then call the typed route-group client methods.

## Problem details

Use `@httpProblem` on Smithy error structures when generated HTTP code should expose
[RFC 9457](https://datatracker.ietf.org/doc/html/rfc9457) problem detail responses.
This is the recommended way to model HTTP errors: you declare a Smithy error structure
with trait defaults, and smithplates generates exception classes, Pydantic error
models, and FastAPI handlers that serialize `application/problem+json`.

Smithplates emits a shared base model **`HttpProblem`** once per codegen run at
`{rootNamespace}/smithplates/codegen/http/http_problem.py` (namespace
`smithplates.codegen.http`, aligned with the `@httpProblem` trait). Error structures
annotated with `@httpProblem` extend `HttpProblem` in generated Python. You do **not**
need to define your own RFC 9457 base type.

You may still define an ordinary structure named `Problem` (or any other name) in your
service namespace when it is unrelated to `@httpProblem`; it is generated like any
other model and does not collide with the bundled `HttpProblem` base.

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

Generated `@httpProblem` error models extend `HttpProblem` and inherit RFC 9457
fields (`type`, `title`, `status`, `detail`, `instance`). Generated application code
raises the generated exception type; generated FastAPI handlers serialize the problem
response.

Smithplates also ships projection transforms for tools that expect only standard Smithy traits:

- `applyHttpProblemHttpError`
- `applyHttpServiceRestJson1`
- `stripSmithplatesHttpCodegenTraits`

See [OpenAPI](openapi.md) for projection usage.

## Reference example

- [Python petstore](../../example/python/) — SQL + FastAPI server + httpx client + adapters.
- [TypeScript petstore client](../../example/typescript/) — fetch client against the shared petstore Smithy model.
