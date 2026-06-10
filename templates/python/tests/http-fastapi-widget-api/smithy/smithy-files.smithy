$version: "2.0"
namespace example

use aws.protocols#restJson1
use smithy.api#http
use smithy.api#tags

@restJson1
service WidgetApi {
    version: "1"
    operations: [GetWidget, ListWidgets]
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
