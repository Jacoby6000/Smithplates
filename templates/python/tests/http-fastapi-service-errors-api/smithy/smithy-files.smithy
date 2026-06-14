$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithplates.codegen.http#httpProblem
use smithy.api#error
use smithy.api#http
use smithy.api#httpError
use smithy.api#tags

/// Golden case: service-level @httpError codegen (error models, api_exceptions,
/// api_exception_handler, app_factory exception registration) and Unit operation input.
@httpService
service WidgetApi {
    version: "1"
    operations: [GetWidget, ListWidgets]
    errors: [WidgetNotFound, InternalWidgetError]
}

@httpProblem(
    type: "https://example.com/errors/widget-not-found"
    title: "Widget not found"
    code: 404
)
@error("client")
structure WidgetNotFound {
}

@error("server")
@httpError(500)
structure InternalWidgetError {
    @required
    message: String
}

@tags(["v1_widgets"])
@http(method: "GET", uri: "/v1/widgets/{id}", code: 200)
operation GetWidget {
    input: GetWidgetInput
    output: WidgetOutput
}

@tags(["v1_widgets"])
@http(method: "GET", uri: "/v1/widgets", code: 200)
operation ListWidgets {
    input: Unit
    output: WidgetListOutput
}

structure GetWidgetInput {
    @required
    @httpLabel
    id: String
}

structure WidgetOutput {
    @required
    id: String
}

structure WidgetListOutput {
    @required
    items: String
}
