$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithy.api#http
use smithy.api#tags
use smithy.api#readonly
use smithy.api#timestampFormat

@httpService
service WidgetApi {
    version: "1"
    operations: [InspectWidget, SearchWidgets]
}

/// Route input members are declared headers, then URI path labels, then queries.
@tags(["v1_widgets"])
@http(method: "GET", uri: "/v1/widgets/{id}", code: 200)
@readonly
operation InspectWidget {
    input: InspectWidgetInput
    output: WidgetOutput
}

@tags(["v1_widgets"])
@http(method: "GET", uri: "/v1/widgets", code: 200)
@readonly
operation SearchWidgets {
    input: SearchWidgetsInput
    output: WidgetListOutput
}

structure InspectWidgetInput {
    @httpHeader("X-Region")
    region: String

    @required
    @httpLabel
    id: String

    @httpQuery("category")
    category: String

    @httpQuery("since")
    @timestampFormat("epoch-seconds")
    since: Timestamp
}

structure SearchWidgetsInput {
    @httpHeader("X-Trace-Id")
    traceId: String

    @httpQuery("sort")
    sort: String

    @httpQuery("limit")
    limit: Integer
}

structure WidgetOutput {
    @required
    id: String
}

structure WidgetListOutput {
    @required
    items: String
}
