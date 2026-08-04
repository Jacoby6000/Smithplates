$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithy.api#http
use smithy.api#tags

@httpService
service WidgetApi {
    version: "1"
    operations: [GetWidget]
}

@tags(["widgets"])
@http(method: "GET", uri: "/widgets/{widgetId}", code: 200)
operation GetWidget {
    input: GetWidgetInput
    output: WidgetOutput
}

structure GetWidgetInput {
    @required
    @httpLabel
    widgetId: String
}

structure WidgetOutput {
    @required
    widgetId: String
}
