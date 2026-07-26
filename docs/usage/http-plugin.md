# HTTP plugin

Smithplates HTTP codegen turns Smithy `@httpService` models into:

- **Python/FastAPI** server wiring (route groups, protocols, app factory, WebSockets);
- **Python/httpx** and **TypeScript** (axios or fetch) HTTP clients from the same model;
- shared API models and RFC 9457 problem-detail helpers.

## Trait cheat sheet

| Trait / shape | Apply to | Purpose |
|---------------|----------|---------|
| `@httpService` | service | Select the service for Smithplates HTTP codegen |
| `@http` | operation | Method, URI, and success status (also required on `@websocket` ops for the path) |
| `@tags` | operation | Route-group / client module grouping |
| `@httpProblem` | error structure | RFC 9457 problem details (+ optional implied `@httpError`) |
| `@httpStaticHeader` | output structure | Fixed response header binding |
| `@websocket` | operation | Bidirectional WebSocket endpoint (skipped by REST generation) |
| `@nestedProperties` | `@httpPayload` member | Flatten the payload target as the wire body |
| Smithy `@httpLabel` / `@httpQuery` / `@httpHeader` / `@httpPayload` | members | Standard HTTP binding traits |

Full trait tables: [`modules/smithplates-plugin/README.md`](../../modules/smithplates-plugin/README.md).

## Modeling

Use Smithy HTTP traits for the wire contract and Smithplates HTTP traits for codegen-specific behavior:

