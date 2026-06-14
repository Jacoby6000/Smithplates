$version: "2.0"
namespace petstore

use aws.protocols#restJson1
use smithplates.codegen.http#httpService

/// HTTP-facing petstore service wired for smithplates FastAPI codegen and Smithy OpenAPI export.
@restJson1
@httpService
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
