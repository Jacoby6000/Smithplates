# OpenAPI

Smithplates HTTP codegen reads Smithy models directly. OpenAPI export is a separate projection step for consumers that also want OpenAPI documents or generated clients.

## Projection transforms

Smithplates provides Smithy model transforms that make `@httpService` models compatible with standard Smithy OpenAPI tooling:

```json
{
  "projections": {
    "openapi": {
      "transforms": [
        { "name": "applyHttpProblemHttpError" },
        { "name": "applyHttpServiceRestJson1" },
        { "name": "stripSmithplatesHttpCodegenTraits" }
      ],
      "plugins": {
        "openapi": {
          "service": "example.api#ExampleService",
          "protocol": "aws.protocols#restJson1"
        }
      }
    }
  }
}
```

| Transform | Purpose |
|-----------|---------|
| `applyHttpProblemHttpError` | Materializes `@httpError` from `@httpProblem(code: ...)`. |
| `applyHttpServiceRestJson1` | Adds `@restJson1` to `@httpService` services for OpenAPI export. |
| `stripSmithplatesHttpCodegenTraits` | Removes Smithplates-only HTTP traits after standard traits are materialized. |

The OpenAPI projection still needs Smithy OpenAPI dependencies such as `software.amazon.smithy:smithy-openapi` and `software.amazon.smithy:smithy-aws-traits`.

## Coordinating with generated code

When combining Smithplates and OpenAPI Generator:

- Use Smithplates for FastAPI server wiring and service protocol boundaries.
- Use OpenAPI Generator for clients or for model/client artifacts your application still needs.
- Keep OpenAPI projections scoped to HTTP API Smithy files, not SQL database model files.
- Keep generated package names aligned when application code imports both generated trees.

The [Python petstore example](../../example/python/) uses Smithplates for server and database artifacts, then exports OpenAPI and generates a Python client from that OpenAPI document.
