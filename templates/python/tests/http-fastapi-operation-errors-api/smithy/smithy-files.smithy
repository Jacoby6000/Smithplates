$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithy.api#error
use smithy.api#http
use smithy.api#httpError
use smithy.api#httpPayload
use smithy.api#tags

/// Golden case: operation-level errors returned as union types (no exceptions),
/// with @httpPayload output flattening and operation_bindings dispatch.
@httpService
service WidgetApi {
    version: "1"
    operations: [GetWidget, ListWidgets]
}

@tags(["v1_widgets"])
@http(method: "GET", uri: "/v1/widgets/{id}", code: 200)
operation GetWidget {
    input: GetWidgetInput
    output: GetWidget200
    errors: [GetWidget404]
}

@tags(["v1_widgets"])
@http(method: "GET", uri: "/v1/widgets", code: 200)
operation ListWidgets {
    input: Unit
    output: ListWidgets200
}

structure GetWidgetInput {
    @required
    @httpLabel
    id: String
}

structure GetWidget200 {
    @httpPayload
    @required
    body: WidgetOutput
}

structure ListWidgets200 {
    @httpPayload
    @required
    body: WidgetListOutput
}

structure WidgetOutput {
    @required
    id: String
}

structure WidgetListOutput {
    @required
    items: String
}

@error("client")
@httpError(404)
structure GetWidget404 {
    @required
    message: String
}
