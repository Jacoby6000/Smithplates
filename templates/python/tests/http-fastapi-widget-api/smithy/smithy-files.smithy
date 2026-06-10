$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithy.api#error
use smithy.api#http
use smithy.api#httpError
use smithy.api#tags

@httpService
service WidgetApi {
    version: "1"
    operations: [GetWidget, ListWidgets]
    errors: [WidgetNotFound, InternalWidgetError]
}

@error("client")
@httpError(404)
structure WidgetNotFound {
    @required
    message: String
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
