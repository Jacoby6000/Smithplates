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

## Build-classpath dependencies

An OpenAPI-only projection still needs the Smithplates plugin on the classpath because Smithplates provides the projection transforms:

```json
{
  "maven": {
    "dependencies": [
      "com.jacoby6000:smithplates-plugin:<version>",
      "software.amazon.smithy:smithy-aws-traits:<smithy-version>",
      "software.amazon.smithy:smithy-openapi:<smithy-version>"
    ]
  }
}
```

Use the same Smithplates version as the codegen build. Use a Smithy toolchain version compatible with the Smithy CLI running the projection.

## Coordinating with generated code

When combining Smithplates and OpenAPI Generator:

- Use Smithplates for FastAPI server wiring and service protocol boundaries.
- Use OpenAPI Generator for clients or for model/client artifacts your application still needs.
- Keep OpenAPI projections scoped to HTTP API Smithy files, not SQL database model files.
- Keep generated package names aligned when application code imports both generated trees.

The [Python petstore example](../../example/python/) uses Smithplates for server and database artifacts, then exports OpenAPI and generates a Python client from that OpenAPI document.

## Source separation

OpenAPI projections should target API Smithy files and the API service shape. Do not include SQL Smithy files in OpenAPI projections. SQL traits are persistence concerns and should not leak into the wire contract.
