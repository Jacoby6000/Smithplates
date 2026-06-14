$version: "2.0"
namespace petstore

use aws.protocols#restJson1

/// Petstore HTTP API exported for OpenAPI client generation.
@restJson1
service Petstore {
    version: "2024-01-01"
    operations: [
        CreatePet
        GetPet
        UpdatePet
        DeletePet
        GetCategory
        PlaceOrder
        GetOrder
        HealthCheck
    ]
}
