$version: "2.0"
namespace petstore.api

use smithy.api#documentation
use smithy.api#error
use smithy.api#httpError
use smithy.api#required
use smithy.api#timestampFormat

// HTTP API timestamps use member-level `@timestampFormat("date-time")` so the Smithy OpenAPI
// export (RFC3339 strings) matches what the smithplates FastAPI server emits, letting the
// generated OpenAPI client deserialize responses without a post-export patch. The HTTP codegen
// only recognizes the prelude `Timestamp`, so we annotate members rather than define a shape.

enum PetStatus {
    AVAILABLE = "available"
    PENDING = "pending"
    SOLD = "sold"
}

intEnum OrderPriority {
    LOW = 1
    NORMAL = 2
    HIGH = 3
}

intEnum PetSpecies {
    DOG = 1
    CAT = 2
    BIRD = 3
    REPTILE = 4
}

enum OrderStatus {
    PLACED = "placed"
    APPROVED = "approved"
    DELIVERED = "delivered"
}

structure PostalAddress {
    @required
    street: String
    @required
    city: String
    @required
    postal_code: String
}

union PetAttributeValue {
    color: String
    weight_kg: Double
    vaccinated: Boolean
}

structure PetAttribute {
    @required
    name: String
    @required
    value: PetAttributeValue
}

list StringList {
    member: String
}

list PetAttributeList {
    member: PetAttribute
}

union FulfillmentState {
    pending: String

    @timestampFormat("date-time")
    shipped: Timestamp

    @timestampFormat("date-time")
    delivered: Timestamp
}

@httpError(404)
@error("client")
@documentation("Requested pet was not found.")
structure PetNotFound {
    @required
    message: String
}

@httpError(404)
@error("client")
@documentation("Requested order was not found.")
structure OrderNotFound {
    @required
    message: String
}

@httpError(404)
@error("client")
@documentation("Requested category was not found.")
structure CategoryNotFound {
    @required
    message: String
}

@httpError(400)
@error("client")
@documentation("Client supplied invalid input.")
structure ValidationError {
    @required
    message: String
}
