# Petstore reference (Python)

Reference consumer project for Smithplates **SQL** and **HTTP** codegen with thin hand-written adapters that connect generated FastAPI routes to generated repository services. HTTP clients are generated from a Smithy OpenAPI export via OpenAPI Generator.

## Layout

```
example/python/
  smithy/                  Smithy models (API + SQL repositories)
  smithy-build.json        Smithplates plugin configuration (sql + http)
  openapi/                 Smithy → OpenAPI export + synced openapi.json
    smithy-build.json      OpenAPI projection transforms + openapi plugin
  build-generated.sh       Regenerate codegen from the repo root
  db/migrations/           Versioned schema DDL (generated)
  src/
    generated/
      api/                 Generated FastAPI routes, protocols, Pydantic models
      client/              OpenAPI Generator asyncio Python client
      db/                  Generated repository services
    server/                Protocol adapters + app wiring
    client/                Thin facade over the generated OpenAPI client
  tests/                   Generated repository tests + API smoke tests
```

## Features demonstrated

| Smithy feature | Where |
|----------------|--------|
| `@sqlTable`, CRUD derive traits | `PetRepository`, `CategoryRepository`, `OrderRepository` |
| `@httpService`, `@http`, `@tags` | `Petstore` service in `smithy/http-service.smithy` |
| Smithy OpenAPI + OAG Python client | `openapi/smithy-build.json` → `src/generated/client/` |
| String + int enums | `PetStatus`, `OrderStatus`, `PetSpecies`, `OrderPriority` |
| Timestamps | `created_at`, `updated_at`, `adopted_at` |
| `@sqlAutoUuid`, `@sqlVarchar`, indexes | `Pet`, `Store`, … |
| `@sqlJson` lists/structs/unions | `tags`, `attributes`, `fulfillment` |
| Blob + Document columns | `photo`, `metadata` |
| FK joins (many-to-one, optional, transitive) | `GetPetRecord` joins category → store, optional owner/profile |
| One-to-many join | `GetOrderRecord` → `order_lines` |
| HTTP + SQL wiring | `src/server/api_adapters.py` implements generated `*ApiServiceProtocol` types |

Generated HTTP routes live under `src/generated/api/`. The build script links that tree at `src/generated/generated/petstore_api/` so imports like `generated.petstore_api.*` resolve (see `packageName` in `smithy-build.json`).

OpenAPI export uses the Smithy OpenAPI plugin with projection transforms (`applyHttpProblemHttpError`, `applyHttpServiceRestJson1`, `stripSmithplatesHttpCodegenTraits`) so the same `@httpService` model drives FastAPI codegen and OpenAPI client generation. OpenAPI Generator writes an asyncio client under `src/generated/client/petstore_client/`; `src/client/petstore_client.py` wraps it with dict-friendly helpers.

## Regenerate codegen

From the repository root (after plugin source changes):

```bash
./example/python/build-generated.sh
```

This runs the Smithy build for SQL/HTTP codegen, exports OpenAPI from `example/python/openapi/`, runs OpenAPI Generator, and syncs output into `src/generated/`, `tests/`, and `db/migrations/`.

## Run the server

```bash
cd example/python
uv sync
uv run petstore-server
```

The server applies SQLite migrations on startup, seeds a demo store/category, and listens on `http://127.0.0.1:8080`.

## Tests

```bash
cd example/python
uv run pytest tests/test_api.py
uv run pytest tests/db/sqlite -m "integration and sqlite"
```

Postgres generated tests require Docker (`tests/db/postgres`).

## Client example

```python
import asyncio
from client.petstore_client import PetstoreClient

async def main() -> None:
    async with PetstoreClient("http://127.0.0.1:8080") as client:
        print(await client.health_check())

asyncio.run(main())
```

Run with `uv run` from this directory (see `pyproject.toml` `pythonpath` for pytest).
