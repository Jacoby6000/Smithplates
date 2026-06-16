# Petstore reference (Python)

Reference consumer project for Smithplates **SQL** and **HTTP** codegen with thin hand-written adapters that connect generated FastAPI routes to generated repository services. HTTP clients are generated from a Smithy OpenAPI export via OpenAPI Generator.

## Layout

```
example/
  petstore-smithy-spec/    Shared Smithy model (`petstore/api/`, `petstore/db/`)
  python/                  Python reference implementation (codegen + server)
    smithy-build.json.template  Smithplates plugin config (render to smithy-build.json; CI commits version bumps)
    smithy-build.json           Rendered plugin config (do not edit; run render-smithy-build.sh)
    render-smithy-build.sh      Inject current plugin version into smithy-build.json files
    openapi/                 Smithy → OpenAPI export + synced openapi.json
    build-generated.sh       publishM2, render configs, smithy build, OpenAPI Generator
    db/migrations/           Versioned schema DDL (generated)
    src/
      generated/
        api/                 Generated FastAPI routes, protocols, Pydantic models
        client/              OpenAPI Generator asyncio Python client
        db/                  Generated repository services
      server/                Protocol adapters + app wiring
    tests/                   Generated repository tests + API smoke tests
  tests/                   Cross-language HTTP scenario suite
```

Smithy models live under [`../petstore-smithy-spec/`](../petstore-smithy-spec/) (not inside `python/`). Regeneration still writes into this tree via `smithy-build.json` `sourceOutputDir` / `testOutputDir` settings.

## Features demonstrated

| Smithy feature | Where |
|----------------|--------|
| Separate API/DB Smithy namespaces | `petstore.api` (HTTP) and `petstore.db` (SQL); mapped in `src/server/repository_service.py` |
| `@sqlTable`, CRUD derive traits | `PetRepository`, `CategoryRepository`, `OrderRepository` in `petstore.db` |
| `@httpService`, `@http`, `@tags` | `Petstore` service in [`petstore-smithy-spec/petstore/api/http-service.smithy`](../petstore-smithy-spec/petstore/api/http-service.smithy) (`petstore.api`) |
| Smithy OpenAPI + OAG Python client | `openapi/smithy-build.json` → `src/generated/client/` |
| String + int enums | `PetStatus`, `OrderStatus`, `PetSpecies`, `OrderPriority` |
| Timestamps | `created_at`, `updated_at`, `adopted_at` |
| `@sqlAutoUuid`, `@sqlVarchar`, indexes | `Pet`, `Store`, … |
| `@sqlJson` lists/structs/unions | `tags`, `attributes`, `fulfillment` |
| Blob + Document columns | `photo`, `metadata` |
| FK joins (many-to-one, optional, transitive) | `GetPetRecord` joins category → store, optional owner/profile |
| One-to-many join | `GetOrderRecord` → `order_lines` |
| HTTP + SQL wiring | `src/server/api_adapters.py` and `repository_service.py` bridge `petstore.api` to `petstore.db` |

Generated HTTP routes live under `src/generated/api/`. The build script links that tree at `src/generated/generated/petstore_api/` so imports like `generated.petstore_api.*` resolve (see `packageName` in `smithy-build.json`).

OpenAPI export uses the Smithy OpenAPI plugin with projection transforms (`applyHttpProblemHttpError`, `applyHttpServiceRestJson1`, `stripSmithplatesHttpCodegenTraits`) so the same `@httpService` model drives FastAPI codegen and OpenAPI client generation. OpenAPI Generator writes an asyncio client under `src/generated/client/petstore_client/`.

See [Integration — HTTP and SQL model separation](../../docs/usage/integration.md#http-and-sql-model-separation) for the convention this example follows.

## Regenerate codegen

This project is a **consumer** reference: the Smithy CLI resolves `smithplates-plugin` and its dependencies from Maven (`publishM2` locally, or Central for releases). Golden template fixtures under `templates/python/tests/` omit `maven` because they run the plugin from the sbt test classpath instead.

From the Smithplates repository root (after plugin source changes):

```bash
./example/python/build-generated.sh
```

That script runs `sbtn publishM2`, renders `smithy-build.json` from [`smithy-build.json.template`](smithy-build.json.template) (and the OpenAPI template) using the current `smithplatesPlugin/version`, then runs `smithy build` for SQL/HTTP codegen and OpenAPI export, OpenAPI Generator, and syncs into `src/generated/`, `tests/`, and `db/migrations/`. Edit the `.template` files for config changes; run `render-smithy-build.sh` locally after `publishM2`. CI re-renders before example tests and commits `smithy-build.json` updates when the plugin version changes.

To render configs only (for example before a manual `smithy build`):

```bash
sbtn publishM2
./example/python/render-smithy-build.sh
cd example/python && smithy build
cd openapi && smithy build
```

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

Cross-language HTTP scenarios live under [`../tests/`](../tests/). Run the Python server and client together:

```bash
../tests/run-tests.sh python python
```

The shared runner sets `PETSTORE_DATABASE_PATH` for an isolated SQLite file during each run.

Postgres generated tests require Docker (`tests/db/postgres`).

## Client example

```python
import asyncio

from petstore_client import ApiClient, Configuration
from petstore_client.api.default_api import DefaultApi

async def main() -> None:
    configuration = Configuration(host="http://127.0.0.1:8080", ignore_operation_servers=True)
    api_client = ApiClient(configuration=configuration)
    api = DefaultApi(api_client)
    try:
        response = await api.health_check()
        print(response.to_dict())
    finally:
        await api_client.close()

asyncio.run(main())
```

Run with `PYTHONPATH=src/generated/client:src uv run python -c "..."` from this directory, or add those paths to your environment (see `pyproject.toml` `pythonpath` for pytest).