- `@httpService` marks the service for Smithplates HTTP codegen.
- Smithy `@http` declares method, URI, and response code.
- Smithy `@tags` group operations into generated route modules.
- `@httpProblem` generates RFC 9457-style problem detail exceptions and response helpers.
- `@httpStaticHeader` adds fixed response headers for generated response bindings.
- Smithy `@nestedProperties` on a single `@httpPayload` member flattens that member's target structure as the HTTP request body while the operation input shape is reconstructed for dispatch (see [Nested payload bodies](#nested-payload-bodies)).
- `@websocket` promotes an operation into a bidirectional WebSocket endpoint (see [WebSockets](#websockets)).

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

HTTP settings live under `smithplates.<language>.http`. Configure `server`, `client`, or both. Every target must declare at least one `outputs` entry specifying where generated code is written:

```json
{
  "plugins": {
    "smithplates": {
      "python": {
        "http": {
          "rootNamespace": "generated",
          "server": {
            "webFramework": "fastapi"
          },
          "client": {
            "httpLibrary": "httpx"
          },
          "outputs": [
            { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
          ]
        }
      },
      "typescript": {
        "http": {
          "rootNamespace": "generated",
          "client": {
            "httpLibrary": "fetch"
          },
          "outputs": [
            { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
          ]
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

### Output entries

The `outputs` array is required — each entry controls one codegen pass. At minimum, every entry needs `sourceOutputDir` and `testOutputDir`. Omit `services` to generate all services in the model, or list specific service names to scope codegen to a subset:

```json
{
  "http": {
    "server": { "webFramework": "fastapi" },
    "outputs": [
      {
        "services": ["ServerApi"],
        "sourceOutputDir": "src/generated/server",
        "testOutputDir": "server/tests",
        "packageName": "generated.server"
      },
      {
        "services": ["RunnerApi"],
        "sourceOutputDir": "src/generated/runner",
        "testOutputDir": "runner/tests",
        "packageName": "generated.runner"
      }
    ]
  }
}
```

Each entry in `outputs` supports:

| Field | Required | Description |
|-------|----------|-------------|
| `sourceOutputDir` | Yes | Directory for generated source artifacts. |
| `testOutputDir` | Yes | Directory for generated test artifacts. |
| `services` | No | List of `@httpService` shape names to include. Omit to generate all services. |
| `packageName` | No | Overrides `server.packageName` / `client.packageName` for this entry's output. |

When `outputs` is absent, all `@httpService` shapes in the model are generated together (existing behavior). When `outputs` is present, each entry runs a full server + client codegen pass scoped to the listed services.

## Generated server output

Generated paths are relative to `build/smithy/source/smithplates/`. For bundled FastAPI templates, Smithplates emits files such as:

```text
<sourceOutputDir>/<smithy namespace>/app_factory.py
<sourceOutputDir>/<smithy namespace>/app_services.py
<sourceOutputDir>/<smithy namespace>/api_response.py
<sourceOutputDir>/<smithy namespace>/operation_bindings.py
<sourceOutputDir>/<smithy namespace>/apis/<route_group>_api.py
<sourceOutputDir>/<smithy namespace>/apis/<route_group>_api_base.py
<sourceOutputDir>/<smithy namespace>/websocket_routes.py
<sourceOutputDir>/<smithy namespace>/<model_shape>.py
<sourceOutputDir>/smithplates/codegen/http/http_problem.py
```

`websocket_routes.py` is emitted only when the service declares `@websocket` operations. The shared `HttpProblem` base is emitted when `@httpProblem` is used (see [Problem details](#problem-details)).

Generated route modules depend on generated protocol base classes. Application code implements those protocols and passes implementations into the generated app factory or service registry.

## Generated client output

### Python / httpx

For bundled httpx templates, Smithplates emits files such as:

```text
<sourceOutputDir>/<smithy namespace>/client/client_registry.py
<sourceOutputDir>/<smithy namespace>/client/client_response.py
<sourceOutputDir>/<smithy namespace>/client/operation_bindings.py
<sourceOutputDir>/<smithy namespace>/clients/<route_group>_client.py
<sourceOutputDir>/<smithy namespace>/clients/websocket_client.py
<sourceOutputDir>/<smithy namespace>/<model_shape>.py
```

`websocket_client.py` is emitted only when the service declares `@websocket` operations (see [WebSockets](#websockets)).

Generated client modules serialize request inputs from Smithy HTTP bindings, issue HTTP requests through httpx, and deserialize responses into the shared models at the namespace root.

**REST client wiring (Python / httpx):**

1. Create an `httpx.AsyncClient` (or reuse an existing client).
2. Call `create_api_clients(client, base_url=...)` from the generated client registry.
3. Invoke generated route-group client methods such as `clients.warehouse_api.create_shelf_item(...)`.

### TypeScript / fetch or axios

For bundled TypeScript clients (`httpLibrary: "fetch"` or `"axios"`), Smithplates emits camelCase `.ts` modules such as:

```text
<sourceOutputDir>/<smithy namespace>/client/clientRegistry.ts
<sourceOutputDir>/<smithy namespace>/client/clientResponse.ts
<sourceOutputDir>/<smithy namespace>/client/operationBindings.ts
<sourceOutputDir>/<smithy namespace>/clients/<routeGroup>Client.ts
<sourceOutputDir>/<smithy namespace>/clients/websocketClient.ts
<sourceOutputDir>/<smithy namespace>/<modelShape>.ts
<sourceOutputDir>/smithplates/codegen/http/httpProblem.ts
```

`websocketClient.ts` and `httpProblem.ts` appear when the model uses `@websocket` / `@httpProblem` respectively (see [WebSockets](#websockets)).

**REST client wiring (TypeScript):** construct the generated client registry with your `baseUrl` (and axios instance when using axios), then call the typed route-group client methods.

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

## WebSockets

Smithplates supports bidirectional WebSocket endpoints on `@httpService` operations via `@websocket`.

| Side | Language | What you get |
|------|----------|--------------|
| Server | Python / FastAPI | `websocket_routes.py` — handler protocol + router factory |
| Client | Python | `clients/websocket_client.py` (depends on the `websockets` package) |
| Client | TypeScript | `clients/websocketClient.ts` (native browser/`WebSocket` API) |

REST route and REST client generation **skip** `@websocket` operations; they are handled only by these WebSocket artifacts.

### 1. Model the endpoint

Annotate the operation with `@websocket`, an `@http` binding (the `uri` is the WebSocket path), and at least one `@tags` value. The operation **input** is the message shape(s) the server receives from the client; the **output** is what the client receives from the server. Prefer unions (or structures of optional members) when multiple message types flow in each direction.

Path labels on the input (`@httpLabel`) become path parameters on the WebSocket URI (for example `/streams/{streamId}/events`).

```smithy
use smithplates.codegen.http#websocket

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

Configure `smithplates.<language>.http.server` and/or `http.client` as usual ([Configuration](configuration.md)); no separate WebSocket flag is required. Enabling HTTP codegen for a model that declares `@websocket` is enough.

### 2. Implement the server (Python / FastAPI)

Smithplates emits `<sourceOutputDir>/<smithy namespace>/websocket_routes.py` with:

- a `WebsocketHandlers` protocol — one `async` method per `@websocket` operation;
- `build_websocket_router(handlers)` — returns a FastAPI `APIRouter` to mount on your app;
- `send_<operation>_message(websocket, message)` helpers — serialize typed outbound messages.

Wire it like this:

1. Implement `WebsocketHandlers` in hand-written application code.
2. Call `app.include_router(build_websocket_router(handlers))` when constructing the FastAPI app.
3. In each handler, read the typed inbound `message`, perform your logic, and call `send_<operation>_message` (or send on the live `WebSocket`) for outbound frames.

Handlers receive the FastAPI `WebSocket`, any path-label arguments, and (when the operation has input messages) a validated inbound message. The generated router accepts the connection, loops over inbound frames, and dispatches each one to your handler.

Reference implementation: [`example/python/src/server/`](../../example/python/src/server/) (`PetEventsHandlers` + `build_websocket_router` in `app.py`) against [`PetEvents`](../../example/petstore-smithy-spec/petstore/api/api.smithy) in the petstore Smithy model.

### 3. Use a generated client

**Python** — construct `<Service>WebsocketClient(base_url=...)`, then `async with await client.connect_<operation>(...) as conn:` (path labels become method arguments). Use `await conn.send(...)`, `await conn.receive()`, `async for message in conn:`, and `await conn.close()`.

**TypeScript** — construct `<Service>WebsocketClient({ baseUrl })`, then `client.connect<Operation>(...)`. The connection exposes `send`, `onMessage`, `onClose`, and `close`.

Message payloads use the same generated models as the rest of the HTTP API (Pydantic in Python, typed models in TypeScript).

### Limits and OpenAPI

- Bundled WebSocket **server** generation is Python/FastAPI only.
- `@websocket` is **not** removed by `stripSmithplatesHttpCodegenTraits`. Keep WebSocket operations out of OpenAPI projections, or filter them yourself — see [Integration](integration.md#openapi-projection-transforms) and [OpenAPI](openapi.md).
- Golden coverage: `templates/python/tests/http-*-websocket-*` and `templates/typescript/tests/http-*-websocket-*`.

## Problem details

Use `@httpProblem` on Smithy error structures when generated HTTP code should expose
[RFC 9457](https://datatracker.ietf.org/doc/html/rfc9457) problem detail responses.
This is the recommended way to model HTTP errors: you declare a Smithy error structure
with trait defaults, and smithplates generates exception classes, Pydantic error
models, and FastAPI handlers that serialize `application/problem+json`.

Smithplates emits a shared base model **`HttpProblem`** once per codegen run under
`{rootNamespace}/smithplates/codegen/http/` (Smithy namespace `smithplates.codegen.http`,
aligned with the `@httpProblem` trait):

- Python: `http_problem.py`
- TypeScript: `httpProblem.ts`

Error structures annotated with `@httpProblem` extend `HttpProblem` in generated code.
You do **not** need to define your own RFC 9457 base type.

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

- [Python petstore](../../example/python/) — SQL + FastAPI server + httpx client + adapters, including the `@websocket` `PetEvents` endpoint.
- [TypeScript petstore client](../../example/typescript/) — fetch client against the shared petstore Smithy model (includes the generated WebSocket client module when present).
