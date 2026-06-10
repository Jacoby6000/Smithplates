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
