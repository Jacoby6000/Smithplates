$version: "2.0"

namespace smithplates.codegen.http

use smithy.api#documentation
use smithy.api#trait

@documentation("HTTP payload serialization format for @httpService.")
enum HttpSerializationFormat {
    JSON = "json"
}

@documentation("""
Marks a Smithy service as an HTTP API service for smithplates HTTP codegen. Operations
use standard Smithy @http bindings and @tags for route grouping. Services may declare
Smithy resources; nested resource inputs may bind identifiers via `structure ... for Resource`.
""")
@trait(selector: "service")
structure httpService {
    @documentation("Wire serialization format for HTTP payloads. Defaults to JSON.")
    serialization: HttpSerializationFormat = "json"
}

@documentation("""
Marks an operation as a bidirectional WebSocket endpoint. The operation's input shape is the union
(or structure) of messages the server can receive from the client; the operation's output shape is
the union (or structure) of messages the client can receive from the server.

The operation must also declare an `@http` binding whose `uri` is the WebSocket route path and at
least one `@tags` value for route grouping. A typical operation declares union-typed input and
output shapes so many distinct message types can flow in each direction.
""")
@trait(selector: "operation")
structure websocket {}

@documentation("""
Attaches a fixed HTTP response header to an output structure (for example
`Content-Type: application/problem+json` on an error payload model).
""")
@trait(selector: "structure")
structure httpStaticHeader {
    @documentation("HTTP header name.")
    @required
    name: String

    @documentation("HTTP header value.")
    @required
    value: String
}

@documentation("""
Marks a service or operation error structure as an [RFC 9457](https://datatracker.ietf.org/doc/html/rfc9457)
problem response. Codegen emits exception classes that serialize to `application/problem+json` with
trait defaults for `type`, `title`, and optional `detail`. Raise the generated exception with optional
`detail` and `instance` arguments to describe a specific occurrence (for example trace identifiers).

The `type` field should be an HTTPS URL pointing to human-readable documentation for this error.
It defaults to `about:blank` when unset; smithplates warns when `type` is not an HTTPS URL.

Implies `Content-Type: application/problem+json` via response bindings (equivalent to
`@httpStaticHeader(name: "Content-Type", value: "application/problem+json")`).

When `code` is set, implies `@httpError` with the same status code (you do not need a separate
`@httpError` trait). Otherwise the structure must declare `@httpError`.
""")
@trait(selector: "structure[trait|error]")
structure httpProblem {
    @documentation("""
URI reference identifying the problem type. Use an HTTPS URL to documentation for this error.
Defaults to `about:blank`.
""")
    type: String = "about:blank"

    @documentation("Short, human-readable summary of the problem type.")
    @required
    title: String

    @documentation("""
Default human-readable explanation when the raised exception does not supply `detail`.
""")
    detail: String

    @documentation("""
HTTP status code for this error. When set, implies `@httpError` with the same code.
""")
    code: Integer
}
