$version: "2.0"
namespace petstore.api

use smithplates.codegen.http#httpService
use smithplates.codegen.http#httpStaticHeader
use smithy.api#http
use smithy.api#httpHeader
use smithy.api#httpLabel
use smithy.api#httpPayload
use smithy.api#idempotent
use smithy.api#readonly
use smithy.api#required
use smithy.api#tags
use smithy.api#timestampFormat

/// HTTP-facing petstore contract. Generated FastAPI routes call protocol implementations
/// in `src/server` that delegate to generated `@sqlService` repositories.
@tags(["pets"])
@http(method: "POST", uri: "/pets", code: 201)
operation CreatePet {
    input: CreatePetInput
    output: CreatePetOutput
    errors: [ValidationError]
}

structure CreatePetInput {
    @required
    name: String
    @required
    status: PetStatus
    @required
    species: PetSpecies
    @required
    category_id: String
    owner_id: String
    @required
    tag_count: Integer
    @required
    tags: StringList
    @required
    attributes: PetAttributeList
    photo: Blob
    metadata: Document

    @timestampFormat("date-time")
    adopted_at: Timestamp
}

structure CreatePetOutput {
    @required
    id: String

    @httpHeader("ETag")
    @required
    etag: String
}

@tags(["pets"])
@readonly
@http(method: "GET", uri: "/pets/{petId}/location", code: 302)
operation ResolvePetLocation {
    input: ResolvePetLocationInput
    output: PetLocationRedirect
    errors: [PetNotFound]
}

structure ResolvePetLocationInput {
    @httpLabel
    @required
    petId: String
}

structure PetLocationRedirect {
    @httpHeader("Location")
    @required
    url: String
}

@tags(["pets"])
@readonly
@http(method: "GET", uri: "/pets/{petId}")
operation GetPet {
    input: GetPetInput
    output: GetPetOutput
    errors: [PetNotFound]
}

structure GetPetInput {
    @httpLabel
    @required
    petId: String
}

structure GetPetOutput {
    @required
    pet: PetDetail
}

/// Aggregate read model returned by GetPet; mirrors joined repository output.
structure PetDetail {
    @required
    id: String
    @required
    name: String
    @required
    status: PetStatus
    @required
    species: PetSpecies
    @required
    category_id: String
    owner_id: String
    @required
    tag_count: Integer
    @required
    tags: StringList
    @required
    attributes: PetAttributeList
    photo: Blob
    metadata: Document

    @timestampFormat("date-time")
    adopted_at: Timestamp

    @required
    @timestampFormat("date-time")
    created_at: Timestamp

    @required
    @timestampFormat("date-time")
    updated_at: Timestamp

    @required
    category: CategorySummary
    @required
    store: StoreSummary
    owner: OwnerSummary
    profile: PetProfileSummary
}

structure CategorySummary {
    @required
    id: String
    @required
    name: String
    @required
    store_id: String
}

structure StoreSummary {
    @required
    id: String
    @required
    name: String
}

structure OwnerSummary {
    @required
    id: String
    @required
    full_name: String
    @required
    mailing_address: PostalAddress

    @required
    @timestampFormat("date-time")
    created_at: Timestamp
}

structure PetProfileSummary {
    @required
    id: String
    @required
    biography: String
    @required
    pet_id: String
}

@tags(["pets"])
@idempotent
@http(method: "PUT", uri: "/pets/{petId}")
operation UpdatePet {
    input: UpdatePetInput
    output: UpdatePetOutput
    errors: [PetNotFound, ValidationError]
}

structure UpdatePetInput {
    @httpLabel
    @required
    petId: String

    @httpPayload
    @required
    body: UpdatePetBody
}

structure UpdatePetBody {
    @required
    name: String
    @required
    status: PetStatus
    @required
    species: PetSpecies
    @required
    category_id: String
    owner_id: String
    @required
    tag_count: Integer
    @required
    tags: StringList
    @required
    attributes: PetAttributeList
    photo: Blob
    metadata: Document

    @timestampFormat("date-time")
    adopted_at: Timestamp
}

structure UpdatePetOutput {
    @required
    updated: Boolean
}

@tags(["pets"])
@idempotent
@http(method: "DELETE", uri: "/pets/{petId}", code: 204)
operation DeletePet {
    input: DeletePetInput
    output: Unit
    errors: [PetNotFound]
}

structure DeletePetInput {
    @httpLabel
    @required
    petId: String
}

@tags(["categories"])
@readonly
@http(method: "GET", uri: "/categories/{categoryId}")
operation GetCategory {
    input: GetCategoryInput
    output: GetCategoryOutput
    errors: [CategoryNotFound]
}

structure GetCategoryInput {
    @httpLabel
    @required
    categoryId: String
}

structure GetCategoryOutput {
    @required
    category: CategoryDetail
}

structure CategoryDetail {
    @required
    id: String
    @required
    name: String
    @required
    store_id: String
    @required
    store: StoreSummary
}

@tags(["orders"])
@http(method: "POST", uri: "/orders", code: 201)
operation PlaceOrder {
    input: PlaceOrderInput
    output: PlaceOrderOutput
    errors: [ValidationError]
}

structure PlaceOrderInput {
    @required
    label: String
    @required
    status: OrderStatus
    @required
    priority: OrderPriority
}

structure PlaceOrderOutput {
    @required
    id: String
}

@tags(["orders"])
@readonly
@http(method: "GET", uri: "/orders/{orderId}")
operation GetOrder {
    input: GetOrderInput
    output: GetOrderOutput
    errors: [OrderNotFound]
}

structure GetOrderInput {
    @httpLabel
    @required
    orderId: String
}

structure GetOrderOutput {
    @required
    order: OrderDetail
}

structure OrderDetail {
    @required
    id: String
    @required
    label: String
    @required
    status: OrderStatus
    @required
    priority: OrderPriority

    @required
    @timestampFormat("date-time")
    created_at: Timestamp

    @required
    @timestampFormat("date-time")
    updated_at: Timestamp

    @required
    lines: OrderLineDetailList
}

list OrderLineDetailList {
    member: OrderLineDetail
}

structure OrderLineDetail {
    @required
    id: String
    @required
    order_id: String
    @required
    pet_id: String
    @required
    quantity: Integer
    @required
    unit_price_cents: Long
    @required
    fulfillment: FulfillmentState
}

@tags(["health"])
@readonly
@http(method: "GET", uri: "/health")
operation HealthCheck {
    input: Unit
    output: HealthCheckOutput
}

@httpStaticHeader(name: "Cache-Control", value: "no-store")
structure HealthCheckOutput {
    @required
    status: String
}
