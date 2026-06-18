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
- Smithplates httpx HTTP client generation from the same `@httpService` model;
- optional OpenAPI Generator reference client under `example/openapi-reference-python/`;
- hand-written adapters that map API models to database models.

## Regenerate artifacts

From the repository root:

```bash
./scripts/run-example-build.sh all
```

Or from an example directory:

```bash
./build-generated.sh
```

The build script runs `publishM2`, renders `smithy-build.json`, runs Smithy build, syncs Smithplates-generated output, and formats the Python petstore reference with `uv run ruff format`. OpenAPI export and the OpenAPI Generator reference client live under `example/openapi-reference-python/`; that generated client is build-only validation collateral and is not linted.

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
| `example/openapi-reference-python/` | OpenAPI export + OpenAPI Generator reference client. |
| `scripts/run-example-build.sh` | End-to-end regeneration for example reference projects. |
| `example/python/build-generated.sh` | Thin wrapper for the Python petstore reference. |
| `example/python/src/server/` | Hand-written app wiring and protocol implementations. |
| `example/python/src/generated/` | Generated API, HTTP client, and DB output. |
| `example/python/tests/` | Generated DB tests plus example API tests. |

## Typical workflow

When changing Smithplates itself:

```bash
sbtn publishM2
bash scripts/render-smithy-build.sh example/python
bash scripts/run-example-build.sh python
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
