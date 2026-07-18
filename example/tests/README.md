# Shared reference HTTP tests

Language-neutral HTTP scenarios for Smithystache reference implementations. Each
case file describes one or more request/response steps that any language target
can execute against a running server.

## Running tests

From this directory:

```bash
./run-tests.sh <client-language-target> <server-language-target> [case-name ...]
```

Examples:

```bash
./run-tests.sh python python
./run-tests.sh openapi-reference-python python
./run-tests.sh python python health-check
```

The runner starts the server target once, then runs each selected case through
the client target. It prints a color-coded pass/fail summary when stdout is a
TTY.

## Layout

```
tests/
  run-tests.sh           # entry point
  cases/*.case.json      # shared scenarios
  schema/                # JSON Schema for case files
  lib/                   # shell helpers (colors, target resolution)
  targets/<language>/    # per-language server + client adapters
```

## Test case format (`smithystache.example.test-case/v1`)

Case files use the suffix `.case.json`. See
[`schema/test-case.v1.schema.json`](schema/test-case.v1.schema.json) for the
full schema.

### Top-level fields

| Field | Required | Description |
| --- | --- | --- |
| `schema` | yes | Must be `smithystache.example.test-case/v1` |
| `name` | yes | Stable case identifier (matches filename stem) |
| `description` | no | Human-readable summary |
| `variables` | no | Case-level variables; values may reference server context |
| `steps` | yes | Ordered HTTP interactions |

### Step fields

Each step has:

- `request` — required `operationId`, `method`, `path`; optional `pathParameters`,
  `headers`, `json`, raw `body`, or `transport` (`client` default, `raw` for direct HTTP)
- `expect` — required `status`; optional `headers`, `json`, or `body`
- `capture` — optional map of variable name → JSON Pointer (`$.field.nested`)

`operationId` is the Smithy/OpenAPI operation name (e.g. `GetPet`). Typed-client
runners (like the Python target) dispatch on it and read `pathParameters` (keyed
by the OpenAPI parameter name, e.g. `petId`) and `json`. `method` and `path` stay
in every step for readability and so raw-HTTP runners can execute cases without a
per-operation mapping.

Steps run in order. Variables from `capture` and earlier steps are available
in later steps via `${variable_name}` substitution in strings anywhere under
`request` or `expect`.

### Server context

`start-server.sh` writes a JSON context file consumed by `run-case.sh`:

```json
{
  "base_url": "http://127.0.0.1:54321",
  "variables": {
    "seed_category_id": "…",
    "order_pending_id": "…",
    "order_shipped_id": "…",
    "order_delivered_id": "…"
  }
}
```

Case files may bind server-provided values with `"${server.seed_category_id}"`
in `variables`.

### Flexible JSON expectations

Object values in `expect.json` support matchers:

| Matcher | Meaning |
| --- | --- |
| `{"$exists": true}` | Key must be present (any value) |
| `{"$type": "string"}` | Value must be a string |
| `{"$type": "integer"}` | Value must be an integer |
| `{"$type": "boolean"}` | Value must be a boolean |
| `{"$type": "string", "$minLength": 1}` | Non-empty string |

Unspecified response fields are ignored (partial object matching).

## Adding a language target

Create `targets/<language>/` with executable scripts:

### `start-server.sh <context-file> <pid-file>`

1. Boot the reference server on an ephemeral port with isolated state.
2. Write server context JSON (at minimum `base_url` and any `variables` cases
   need).
3. Write the server PID to `<pid-file>`.
4. Print `base_url` on stdout (last line is sufficient).

### `run-case.sh <case-file> <server-context-file>`

Parse the case file, perform HTTP requests against `base_url`, validate
responses, and exit non-zero on failure.

Optional `target.json` documents the target for tooling.

The Python target (`targets/python/`) boots the FastAPI server via `uvicorn` and
executes cases through the generated Smithplates httpx client (`generated.petstore.api.client` with
`parse_client_response` and `OPERATION_HTTP_BINDINGS`).

The OpenAPI reference target (`targets/openapi-reference-python/`) is client-only:
it runs the same cases through OpenAPI Generator's `petstore_client.DefaultApi`
against any compatible server target (typically `python`). Use it to compare
behavior with the Smithplates client when investigating HTTP binding bugs.

Other languages should implement the same script contract with their generated
client or raw HTTP libraries.

## Cases

