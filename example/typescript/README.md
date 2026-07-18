# TypeScript petstore client

Client-only Smithplates reference against the shared [`petstore-smithy-spec`](../petstore-smithy-spec/) model.

## What it generates

- TypeScript HTTP client (`httpLibrary: "fetch"` in `smithy-build.json`)
- Shared API models and `smithplates/codegen/http/httpProblem.ts`
- WebSocket client module when the service declares `@websocket` operations

There is no SQL or HTTP server output in this example.

## Regenerate

From the repository root:

```bash
./scripts/run-example-build.sh typescript
```

Or from this directory:

```bash
./build-generated.sh
```

## Typecheck

```bash
npm install
npm run typecheck
```

## Cross-implementation tests

Drive this client against the Python petstore server:

```bash
../tests/run-tests.sh typescript python
```

See [`../tests/README.md`](../tests/README.md) for the shared HTTP case harness.
