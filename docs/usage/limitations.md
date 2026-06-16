# Limitations

This page separates current shipped behavior from roadmap work.

## Current language and framework support

- Bundled SQL service templates are Python-only.
- Bundled HTTP service templates are Python/FastAPI-only.
- Non-bundled languages and frameworks require explicit `templateDirectory` support.

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

HTTP service generation currently targets server-side Python/FastAPI wiring. It does not generate a client. Use OpenAPI export and a client generator when you need clients.

Generated route modules call generated protocol boundaries. Application behavior still belongs in hand-written protocol implementations.

## OpenAPI coordination

Smithplates HTTP codegen reads Smithy directly. OpenAPI Generator remains useful for client generation and consumer-specific workflows, but it is not the source of truth for Smithplates server generation.

## Project naming

The repository path and some scripts may still use `SmithyStache`; the product, plugin key, and Maven artifact are documented as Smithplates / `smithplates` / `com.jacoby6000:smithplates-plugin`.
