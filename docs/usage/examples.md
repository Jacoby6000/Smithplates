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

## Important files

| File | Purpose |
|------|---------|
| `example/python/smithy-build.json.template` | Source template for the Smithplates SQL and HTTP plugin config. |
| `example/python/smithy-build.json` | Rendered config with the current local or published plugin version. |
| `example/python/openapi/smithy-build.json.template` | Source template for OpenAPI export. |
| `example/python/build-generated.sh` | End-to-end regeneration script. |
| `example/python/src/server/` | Hand-written app wiring and protocol implementations. |
| `example/python/src/generated/` | Generated API, DB, and client output. |
| `example/python/tests/` | Generated DB tests plus example API tests. |

## Typical workflow

When changing Smithplates itself:

```bash
sbtn publishM2
bash example/python/render-smithy-build.sh
bash example/python/build-generated.sh
```

When inspecting only the generated consumer project:

```bash
cd example/python
uv sync
uv run pytest tests/test_api.py
uv run pytest tests/db/sqlite -m "integration and sqlite"
```

Postgres generated tests require Docker.

## Pattern to copy

Copy the architecture, not necessarily every script:

- Keep generated code in a predictable generated tree.
- Keep hand-written code in a separate application tree.
- Implement generated HTTP protocols in hand-written classes.
- Call generated DB repositories from those protocol implementations.
- Map API models to DB models at that boundary.
- Keep Smithy API and DB namespaces separate.
