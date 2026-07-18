# Limitations

This page separates current shipped behavior from roadmap work.

## Current language and framework support

- Bundled SQL service templates are Python-only.
- Bundled HTTP **server** templates are Python/FastAPI-only (including `@websocket` route wiring).
- Bundled HTTP **client** templates: Python/httpx and TypeScript (axios or fetch), including WebSocket clients when the service declares `@websocket` operations.
- Non-bundled languages and frameworks require an explicit `templateDirectory` that ships its own `outputs.json` deck.

## Migrations

Smithplates currently generates:

- build-time initial schema migration files such as `v1_initial_schema.sql`;
- Python SQLite and Postgres migration services that apply ordered migration files and track `_smithplates_migrations`.

Diff-based incremental migrations from Smithy model changes are roadmap work. Do not assume Smithplates will generate a full migration history from model diffs today.

The generated migration service applies migration files you provide in order. Today Smithplates generates the initial schema migration file for enabled dialects; future migrations still need an explicit workflow until diff-based generation exists.

## SQL service generation

Dialect-specific SQL service implementations require enabled dialects. Shared model and protocol generation can run without enabled dialects; this is useful for publishing common interfaces or custom implementations.

Generated derived SQL focuses on supported derive traits and the configured dialect renderers. Hand-written custom SQL is outside the bundled Python DB templates today.

## Generated tests

Generated SQL integration tests cover derived-query happy paths for supported Python DB variants. They are strongest as generated-code smoke tests and examples, not as a substitute for application-specific tests.

HTTP golden tests currently focus on render output. Application behavior should be tested in consumer code or example harnesses.

## HTTP service generation

HTTP service generation targets server-side Python/FastAPI wiring (REST route groups plus optional `@websocket` handlers). Generated route modules call generated protocol boundaries. Application behavior still belongs in hand-written protocol implementations.

## HTTP client generation

HTTP client generation mirrors server-side route groups and wire bindings. Configure `smithplates.<language>.http.client` alongside or instead of `server`:

- Python: `httpLibrary: "httpx"` (default).
- TypeScript: `httpLibrary: "fetch"` or `"axios"`.

OpenAPI Generator remains useful for external consumers and languages without a bundled Smithplates client.

## OpenAPI coordination

Smithplates HTTP codegen reads Smithy directly. OpenAPI Generator remains useful for client generation and consumer-specific workflows, but it is not the source of truth for Smithplates server generation.

## Project naming

The repository path and some scripts may still use `SmithyStache`; the product, plugin key, and Maven artifact are documented as Smithplates / `smithplates` / `com.jacoby6000:smithplates-plugin`.
