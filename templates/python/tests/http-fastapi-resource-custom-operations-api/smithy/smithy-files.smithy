$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithy.api#http
use smithy.api#httpHeader
use smithy.api#tags
use smithy.api#readonly

/// Golden case: Smithy `resource operations: [...]` bindings on deeply nested resources.
@httpService
service ProjectApi {
    version: "1"
    resources: [Project]
}

resource Project {
    identifiers: { projectId: String }
    resources: [ProjectAssets]
}

resource ProjectAssets {
    identifiers: { projectId: String }
    resources: [Asset]
}

resource Asset {
    identifiers: { projectId: String, assetId: String }
    read: GetProjectAsset
    operations: [GetProjectAssetContent, ListAssetEvents]
}

@tags(["project_assets"])
@http(method: "GET", uri: "/projects/{projectId}/assets/{assetId}", code: 200)
@readonly
operation GetProjectAsset {
    input: GetProjectAssetInput
    output: AssetOutput
}

@tags(["project_assets"])
@http(method: "GET", uri: "/projects/{projectId}/assets/{assetId}/content", code: 302)
@readonly
operation GetProjectAssetContent {
    input: GetProjectAssetContentInput
    output: Redirect
}

@tags(["project_assets"])
@http(method: "GET", uri: "/projects/{projectId}/assets/{assetId}/events", code: 200)
@readonly
operation ListAssetEvents {
    input: ListAssetEventsInput
    output: AssetEventListOutput
}

structure GetProjectAssetInput for Asset {
    @required
    @httpLabel
    $projectId

    @required
    @httpLabel
    $assetId
}

structure GetProjectAssetContentInput for Asset {
    @required
    @httpLabel
    $projectId

    @required
    @httpLabel
    $assetId
}

structure ListAssetEventsInput for Asset {
    @required
    @httpLabel
    $projectId

    @required
    @httpLabel
    $assetId
}

structure AssetOutput {
    @required
    assetId: String
}

structure AssetEventListOutput {
    @required
    items: String
}

structure Redirect {
    @httpHeader("Location")
    @required
    url: String
}
