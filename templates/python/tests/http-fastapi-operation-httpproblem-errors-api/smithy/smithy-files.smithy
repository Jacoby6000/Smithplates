$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithplates.codegen.http#httpProblem
use smithy.api#error
use smithy.api#http
use smithy.api#httpPayload
use smithy.api#idempotent
use smithy.api#tags

/// Golden case: operation @httpProblem errors use distinct Problem subclasses in protocol unions.
@httpService
service WidgetApi {
    version: "1"
    operations: [MutateWidget, DeleteWidget]
}

@tags(["v1_widgets"])
@http(method: "PATCH", uri: "/v1/widgets/{id}", code: 200)
operation MutateWidget {
    input: MutateWidgetInput
    output: MutateWidget200
    errors: [MutateWidget404, MutateWidget409]
}

@tags(["v1_widgets"])
@http(method: "DELETE", uri: "/v1/widgets/{id}", code: 204)
@idempotent
operation DeleteWidget {
    input: DeleteWidgetInput
    output: Unit
    errors: [DeleteWidget404]
}

structure MutateWidgetInput {
    @required
    @httpLabel
    id: String

    @httpPayload
    @required
    body: WidgetPatch
}

structure WidgetPatch {
    @required
    status: String
}

structure MutateWidget200 {
    @httpPayload
    @required
    body: WidgetOutput
}

structure WidgetOutput {
    @required
    id: String
}

structure DeleteWidgetInput {
    @required
    @httpLabel
    id: String
}

structure Problem {
    @required
    title: String
}

@httpProblem(
    type: "https://example.com/errors/widget-not-found"
    title: "Widget not found"
    code: 404
)
@error("client")
structure MutateWidget404 {
    @httpPayload
    @required
    body: Problem
}

@httpProblem(
    type: "https://example.com/errors/widget-conflict"
    title: "Widget conflict"
    code: 409
)
@error("client")
structure MutateWidget409 {
    @httpPayload
    @required
    body: Problem
}

@error("client")
@httpError(404)
structure DeleteWidget404 {
    @required
    message: String
}
