$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithplates.codegen.http#httpProblem
use smithy.api#error
use smithy.api#http
use smithy.api#httpError
use smithy.api#httpPayload
use smithy.api#httpQuery
use smithy.api#tags

@httpService
service WarehouseApi {
    version: "1"
    operations: [CreateShelfItem, AssignShelfSku]
    errors: [ServiceUnavailable]
}

/// POST with @httpHeader, @httpLabel path labels, and a nested structure @httpPayload body.
@tags(["warehouse"])
@http(method: "POST", uri: "/warehouses/{warehouseId}/shelves/{shelfId}/items", code: 201)
operation CreateShelfItem {
    input: CreateShelfItemInput
    output: ShelfItemOutput
    errors: [Conflict, ServiceUnavailable]
}

/// POST with @httpHeader, one @httpLabel, and a primitive payload body member.
@tags(["warehouse"])
@http(method: "POST", uri: "/shelves/{shelfId}/skus", code: 201)
operation AssignShelfSku {
    input: AssignShelfSkuInput
    output: ShelfSkuOutput
}

structure PackageDimensions {
    @required
    lengthCm: Integer

    @required
    widthCm: Integer

    @required
    heightCm: Integer
}

structure PackageDetails {
    @required
    weightKg: Float

    @required
    dimensions: PackageDimensions
}

structure ItemDetails {
    @required
    name: String

    description: String

    tags: StringList

    @required
    package: PackageDetails
}

structure CreateShelfItemInput {
    @httpHeader("content-type")
    contentType: String

    @httpHeader("X-Idempotency-Key")
    idempotencyKey: String

    @required
    @httpLabel
    warehouseId: String

    @required
    @httpLabel
    shelfId: String

    @httpPayload
    @required
    details: ItemDetails
}

structure AssignShelfSkuInput {
    @httpHeader("X-Request-Id")
    requestId: String

    @required
    @httpLabel
    shelfId: String

    @required
    @httpQuery("tenant")
    tenant: String

    @httpQuery("preview")
    preview: Boolean

    @httpQuery("tag")
    tags: StringList

    @required
    sku: String
}

list StringList {
    member: String
}

structure ShelfItemOutput {
    @required
    itemId: String

    @required
    name: String
}

structure ShelfSkuOutput {
    @required
    shelfId: String

    @required
    sku: String
}

@httpProblem(
    type: "https://example.com/errors/service-unavailable"
    title: "Service unavailable"
    code: 503
)
@error("server")
structure ServiceUnavailable {
    message: String
}

@httpError(409)
@error("client")
structure Conflict {
    message: String
}
