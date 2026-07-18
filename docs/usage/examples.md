# Examples

Reference projects live under [`example/`](../../example/).

| Directory | Role |
|-----------|------|
| [`example/python/`](../../example/python/) | Full petstore: SQL + FastAPI server + Smithplates httpx client + adapters |
| [`example/typescript/`](../../example/typescript/) | Petstore TypeScript HTTP client (`httpLibrary: "fetch"`) against the shared Smithy model |
| [`example/openapi-reference-python/`](../../example/openapi-reference-python/) | OpenAPI Generator asyncio client (comparison / external-consumer reference) |

Shared Smithy models live under [`example/petstore-smithy-spec/`](../../example/petstore-smithy-spec/).

## Feature coverage: examples vs golden tests

| Feature | Petstore examples | Golden / fixture coverage |
|---------|-------------------|---------------------------|
| SQL schema + derived repositories (SQLite/Postgres) | Python | `templates/python/tests/` (`db-*`) |
| FastAPI server + httpx client | Python | `templates/python/tests/http-*` |
| TypeScript axios/fetch clients | TypeScript (`fetch`) | `templates/typescript/tests/http-*` |
| Shared `HttpProblem` | Python + TypeScript | HTTP goldens |
| `@websocket` | Python petstore (`PetEvents`) + generated TS client module | Python + TypeScript HTTP goldens; how-to: [HTTP plugin — WebSockets](http-plugin.md#websockets) |
| `@sqlAutoIncrement` | Not in petstore Smithy | Python DB goldens |
| `@nestedProperties` body binding | Not in petstore Smithy | Python + TypeScript HTTP goldens |
| `additionalTemplatesDirectory` | Not in petstore | Golden cases under template test suites |

Prefer the petstore examples for end-to-end consumer layout. Prefer golden fixtures under [`templates/python/tests/`](../../templates/python/tests/) and [`templates/typescript/tests/`](../../templates/typescript/tests/) when inspecting newer trait or deck behavior.

## Python petstore

Demonstrates:

- a consumer `smithy-build.json` using only `com.jacoby6000:smithplates-plugin`;
- SQL schema DDL generation for SQLite and Postgres;
- Python DB model (dataclasses), repository protocol, dialect implementation, migration service, and generated test output under namespace-aware paths;
- Python/FastAPI HTTP route and service protocol generation;
- Smithplates httpx HTTP client generation from the same `@httpService` model;
- optional OpenAPI Generator reference client under `example/openapi-reference-python/`;
- hand-written adapters that map API models to database models.

## TypeScript petstore client

Demonstrates a client-only `typescript` language entry with `httpLibrary: "fetch"` (axios is also bundled). Cross-implementation example tests can drive the TypeScript client against the Python server — see [`example/tests/`](../../example/tests/).

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

## Runtime shape (Python)

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
| `example/typescript/smithy-build.json.template` | TypeScript client-only plugin config. |
| `example/python/smithy-build.json` | Rendered config with the current local or published plugin version. |
| `example/typescript/smithy-build.json` | Rendered TypeScript client config. |
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
bash scripts/render-smithy-build.sh all
bash scripts/run-example-build.sh all
```

When inspecting only the generated Python consumer project:

```bash
cd example/python
uv sync
uv run pytest tests/test_api.py
uv run pytest tests/petstore/db/sqlite -m "integration and sqlite"
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
