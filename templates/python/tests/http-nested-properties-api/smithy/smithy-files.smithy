$version: "2.0"

namespace example

use smithy.api#http
use smithy.api#httpPayload
use smithy.api#nestedProperties
use smithy.api#readonly
use smithy.api#tags
use smithplates.codegen.http#httpService

/// Golden case: @nestedProperties on @httpPayload members flattens the payload
/// target's fields to the document root for both request bodies and response bodies.
@httpService
service WidgetApi {
    version: "1"
    operations: [CreateWidget, GetWidget]
}

@tags(["widgets"])
@http(method: "POST", uri: "/widgets", code: 201)
operation CreateWidget {
    input: CreateWidgetInput
    output: CreateWidgetOutput
}

@tags(["widgets"])
@readonly
@http(method: "GET", uri: "/widgets/{widgetId}", code: 200)
operation GetWidget {
    input: GetWidgetInput
    output: GetWidgetOutput
}

structure CreateWidgetInput {
    @nestedProperties
    @httpPayload
    @required
    body: WidgetCreateRequest
}

structure CreateWidgetOutput {
    @nestedProperties
    @httpPayload
    @required
    body: WidgetSummary
}

structure GetWidgetInput {
    @required
    @httpLabel
    widgetId: String
}

structure GetWidgetOutput {
    @nestedProperties
    @httpPayload
    @required
    body: WidgetSummary
}

structure WidgetCreateRequest {
    @required
    name: String

    @required
    description: String
}

structure WidgetSummary {
    @required
    widgetId: String

    @required
    name: String

    @required
    description: String
}
