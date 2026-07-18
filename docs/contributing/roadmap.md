# Roadmap

This page is for contributor-facing planning. User-facing docs should describe shipped behavior first and link to [Limitations](../usage/limitations.md) for boundaries.

## Shipped since v0.2.5 (through v0.3.x)

- **Language-neutral codegen (`#34` / `#35`–`#42`)** — `NeutralType` / `ModelSet`, strategies, `CodegenPlanner`, SQL + HTTP cutover to `outputs.json` decks, consumer `additionalTemplatesDirectory`, cleanup of Python-specific Scala rendering helpers.
- **TypeScript HTTP clients** — bundled axios/fetch clients + petstore example.
- **WebSockets** — `@websocket` server (Python/FastAPI) and clients (Python + TypeScript).
- **`@sqlAutoIncrement`** and HTTP **`@nestedProperties`** payload flattening.

## Current focus

- Keep SQL and HTTP docs aligned with shipped behavior (including TypeScript and WebSockets); keep [`CHANGELOG.md`](../../CHANGELOG.md) current.
- Wire consumer-deck `CodegenStaticOutput` / filesystem static copy left open by `#41`.
- Move SQL enum emission into `outputs.json` model bindings (today: Scala `string_enum` / `int_enum` side path).
- Keep the Python petstore example the canonical full-stack reference; TypeScript example for client-only flows.

## Known future work

- Diff-based incremental migrations from Smithy model changes.
- Additional SQL dialects and driver patterns.
- TypeScript (or other) HTTP **server** and SQL template bundles.
- Additional HTTP server frameworks beyond bundled FastAPI.
- Stronger generated HTTP execution tests beyond render-focused golden cases and example harnesses.

## Roadmap hygiene

When adding roadmap work:

1. Keep the user guide clear about what exists today.
2. Add or update contributor architecture notes for the intended design.
3. Add acceptance criteria to the related issue.
4. Identify the test layer that will prove the change.
5. Update examples only after the behavior is implemented.
