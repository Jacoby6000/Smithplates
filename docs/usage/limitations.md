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

## Generated tests

Generated SQL integration tests cover derived-query happy paths for supported Python DB variants. They are strongest as generated-code smoke tests and examples, not as a substitute for application-specific tests.

HTTP golden tests currently focus on render output. Application behavior should be tested in consumer code or example harnesses.

## OpenAPI coordination

Smithplates HTTP codegen reads Smithy directly. OpenAPI Generator remains useful for client generation and consumer-specific workflows, but it is not the source of truth for Smithplates server generation.

## Project naming

The repository path and some scripts may still use `SmithyStache`; the product, plugin key, and Maven artifact are documented as Smithplates / `smithplates` / `com.jacoby6000:smithplates-plugin`.
