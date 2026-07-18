# Reference implementations

Each subdirectory corresponds to a Smithplates language entry or a companion reference project.

| Directory | Role |
|-----------|------|
| [`python/`](python/) | Full petstore reference: SQL + FastAPI server + Smithplates httpx client |
| [`typescript/`](typescript/) | Petstore TypeScript HTTP client (`fetch`; axios also bundled) |
| [`openapi-reference-python/`](openapi-reference-python/) | OpenAPI Generator asyncio client only (reference for HTTP test comparison) |

Shared Smithy models for all reference implementations live under [`petstore-smithy-spec/`](petstore-smithy-spec/).

Cross-language HTTP scenarios live under [`tests/`](tests/). Run them with:

```bash
./tests/run-tests.sh python python
./tests/run-tests.sh typescript python
./tests/run-tests.sh openapi-reference-python python
```
