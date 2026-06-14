$version: "2.0"
namespace petstore

use smithy.api#documentation
use smithy.api#error
use smithy.api#required
use smithy.api#timestampFormat

/// Classic pet lifecycle states stored as a Postgres ENUM / SQLite TEXT+CHECK column.
enum PetStatus {
    AVAILABLE = "available"
    PENDING = "pending"
    SOLD = "sold"
}

/// Numeric priority levels for store orders.
intEnum OrderPriority {
    LOW = 1
    NORMAL = 2
    HIGH = 3
}

/// Species identifier stored as an integer enum column.
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

/// Structured postal address stored as JSON on pets and owners.
structure PostalAddress {
    @required
    street: String
    @required
    city: String
    @required
    postal_code: String
}

/// Union of possible pet attribute payloads stored as JSON.
union PetAttributeValue {
    color: String
    weight_kg: Double
    vaccinated: Boolean
}

structure PetHighlight {
    @required
    name: String
    @required
    color: String
}

structure PetAttribute {
    @required
    name: String
    @required
    value: PetAttributeValue
}

/// Delivery progress for a pet order line item.
union FulfillmentState {
    pending: String
    shipped: Timestamp
    delivered: Timestamp
}

@error("client")
@documentation("Requested pet was not found.")
structure PetNotFound {
    @required
    message: String
}

@error("client")
@documentation("Requested order was not found.")
structure OrderNotFound {
    @required
    message: String
}

@error("client")
@documentation("Requested category was not found.")
structure CategoryNotFound {
    @required
    message: String
}

@error("client")
@documentation("Client supplied invalid input.")
structure ValidationError {
    @required
    message: String
}
