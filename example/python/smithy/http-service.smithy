$version: "2.0"
namespace petstore

use smithplates.codegen.http#httpService

/// HTTP-facing petstore service for smithplates FastAPI codegen and Smithy OpenAPI export.
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
