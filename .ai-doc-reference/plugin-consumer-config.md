# Plugin Consumer Configuration

Read this when wiring `smithy-build.json` for consumers, adding language targets,
or validating plugin config in tests.

Full user documentation:
[`docs/usage/configuration.md`](../docs/usage/configuration.md). HTTP-specific
config: [`docs/usage/http-plugin.md`](../docs/usage/http-plugin.md). Trait
reference: [`modules/smithplates-plugin/README.md`](../modules/smithplates-plugin/README.md).

## Maven coordinates

Published plugin JARs are consumed by `smithy build` via Maven coordinates in
consumer `smithy-build.json` files.

## Language-first `smithplates` key

Configuration under the `smithplates` plugin key is language-first: each top-level
key is a language id such as `python`, with optional `sql` and `http` blocks.
Output directories (`sourceOutputDir`, `testOutputDir`) are **not** set at the
language level — they are required fields inside each `sql.outputs[]` and
`http.outputs[]` entry. Each target's `outputs` array must have at least one
entry.

### SQL

SQL dialect keys (`sqlite`, `postgres`) live under `smithplates.<language>.sql`
and take `enable` (default `false`) and `migrationLocation`. SQL blocks accept
optional `templateDirectory`, `rootNamespace`, and `packageName`.

Configure SQL outputs in `smithplates.<language>.sql.outputs[]` (required).
Each entry takes `sourceOutputDir`, `testOutputDir`, optional `services` filter
(accepts full shape IDs `com.example#MyService` or bare names `MyService`; empty
list = generate all), and optional `packageName`. A warning is logged when a
`services` filter matches no `@sqlService` in the model.

Consumers can append bundled outputs via `additionalTemplatesDirectory` on the `sql`
block (and `enableExternalTemplates` on the language entry). See
[`docs/usage/configuration.md`](../docs/usage/configuration.md#custom-codegen-outputs).

### HTTP

HTTP configuration uses `smithplates.<language>.http.server` and/or
`smithplates.<language>.http.client`.

Configure HTTP server/client outputs in
`smithplates.<language>.http.outputs[]` (required). Each entry takes
`sourceOutputDir`, `testOutputDir`, optional `services` filter (same semantics
as SQL), and optional `packageName`.

Consumers can also append bundled outputs via `additionalTemplatesDirectory` on each
`sql` / `http.server` / `http.client` block (and `enableExternalTemplates` on the
language entry). See
[`docs/usage/configuration.md`](../docs/usage/configuration.md#custom-codegen-outputs).

Path collision detection for merged decks runs at codegen time (after the Smithy model
is loaded), not during plugin config validation. Consumer-deck static file copy
(`CodegenStaticOutput`) is not wired yet.

### Templates

Bundled languages (default `classpath:`):

- **Python** — [`templates/python/src/db/`](../templates/python/src/db/) and
  [`templates/python/src/http/`](../templates/python/src/http/) (`server/`,
  `client/`, `models/`).
- **TypeScript** — [`templates/typescript/src/http/`](../templates/typescript/src/http/)
  (client + models only; `httpLibrary` `fetch` or `axios`).

Other languages require an explicit `templateDirectory` whose classpath
contains every required template and an `outputs.json` deck.

Bundled codegen templates are inferred from enabled dialects/frameworks — do
not require per-artifact `artifacts` entries in `smithplates`.

Plugin config validation checks top-level templates required by enabled
dialects/frameworks.
