# OpenAPI reference client (Python)

Reference **client-only** implementation for the petstore HTTP contract. Smithy exports OpenAPI with Smithplates projection transforms; [OpenAPI Generator](https://openapi-generator.tech/) produces an asyncio Python client. Use this alongside the Smithplates httpx client in [`../python/`](../python/) to compare behavior and catch regressions — the generated OpenAPI client is more hardened for edge cases.

This project does **not** include a server. Shared HTTP tests run it against the FastAPI server from [`../python/`](../python/).

## Layout

```
openapi-reference-python/
  smithy-build.json.template   OpenAPI export projection config
  smithy-build.json              Rendered config (CI may commit version bumps)
  build-generated.sh             Wrapper for scripts/run-example-build.sh openapi-reference-python
  openapi/openapi.json           Exported OpenAPI document
  src/generated/client/          OpenAPI Generator asyncio client (`petstore_client`)
```

## Regenerate

From the Smithplates repository root:

```bash
./scripts/run-example-build.sh openapi-reference-python
```

Or from this directory:

```bash
./build-generated.sh
```

## Run shared HTTP tests

Boot the Python reference server and exercise this client:

```bash
cd example/tests
./run-tests.sh openapi-reference-python python
```

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

Run from this directory with `PYTHONPATH=src/generated/client uv run python -c "..."`.