| Case | Description |
| --- | --- |
| `health-check` | `GET /health` returns `{"status": "ok"}` |
| `http-response-bindings` | `@httpStaticHeader`, output `@httpHeader` (`ETag`), and `302` `Location` redirect |
| `category-lookup` | Read seeded category; missing category returns 404 |
| `order-lifecycle` | Place order, read back, missing order returns 404 |
| `order-fulfillment-states` | Each `FulfillmentState` union variant on seeded orders |
| `pet-crud-lifecycle` | Create, read, update, delete a pet |
| `pet-not-found` | Get/update/delete missing pet returns 404 |
| `pet-attribute-color` | `PetAttributeValue.color` round-trip; update to `weight_kg` |
| `pet-attribute-weight` | `PetAttributeValue.weight_kg` round-trip; update to `vaccinated` |
| `pet-attribute-vaccinated` | `PetAttributeValue.vaccinated` round-trip; update to `color` |

The pet-attribute cases together exercise every `PetAttributeValue` variant via both
`CreatePet` and `UpdatePet`. `order-fulfillment-states` reads server-seeded orders
(one line per `FulfillmentState` variant) via `${server.order_*_id}` context variables.

These mirror and extend the pytest smoke tests in
[`../python/tests/test_api.py`](../python/tests/test_api.py).

## Coverage gaps (vs [`../petstore-smithy-spec/`](../petstore-smithy-spec/))

All nine REST HTTP operations have at least one shared case. `@websocket` `PetEvents` is not covered by shared cases yet. Gaps below are scenarios
not yet represented in `cases/*.case.json`.

### Operations — covered

| Operation | Cases |
| --- | --- |
| `HealthCheck` | `health-check`, `http-response-bindings` |
| `CreatePet` | `pet-crud-lifecycle`, `pet-attribute-*`, `http-response-bindings` |
| `GetPet` | `pet-crud-lifecycle`, `pet-attribute-*`, `pet-not-found` |
| `ResolvePetLocation` | `http-response-bindings` |
| `UpdatePet` | `pet-crud-lifecycle`, `pet-attribute-*`, `pet-not-found` |
| `DeletePet` | `pet-crud-lifecycle`, `pet-not-found` |
| `GetCategory` | `category-lookup` |
| `PlaceOrder` | `order-lifecycle` |
| `GetOrder` | `order-lifecycle`, `order-fulfillment-states` |

### WebSocket — not covered

| Operation | Notes |
| --- | --- |
| `PetEvents` (`@websocket`) | No shared `cases/*.case.json` yet; server WS wiring is generated but not exercised by the cross-client harness |

### Error responses — not covered

| Error | Operations | Suggested case |
| --- | --- | --- |
| `ValidationError` (400) | `CreatePet`, `UpdatePet`, `PlaceOrder` | Invalid/missing required fields or bad enum values |
| `CategoryNotFound` body | `GetCategory` | Assert `message` on 404 (today only status) |
| `OrderNotFound` body | `GetOrder` | Assert `message` on 404 (today only status) |

### Enums — partial coverage

| Enum | Covered | Not covered |
| --- | --- | --- |
| `PetStatus` | `available`, `pending` | `sold` |
| `PetSpecies` | `DOG` (1) only | `CAT`, `BIRD`, `REPTILE` |
| `OrderStatus` | `placed` | `approved`, `delivered` |
| `OrderPriority` | `NORMAL` (2); seed uses `LOW` (1) | `HIGH` (3) on `PlaceOrder` |

### Unions — covered

| Union | Cases |
| --- | --- |
| `PetAttributeValue` (`color` / `weight_kg` / `vaccinated`) | `pet-attribute-color`, `pet-attribute-weight`, `pet-attribute-vaccinated` |
| `FulfillmentState` (`pending` / `shipped` / `delivered`) | `order-fulfillment-states` |

### HTTP binding features — covered

| Feature | Case |
| --- | --- |
| `@httpStaticHeader` on output (`Cache-Control` on `HealthCheck`) | `http-response-bindings` |
| Output `@httpHeader` (`ETag` on `CreatePet`) | `http-response-bindings` |
| Redirect via output `@httpHeader("Location")` (`ResolvePetLocation` 302) | `http-response-bindings` |

### Optional / nested response shapes — not covered

| Shape / field | Notes |
| --- | --- |
| `CreatePetInput.photo` (`Blob`) | No case sends binary photo data |
| `CreatePetInput.metadata` (`Document`) | `pet-crud-lifecycle` uses it; attribute cases use `null` |
| `CreatePetInput.adopted_at` | No case sets an explicit timestamp |
| `owner_id` + `OwnerSummary` / `PostalAddress` | All cases use `owner_id: null`; joined owner JSON never exercised over HTTP |
| `PetProfileSummary` | Requires seeded owner/profile data |
| `OrderLineDetail` scalar fields | `order-fulfillment-states` asserts `fulfillment` only, not `pet_id` / `quantity` / `unit_price_cents` |
| Multi-line orders | Only single-line seeded orders today |
