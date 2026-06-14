# Petstore reference (Python)

Reference consumer project for Smithplates SQL codegen and a hand-written HTTP layer that implements the Smithy API contract in [`smithy/api.smithy`](smithy/api.smithy).

## Layout

```
example/python/
  smithy/                  Smithy models (API + SQL repositories)
  smithy-build.json        Smithplates plugin configuration
  build-generated.sh       Regenerate codegen from the repo root
  db/migrations/           Versioned schema DDL (generated)
  src/
    generated/             Smithplates Python repository output (generated)
    server/                FastAPI server calling generated services
    client/                httpx client for the HTTP API
  tests/                   Generated repository tests + API smoke tests
```

## Features demonstrated

| Smithy / SQL feature | Where |
|----------------------|--------|
| `@sqlTable`, CRUD derive traits | `PetRepository`, `CategoryRepository`, `OrderRepository` |
| String + int enums | `PetStatus`, `OrderStatus`, `PetSpecies`, `OrderPriority` |
| Timestamps + epoch seconds | `created_at`, `updated_at`, `adopted_at` |
| `@sqlAutoUuid`, `@sqlVarchar`, indexes | `Pet`, `Store`, … |
| `@sqlJson` lists/structs/unions | `tags`, `attributes`, `fulfillment` |
| Blob + Document columns | `photo`, `metadata` |
| FK joins (many-to-one, optional, transitive) | `GetPetRecord` joins category → store, optional owner/profile |
| One-to-many join | `GetOrderRecord` → `order_lines` |
| HTTP contract (hand-implemented) | `smithy/api.smithy` + `src/server` |

HTTP service codegen is not available yet; the server and client implement the `@http` operations manually against generated repositories.

## Regenerate codegen

From the repository root (after `sbtn publishM2` when plugin sources change):

```bash
./example/python/build-generated.sh
```

This runs the same Smithy build path as template golden tests and syncs output into `src/generated/`, `tests/`, and `db/migrations/`.

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

Run with `PYTHONPATH=src/generated/db/model:src/generated/db:src:src/generated/db/sqlite` or via `uv run` from this directory (see `pyproject.toml` `pythonpath` for pytest).
