$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithplates.codegen.http#httpProblem
use smithy.api#error
use smithy.api#http
use smithy.api#httpError
use smithy.api#httpHeader
use smithy.api#httpPayload
use smithy.api#tags

/// Golden case: output @httpPayload flattening, @httpHeader redirect responses,
/// and operation error with @httpProblem implying `application/problem+json`.
@httpService
service AssetApi {
    version: "1"
    operations: [GetAsset, GetAssetContent, UpdateAssetState]
}

@tags(["assets"])
@http(method: "GET", uri: "/assets/{id}", code: 200)
operation GetAsset {
    input: GetAssetInput
    output: GetAsset200
}

@tags(["assets"])
@http(method: "GET", uri: "/assets/{id}/content", code: 302)
operation GetAssetContent {
    input: GetAssetContentInput
    output: Redirect
    errors: [GetAssetContent404]
}

@tags(["assets"])
@http(method: "PATCH", uri: "/assets/{id}/state", code: 200)
operation UpdateAssetState {
    input: UpdateAssetStateInput
    output: GetAsset200
    errors: [UpdateAssetState409]
}

structure GetAssetInput {
    @required
    @httpLabel
    id: String
}

structure GetAssetContentInput {
    @required
    @httpLabel
    id: String
}

structure UpdateAssetStateInput {
    @required
    @httpLabel
    id: String

    @httpPayload
    @required
    body: AssetStatePatch
}

structure AssetStatePatch {
    @required
    status: String
}

structure GetAsset200 {
    @httpPayload
    @required
    body: AssetOutput
}

structure AssetOutput {
    @required
    id: String

    @required
    status: String
}

structure Redirect {
    @httpHeader("Location")
    @required
    url: String
}

@error("client")
@httpError(404)
structure GetAssetContent404 {
    @required
    message: String
}

@httpProblem(
    type: "https://example.com/errors/state-conflict"
    title: "Asset state conflict"
    code: 409
)
@error("client")
structure UpdateAssetState409 {
}
