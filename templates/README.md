# Templates

Bundled Scalate SSP templates and golden expected outputs for Smithplates SQL and HTTP service codegen.

## Layout

```
templates/
  <language>/                 # e.g. python
    src/
      <feature>/              # e.g. db, http
        <feature-templates>   # models.ssp, service_protocol.ssp, …
        <implementation>/     # e.g. sqlite/, postgres/, fastapi/
          <impl-templates>
          tests/
        fragments/            # reusable SSP snippets for this feature
    tests/
      <test-case>/
        smithy/smithy-files.smithy
        smithy-build.json
        expected/
          db/migrations/<dialect>/...
          src/<feature>/…     # golden generated src artifacts
          test/<feature>/…    # golden generated test artifacts
```

Bundled Python templates are packaged from `templates/python/src/db/` and `templates/python/src/http/` into published renderer jars and loaded via default `classpath:` during `smithy build`. Golden tests compare rendered output against files under `templates/python/tests/<test-case>/expected/`.

## Bundled Python service types

| Service type | Template root | Generated output |
|--------------|---------------|------------------|
| SQL DB | `templates/python/src/db/` | DB models, repository protocols, SQLite/Postgres implementations, migration services, transaction helpers, and generated DB tests |
| HTTP API server | `templates/python/src/http/server/` | FastAPI route modules, route-group protocols, app wiring, response helpers, and problem detail helpers |
| HTTP API client | `templates/python/src/http/client/` | httpx route-group clients, client registry, response parsing, and operation bindings |
| HTTP API models | `templates/python/src/http/models/` | Shared Pydantic models used by server and client codegen |

## Contributing

- Edit SSP sources under `templates/python/src/db/` or `templates/python/src/http/` (not under renderer module resources).
- Refresh golden expectations under `templates/python/tests/<test-case>/expected/` when intentional output changes.
- Run `./scripts/run-template-golden-tests.sh` after template changes (golden render comparison).
- Run `./language-test-harnesses/python/run-linters.sh` and `./language-test-harnesses/python/run-tests.sh` after template changes.

See [`templates/python/tests/README.md`](python/tests/README.md) for golden-test case conventions and [`docs/contributing/template-authoring.md`](../docs/contributing/template-authoring.md) for contributor workflow details.
