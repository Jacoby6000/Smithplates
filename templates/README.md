# Templates

Bundled Scalate SSP templates and golden expected outputs for Smithplates SQL and HTTP service codegen.

## Layout

```
templates/
  <language>/                 # e.g. python, typescript
    base_config.json          # NamingStrategy + TypeRenderer syntax for the language
    src/
      <feature>/              # e.g. db, http
        outputs.json          # CodegenOutput deck (required beside templates)
        <feature-templates>   # models.ssp, service_protocol.ssp, …
        <implementation>/     # e.g. sqlite/, postgres/, fastapi/, axios/, fetch/
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

Bundled templates are packaged from `templates/<language>/src/...` into published renderer jars and loaded via default `classpath:` during `smithy build`. Golden tests compare rendered output against files under `templates/<language>/tests/<test-case>/expected/`.

## Bundled languages

### Python

| Service type | Template root | Generated output |
|--------------|---------------|------------------|
| SQL DB | `templates/python/src/db/` | DB models, repository protocols, SQLite/Postgres implementations, migration services, transaction helpers, and generated DB tests |
| HTTP API server | `templates/python/src/http/server/` | FastAPI route modules, route-group protocols, app wiring, WebSocket routes, response helpers, and problem detail helpers |
| HTTP API client | `templates/python/src/http/client/` | httpx route-group clients, client registry, response parsing, WebSocket client, and operation bindings |
| HTTP API models | `templates/python/src/http/models/` | Shared Pydantic models used by server and client codegen |

### TypeScript

| Service type | Template root | Generated output |
|--------------|---------------|------------------|
| HTTP API client | `templates/typescript/src/http/client/` | axios or fetch route-group clients, registries, bindings, WebSocket client |
| HTTP API models | `templates/typescript/src/http/models/` | Shared TypeScript models used by the client |

TypeScript is **client-only** today — there is no bundled TypeScript HTTP server or SQL template set.

## Contributing

- Edit SSP sources under `templates/<language>/src/...` (not under renderer module resources).
- Keep each template root's `outputs.json` deck in sync when adding or removing artifacts.
- Refresh golden expectations under `templates/<language>/tests/<test-case>/expected/` when intentional output changes.
- Run `./scripts/run-template-golden-tests.sh` after template changes (golden render comparison).
- Run language harness linters/tests under `language-test-harnesses/` after template changes.

See [`templates/python/tests/README.md`](python/tests/README.md) for golden-test case conventions and [`docs/contributing/template-authoring.md`](../docs/contributing/template-authoring.md) for contributor workflow details.
