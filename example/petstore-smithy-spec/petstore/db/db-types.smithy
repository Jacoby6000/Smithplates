$version: "2.0"
namespace petstore.db

use smithy.api#required
use smithy.api#timestampFormat

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

structure PetHighlight {
    @required
    name: String
    @required
    color: String
}

list StringList {
    member: String
}

structure PetTags {
    @required
    items: StringList
}

union FulfillmentState {
    pending: String
    shipped: Timestamp
    delivered: Timestamp
}
