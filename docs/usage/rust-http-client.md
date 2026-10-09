# Rust async HTTP clients

The bundled `rust` target generates a bounded JSON HTTP client using async
reqwest 0.13 and Tokio. It is not feature-parity with Python or TypeScript.
There are no bundled Rust SQL, server, sync, or WebSocket templates.

## Configuration

```json
{
  "version": "1.0",
  "sources": ["smithy"],
  "plugins": {
    "smithplates": {
      "rust": {
        "http": {
          "rootNamespace": "catalog",
          "client": { "httpLibrary": "reqwest", "mode": "async" },
          "outputs": [{
            "sourceOutputDir": "src/generated",
            "testOutputDir": "test",
            "services": ["example#Catalog"]
          }]
        }
      }
    }
  }
}
```

Select **exactly one service per output entry**. Multiple entries must have
distinct output directories. Each entry emits `mod.rs`, `models.rs`, `client.rs`,
`http_problem.rs`, and an internal `runtime.rs` beneath
`sourceOutputDir/rootNamespaceDir`. Model names are flat across namespaces;
colliding names are rejected. No Cargo manifest or application scaffolding is
generated, and `packageName` is not a Rust crate name.
At least one represented input, output, or error model is required in an entry.

Include the generated module in your existing crate:

```rust
#[path = "generated/catalog/mod.rs"]
pub mod catalog;
```

Use Rust 1.93 (edition 2024) and these tested dependencies, then commit your
application's Cargo lockfile:

```toml
reqwest = { version = "=0.13.5", default-features = false, features = ["json", "query", "rustls"] }
serde = { version = "=1.0.229", features = ["derive"] }
serde_json = "=1.0.151"
tokio = { version = "=1.53.1", features = ["rt-multi-thread", "macros", "time"] }
```

## Construction and calls

```rust
let client = catalog::client::Client::new("https://api.example.com/v1/")?;
let input = catalog::models::GetItemInput {
    id: "item/with space".into(),
    filters: None,
    request_id: None,
};
let item = client.get_item(&input, Some(std::time::Duration::from_secs(10))).await?;
```

The client is reusable and cloneable: clones share reqwest's connection pool.
The base URL's path is retained; credentials, query strings, fragments, and
non-HTTP schemes in the base URL are rejected. Labels are single encoded path
segments. Empty, `.` and `..` labels are rejected to avoid URL normalization.
Operations without input take just the timeout argument.

The default transport disables redirects and automatic retries. Applications
may deliberately replace the hidden `client.runtime.transport` with a configured
reqwest client; doing so makes redirect, retry, TLS, proxy, and logging policy
the application's responsibility. Never enable redirects indiscriminately for
credential-bearing requests. HTTP is permitted for local development; use HTTPS
for credentials in production.

The optional per-call timeout covers request preparation, network waiting and
the complete response body. Credential hooks are synchronous: they must be
nonblocking, since Tokio cannot preempt synchronous work. `None` means no
generated timeout. Dropping the future, including a losing `tokio::select!`
branch, stops local waiting. It does **not** prove that an accepted server-side
mutation was canceled or rolled back.

## Credentials

Implement `catalog::client::AuthProvider` and assign an `Arc` to
`client.auth_provider`. `credential(operation_id, scheme_id)` returns
`Result<Option<String>, TransportError>`; `None` means this scheme has no
credential. Hooks receive full Smithy IDs and may only supply credentials, not
change the request contract. Return an unprefixed credential: generated metadata
adds `Bearer` or the modeled API-key prefix. Ordered alternatives select the
first available credential. Required authentication fails before I/O when none
is available; optional authentication permits anonymous requests; `@auth([])`
never calls the provider.

Bearer, header/query API keys, and `@httpCookieAuth` are supported. Cookie values
must be valid cookie octets (no separators/control characters). Conflicting
static/dynamic headers or modeled query values are not silently replaced by
credentials. Unsupported auth schemes fail generation.

## Responses and diagnostics

Only the modeled success status succeeds. Operation and service errors are
status-directed typed enum variants; ambiguous statuses fail generation.
`@httpProblem` models compose the shared `HttpProblem` through serde flattening.
Empty success is accepted for no-output, empty-structure and header-only output
operations; ordinary JSON output requires valid JSON and required field types.
Scalar dynamic response headers are authoritative over same-named JSON fields;
missing required, malformed, or repeated scalar headers fail decoding. Modeled
static response headers must match exactly.
Named scalar aliases also work in response headers, including service errors.
Operation errors normally use `<Operation>Error`; `Transport` uses
`TransportOperationError` to avoid the runtime error name. Colliding generated
operation error names are rejected.

Responses are buffered with a 1 MiB default limit; adjust
`client.runtime.max_response_bytes` explicitly if needed. Generated error
`Display`/`Debug` never include URLs, credentials, raw response bodies, or typed
error payloads. Payloads remain inspectable by matching typed variants; logging
them explicitly is the application's responsibility. Models intentionally do
not derive `Debug`. `SERVICE_ID` and `SERVICE_VERSION` are Smithy metadata, not
the consumer crate's package version.

## Supported subset and explicit exclusions

Supported: structures, string aliases, string enums, externally tagged unions,
string/boolean/integer/long/document values, optional members, lists and
string-keyed maps; serde wire names and Rust keyword remapping; scalar labels,
queries and request headers, repeated scalar queries, static headers, JSON
document and `@nestedProperties` structure payloads, and no-body operations.
An omitted optional nested payload sends no body or implicit JSON content type.
Unknown JSON fields are ignored; unknown enum/union variants fail decoding.
This is not a general Smithy constraint validator.

Transport-bound request scalars must use the directly supported Smithy
primitive types, not named aliases or enums. Type aliases inside JSON models
are resolved to their underlying serde types rather than distinct Rust newtypes.

Generation rejects unsupported features rather than approximating them:
blob/timestamp/big-number/float/int-enum/byte/short/set shapes, recursion, sparse
collections, defaults, length/range/pattern/unique constraints, raw/streaming
payloads, greedy labels, query-map/prefix-header bindings, dynamic status codes,
non-JSON media types, checksums, WebSockets, sync/both modes and ambiguous names
or response statuses, percent-escaped literal URI paths, response payload wrappers (including wrappers with headers),
legacy `@enum` string traits, and problem structures redeclaring shared problem
fields (including members renamed to shared wire names with `@jsonName`). Use
another target or consumer templates for these needs.

See [the Rust fixture](../../templates/rust/tests/http-json-client-api/) and
[contributor testing](../contributing/rust-http-client.md).
