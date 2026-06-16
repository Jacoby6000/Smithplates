# Examples

The main reference project is the Python petstore example:

```text
example/python/
```

It demonstrates:

- a consumer `smithy-build.json` using only `com.jacoby6000:smithplates-plugin`;
- SQL schema DDL generation for SQLite and Postgres;
- Python DB model, repository protocol, dialect implementation, migration service, and generated test output;
- Python/FastAPI HTTP route and service protocol generation;
- OpenAPI export from Smithy HTTP models;
- generated Python client code from OpenAPI;
- hand-written adapters that map API models to database models.

## Regenerate artifacts

From the example directory:

```bash
./build-generated.sh
```

The script runs Smithy build steps, synchronizes generated output from `build/smithy/source/smithplates/`, exports OpenAPI, and runs OpenAPI Generator for the client path.

## Runtime shape

Generated code stays under `src/generated/` and `tests/`. Hand-written application code lives under `src/server/` and implements generated protocols.

The example keeps API and database Smithy namespaces separate:

- `petstore.api` is the HTTP contract.
- `petstore.db` is the persistence contract.
- `src/server/repository_service.py` maps between generated API and DB model trees.

Use this as the reference pattern for combining SQL and HTTP codegen in one application.
